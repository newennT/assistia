package com.assistia.service;

import com.assistia.dto.auth.AuthenticatedUserResponse;
import com.assistia.dto.auth.LoginRequest;
import com.assistia.entity.User;
import com.assistia.repository.UserRepository;
import com.assistia.security.AssistiaPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final CsrfAuthenticationStrategy csrfAuthenticationStrategy;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = new ChangeSessionIdAuthenticationStrategy();
        this.csrfAuthenticationStrategy = new CsrfAuthenticationStrategy(csrfTokenRepository);
    }

    public AuthenticatedUserResponse login(
            LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalidCredentials();
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.email().trim(), request.password()));
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            throw invalidCredentials();
        }

        servletRequest.getSession(true);
        sessionAuthenticationStrategy.onAuthentication(authentication, servletRequest, servletResponse);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        csrfAuthenticationStrategy.onAuthentication(authentication, servletRequest, servletResponse);

        AssistiaPrincipal principal = (AssistiaPrincipal) authentication.getPrincipal();
        return findUserResponse(principal.id());
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserResponse currentUser(AssistiaPrincipal principal) {
        return findUserResponse(principal.id());
    }

    private AuthenticatedUserResponse findUserResponse(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationServiceException("Compte indisponible."));
        return AuthenticatedUserResponse.from(user);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides.");
    }
}
