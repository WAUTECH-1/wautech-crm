package com.wautech.crm.identity.service;
import com.wautech.crm.identity.dto.SelfProfileRequest;
import com.wautech.crm.identity.repository.UserRepository;
import com.wautech.crm.platform.test.SecurityTestIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class CurrentUserProfileServiceTest {
    UserService users = mock(UserService.class);
    UserRepository repository = mock(UserRepository.class);
    CurrentUserProfileService service = new CurrentUserProfileService(users, repository);
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void noPrincipalCannotReadOrWrite() {
        assertThrows(AccessDeniedException.class, () -> service.get());
        assertThrows(AccessDeniedException.class, () -> service.update(new SelfProfileRequest("A", "B")));
        verifyNoInteractions(repository, users);
    }
    @Test void updatesOnlyPrincipalNamesAndPreservesEmail() {
        var user = SecurityTestIdentity.user(); org.springframework.test.util.ReflectionTestUtils.setField(user, "id", SecurityTestIdentity.USER_ID);
        var email = user.getEmail();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(SecurityTestIdentity.principal(), "", java.util.List.of()));
        when(users.requireEnabledUser(SecurityTestIdentity.USER_ID)).thenReturn(user);
        when(repository.save(user)).thenReturn(user);
        var result = service.update(new SelfProfileRequest(" Updated ", " Name "));
        assertEquals("Updated", result.firstName()); assertEquals("Name", result.lastName());
        assertEquals(email, result.email()); assertEquals(SecurityTestIdentity.USER_ID, result.id());
        verify(users).requireEnabledUser(SecurityTestIdentity.USER_ID);
    }
}
