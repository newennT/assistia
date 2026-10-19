package com.assistia.service;

import com.assistia.entity.User;
import com.assistia.entity.UserRole;
import com.assistia.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({AdminBootstrapService.class, AdminBootstrapServiceTest.EncoderConfiguration.class})
class AdminBootstrapServiceTest {

    @Autowired
    private AdminBootstrapService bootstrapService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void preserveSystemAccountWithoutCredentials() {
        userRepository.deleteAll();
        userRepository.saveAndFlush(new User(
                AdminBootstrapService.SYSTEM_ACCOUNT_EMAIL,
                "System",
                "Administrator",
                UserRole.ADMIN,
                "IT"));
    }

    @Test
    void createsFirstRealAdministratorWithBcryptWithoutChangingSystemAccount() {
        char[] password = "secure-initial-password".toCharArray();
        User administrator = bootstrapService.createFirstAdministrator(
                "ADMIN@assistia.local", "Ada", "Lovelace", "IT", password, password.clone());

        assertThat(administrator.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(administrator.getEmail()).isEqualTo("admin@assistia.local");
        assertThat(passwordEncoder.matches("secure-initial-password", administrator.getPasswordHash())).isTrue();
        assertThat(administrator.getPasswordHash()).startsWith("$2");

        User system = userRepository.findByEmailIgnoreCase(AdminBootstrapService.SYSTEM_ACCOUNT_EMAIL).orElseThrow();
        assertThat(system.getPasswordHash()).isNull();
        assertThat(system.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void refusesPasswordMismatchAndAnySecondAdministratorBootstrap() {
        assertThatThrownBy(() -> bootstrapService.createFirstAdministrator(
                "admin@assistia.local",
                "Ada",
                "Lovelace",
                "IT",
                "secure-initial-password".toCharArray(),
                "different-password".toCharArray()))
                .isInstanceOf(IllegalArgumentException.class);

        bootstrapService.createFirstAdministrator(
                "admin@assistia.local",
                "Ada",
                "Lovelace",
                "IT",
                "secure-initial-password".toCharArray(),
                "secure-initial-password".toCharArray());
        assertThatThrownBy(() -> bootstrapService.createFirstAdministrator(
                "second-admin@assistia.local",
                "Grace",
                "Hopper",
                "IT",
                "another-secure-password".toCharArray(),
                "another-secure-password".toCharArray()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Un administrateur initial existe déjà.");
    }

    @TestConfiguration
    static class EncoderConfiguration {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }
    }
}
