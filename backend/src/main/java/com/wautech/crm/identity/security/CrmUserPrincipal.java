package com.wautech.crm.identity.security;

import com.wautech.crm.identity.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Trusted login principal; authorities are intentionally empty until Feature #14 RBAC. */
public final class CrmUserPrincipal implements UserDetails {
    private final UUID id;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final String passwordHash;
    private final boolean enabled;

    public CrmUserPrincipal(User user, String passwordHash) {
        this(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), passwordHash, user.isEnabled());
    }

    public CrmUserPrincipal(UUID id, String email, String firstName, String lastName, String passwordHash, boolean enabled) {
        this.id = id;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
}
