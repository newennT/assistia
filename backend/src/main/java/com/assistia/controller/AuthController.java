package com.assistia.controller;

import com.assistia.dto.auth.AuthenticatedUserResponse;
import com.assistia.dto.auth.CsrfTokenResponse;
import com.assistia.dto.auth.LoginRequest;
import com.assistia.security.AssistiaPrincipal;
import com.assistia.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
    private final CsrfLogoutHandler csrfLogoutHandler;

    public AuthController(AuthService authService, CsrfTokenRepository csrfTokenRepository) {
        this.authService = authService;
        this.csrfLogoutHandler = new CsrfLogoutHandler(csrfTokenRepository);
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }

    @PostMapping("/login")
    public AuthenticatedUserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        return authService.login(request, servletRequest, servletResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {
        csrfLogoutHandler.logout(request, response, authentication);
        logoutHandler.logout(request, response, authentication);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/me")
    public AuthenticatedUserResponse currentUser(Authentication authentication) {
        return authService.currentUser((AssistiaPrincipal) authentication.getPrincipal());
    }
}
