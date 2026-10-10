package com.wautech.crm.identity.service;

import com.wautech.crm.identity.dto.AuthenticatedUserResponse;
import com.wautech.crm.identity.dto.LoginRequest;
import com.wautech.crm.identity.security.CrmUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class AuthenticationService {
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;

    public AuthenticationService(AuthenticationManager authenticationManager,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository) {
        this.authenticationManager = authenticationManager;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
    }

    public AuthenticatedUserResponse login(LoginRequest request, HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidCredentialsException();
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email().trim(), request.password()));
        } catch (AuthenticationException exception) {
            throw new InvalidCredentialsException();
        }

        sessionAuthenticationStrategy.onAuthentication(authentication, servletRequest, servletResponse);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        return AuthenticatedUserResponse.from((CrmUserPrincipal) authentication.getPrincipal());
    }
}
