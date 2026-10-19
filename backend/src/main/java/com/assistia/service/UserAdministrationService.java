package com.assistia.service;

import com.assistia.dto.auth.CreateManagedUserRequest;
import com.assistia.entity.User;
import com.assistia.entity.UserRole;
import com.assistia.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class UserAdministrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAdministrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User createUser(CreateManagedUserRequest request) {
        if (request.role() == UserRole.ADMIN) {
            throw new IllegalArgumentException("La création de comptes ADMIN est réservée à l'initialisation sécurisée.");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Le mot de passe dépasse la limite prise en charge.");
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette adresse e-mail est déjà utilisée.");
        }

        User user = new User(
                email,
                request.firstName().trim(),
                request.lastName().trim(),
                request.role(),
                request.department() == null || request.department().isBlank()
                        ? null
                        : request.department().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getUser(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Identifiant utilisateur invalide.");
        }
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Utilisateur introuvable : " + id));
    }
}
