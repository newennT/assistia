package com.assistia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.Console;
import java.util.Arrays;

@SpringBootApplication
public class AssistiaApplication {

    public static void main(String[] args) {
        if (java.util.Arrays.asList(args).contains("--assistia.bootstrap-admin")) {
            String[] applicationArgs = java.util.Arrays.stream(args)
                    .filter(argument -> !argument.equals("--assistia.bootstrap-admin"))
                    .toArray(String[]::new);
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(AssistiaApplication.class)
                    .web(WebApplicationType.NONE)
                    .run(applicationArgs)) {
                createFirstAdministrator(context);
            }
            return;
        }
        SpringApplication.run(AssistiaApplication.class, args);
    }

    private static void createFirstAdministrator(ConfigurableApplicationContext context) {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException(
                    "Une console interactive est requise; aucun mot de passe ne doit être passé en argument.");
        }

        String email = console.readLine("E-mail du premier administrateur : ");
        String firstName = console.readLine("Prénom : ");
        String lastName = console.readLine("Nom : ");
        String department = console.readLine("Département (facultatif) : ");
        char[] password = console.readPassword("Mot de passe (12 caractères minimum) : ");
        char[] confirmation = console.readPassword("Confirmer le mot de passe : ");
        try {
            context.getBean(com.assistia.service.AdminBootstrapService.class)
                    .createFirstAdministrator(email, firstName, lastName, department, password, confirmation);
            console.printf("Premier administrateur créé : %s%n", email.trim());
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
            if (confirmation != null) {
                Arrays.fill(confirmation, '\0');
            }
        }
    }
}