package com.assistia.service;

import com.assistia.entity.User;
import com.assistia.entity.UserRole;
import com.assistia.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AdminBootstrapService {

    public static final String SYSTEM_ACCOUNT_EMAIL = "system@assistia.local";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public User createFirstAdministrator(
            String email,
            String firstName,
            String lastName,
            String department,
            char[] password,
            char[] passwordConfirmation) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches() || normalizedEmail.length() > 255) {
            throw new IllegalArgumentException("Adresse e-mail invalide.");
        }
        if (normalizedEmail.equalsIgnoreCase(SYSTEM_ACCOUNT_EMAIL)) {
            throw new IllegalArgumentException("Le compte technique système ne peut pas servir à la connexion.");
        }
        if (password == null || passwordConfirmation == null
                || !Arrays.equals(password, passwordConfirmation)) {
            throw new IllegalArgumentException("Les mots de passe ne correspondent pas.");
        }
        String rawPassword = new String(password);
        if (rawPassword.length() < 12 || rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Le mot de passe doit faire au moins 12 caractères et au plus 72 octets UTF-8.");
        }
        if (firstName == null || firstName.isBlank() || firstName.length() > 100
                || lastName == null || lastName.isBlank() || lastName.length() > 100
                || department != null && department.length() > 100) {
            throw new IllegalArgumentException("Les informations de l'administrateur sont invalides.");
        }
        if (userRepository.existsByRoleAndPasswordHashIsNotNullAndEmailNotIgnoreCase(
                UserRole.ADMIN, SYSTEM_ACCOUNT_EMAIL)) {
            throw new IllegalStateException("Un administrateur initial existe déjà.");
        }
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new IllegalStateException("Cette adresse e-mail est déjà utilisée.");
        }

        User administrator = new User(
                normalizedEmail,
                firstName.trim(),
                lastName.trim(),
                UserRole.ADMIN,
                department == null || department.isBlank() ? null : department.trim());
        administrator.setPasswordHash(passwordEncoder.encode(rawPassword));
        return userRepository.save(administrator);
    }
}
