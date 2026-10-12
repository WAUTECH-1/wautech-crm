package com.wautech.localsetup;

import com.wautech.crm.identity.dto.UserProfileRequest;
import com.wautech.crm.identity.repository.UserRepository;
import com.wautech.crm.identity.service.CredentialProvisioningService;
import com.wautech.crm.identity.service.UserService;
import com.wautech.crm.organization.entity.MembershipStatus;
import com.wautech.crm.organization.entity.OrganizationMembership;
import com.wautech.crm.organization.entity.OrganizationRole;
import com.wautech.crm.organization.repository.OrganizationMembershipRepository;
import com.wautech.crm.organization.repository.OrganizationRepository;
import com.wautech.crm.platform.security.SecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Separate, non-web entry point. Never discovered by the CRM component scan. */
public final class LocalAccountSetup {
    public static final String EMAIL = "admin@wautech.local";

    public static void validateTarget(String url, String confirmation, String profiles) {
        if (url == null || !url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]{1,5}/[A-Za-z0-9_]+")
                || !url.equals(confirmation) || !"local-account-setup".equals(profiles)) {
            throw new IllegalArgumentException("Explicit local-account-setup profile and confirmed loopback database required");
        }
    }

    public static void main(String[] args) {
        // Validate before starting Spring, opening a database connection, or running migrations.
        String url = System.getenv("LOCAL_SETUP_DB_URL");
        validateTarget(url, System.getenv("LOCAL_SETUP_CONFIRMED_DB_URL"),
                System.getenv("SPRING_PROFILES_ACTIVE"));
        UUID organizationId = UUID.fromString(System.getenv("LOCAL_SETUP_ORGANIZATION_ID"));
        SpringApplication application = new SpringApplication(SetupConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        // Command-line properties take precedence over .env and inherited datasource overrides.
        try (var context = application.run(
                "--spring.datasource.url=" + url,
                "--spring.profiles.active=local-account-setup",
                "--spring.flyway.enabled=false", "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.jpa.show-sql=false", "--logging.level.root=OFF", "--spring.main.banner-mode=off")) {
            if (args.length == 1 && "--inspect".equals(args[0])) {
                var organization = context.getBean(OrganizationRepository.class).findByIdAndArchivedFalse(organizationId)
                        .orElseThrow(() -> new IllegalStateException("Existing active organization required"));
                System.out.println("Read-only inspection: " + url + "; organization " + organization.getId()
                        + " (" + organization.getName() + "); account exists: "
                        + context.getBean(UserRepository.class).existsByEmail(EMAIL));
                return;
            }
            var transaction = new TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            transaction.executeWithoutResult(status -> provision(organizationId,
                    context.getBean(UserRepository.class), context.getBean(OrganizationRepository.class),
                    context.getBean(OrganizationMembershipRepository.class), context.getBean(UserService.class),
                    context.getBean(CredentialProvisioningService.class), LocalAccountSetup::readPassword));
            System.out.println("Local account ready: " + EMAIL + "; organization " + organizationId + "; ACTIVE ADMIN. Existing password preserved.");
        } catch (Exception failure) {
            // Do not print exception messages, SQL, credentials, or stack traces.
            System.err.println("Local setup failed; transaction rolled back. Check target, password policy, and account/membership state.");
            System.exit(1);
        }
    }

    @FunctionalInterface
    public interface PasswordInput { String read(); }

    public static void provision(UUID organizationId, UserRepository users, OrganizationRepository organizations,
            OrganizationMembershipRepository memberships, UserService userService,
            CredentialProvisioningService credentials, PasswordInput passwordInput) {
        var organization = organizations.lockByIdAndArchivedFalse(organizationId)
                .orElseThrow(() -> new IllegalStateException("Existing active organization required"));
        var user = users.findByEmail(EMAIL).orElseGet(() -> userService.requireUser(
                userService.create(new UserProfileRequest(EMAIL, "Local", "Administrator")).id()));
        if (!user.isEnabled()) throw new IllegalStateException("Disabled account requires manual review");
        var existing = memberships.findAllByOrganization_IdAndUser_IdOrderByCreatedAtDesc(organizationId, user.getId());
        if (!existing.isEmpty() && (existing.size() != 1 || existing.getFirst().getStatus() != MembershipStatus.ACTIVE
                || existing.getFirst().getRole() != OrganizationRole.ADMIN)) {
            throw new IllegalStateException("Existing membership requires manual review");
        }
        if (user.getPasswordHash() == null) credentials.provisionInitialPassword(user.getId(), passwordInput.read());
        if (existing.isEmpty()) {
            var membership = new OrganizationMembership(organization, user);
            membership.changeRole(OrganizationRole.ADMIN);
            membership.transitionTo(MembershipStatus.ACTIVE);
            memberships.save(membership);
        }
    }

    private static String readPassword() {
        // The PowerShell parent supplies its SecureString through a private redirected stdin pipe.
        try {
            return new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Password input unavailable");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan("com.wautech.crm")
    @EnableJpaRepositories(basePackages = {"com.wautech.crm.identity.repository", "com.wautech.crm.organization.repository"})
    @Import({UserService.class, CredentialProvisioningService.class})
    public static class SetupConfiguration {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new SecurityConfiguration().passwordEncoder(12);
        }
    }
}
