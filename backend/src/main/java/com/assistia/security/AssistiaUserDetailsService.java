package com.assistia.security;

import com.assistia.repository.UserRepository;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AssistiaUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AssistiaUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = userRepository.findByEmailIgnoreCase(username.trim())
                .orElseThrow(() -> new UsernameNotFoundException("Identifiants invalides."));
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new DisabledException("Identifiants invalides.");
        }
        return new AssistiaPrincipal(user);
    }
}
