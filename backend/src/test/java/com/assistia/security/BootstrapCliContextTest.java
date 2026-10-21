package com.assistia.security;

import com.assistia.service.AdminBootstrapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BootstrapCliContextTest {

    @Autowired
    private AdminBootstrapService adminBootstrapService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Test
    void cliContextStartsWithoutServletFilterChainAndKeepsBootstrapDependencies() {
        assertThat(adminBootstrapService).isNotNull();
        assertThat(passwordEncoder).isNotNull();
        assertThat(applicationContext.containsBean("securityFilterChain")).isFalse();
    }
}
