package com.wautech.crm.platform.security;

import com.wautech.crm.identity.security.CrmUserPrincipal;
import com.wautech.crm.identity.service.DisabledUserException;
import com.wautech.crm.identity.service.UserNotFoundException;
import com.wautech.crm.identity.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class UserEnabledSessionFilter extends OncePerRequestFilter {
    private final UserService userService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public UserEnabledSessionFilter(UserService userService, AuthenticationEntryPoint authenticationEntryPoint) {
        this.userService = userService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }
        if (!(authentication.getPrincipal() instanceof CrmUserPrincipal principal)) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response,
                    new InsufficientAuthenticationException("Authenticated identity is not valid"));
            return;
        }
        try {
            userService.requireEnabledUser(principal.getId());
        } catch (DisabledUserException | UserNotFoundException exception) {
            SecurityContextHolder.clearContext();
            if (request.getSession(false) != null) request.getSession(false).invalidate();
            authenticationEntryPoint.commence(request, response,
                    new InsufficientAuthenticationException("Authentication is no longer valid"));
            return;
        }
        chain.doFilter(request, response);
    }
}
