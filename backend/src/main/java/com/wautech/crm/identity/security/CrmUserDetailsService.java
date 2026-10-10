package com.wautech.crm.identity.security;

import com.wautech.crm.identity.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class CrmUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    private final String unprovisionedHash;

    public CrmUserDetailsService(UserRepository userRepository, UnprovisionedPasswordHash unprovisionedHash) {
        this.userRepository = userRepository;
        this.unprovisionedHash = unprovisionedHash.value();
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .map(user -> new CrmUserPrincipal(user,
                        user.getPasswordHash() == null ? unprovisionedHash : user.getPasswordHash()))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
