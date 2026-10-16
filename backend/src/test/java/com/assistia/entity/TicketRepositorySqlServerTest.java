package com.assistia.entity;

import com.assistia.repository.TicketRepository;
import com.assistia.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("sqlserver")
@EnabledIfEnvironmentVariable(named = "ASSISTIA_SQLSERVER_TESTS", matches = "true")
class TicketRepositorySqlServerTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void ticketFieldsAndRelationsArePersistedAndReadableOnSqlServer() {
        User creator = newUser("creator", UserRole.USER);
        User technician = newUser("technician", UserRole.TECHNICIAN);
        userRepository.saveAndFlush(creator);
        userRepository.saveAndFlush(technician);

        String description = "La connexion VPN échoue après la dernière mise à jour. Éléments à vérifier : réseau, DNS et authentification.";
        Ticket ticket = new Ticket(
                "VPN indisponible",
                description,
                TicketStatus.OPEN,
                TicketPriority.HIGH,
                TicketCategory.VPN,
                creator,
                technician);

        Long ticketId = ticketRepository.saveAndFlush(ticket).getId();
        entityManager.clear();

        Ticket reloaded = ticketRepository.findById(ticketId).orElseThrow();

        assertThat(reloaded.getTitle()).isEqualTo("VPN indisponible");
        assertThat(reloaded.getDescription()).isEqualTo(description);
        assertThat(reloaded.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(reloaded.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(reloaded.getCategory()).isEqualTo(TicketCategory.VPN);
        assertThat(reloaded.getCreator().getEmail()).isEqualTo(creator.getEmail());
        assertThat(reloaded.getAssignee().getEmail()).isEqualTo(technician.getEmail());
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void userEmailMustBeUniqueOnSqlServer() {
        String email = "duplicate-" + UUID.randomUUID() + "@assistia.local";
        userRepository.saveAndFlush(new User(email, "Alice", "Martin", UserRole.USER, "IT"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new User(email, "Bob", "Dumont", UserRole.TECHNICIAN, "IT")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ticketRequiresCreatorOnSqlServer() {
        Ticket ticketWithoutCreator = new Ticket(
                "Ticket sans créateur",
                "Ce ticket doit être rejeté.",
                TicketStatus.OPEN,
                TicketPriority.LOW,
                TicketCategory.GENERAL,
                null,
                null);

        assertThatThrownBy(() -> ticketRepository.saveAndFlush(ticketWithoutCreator))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User newUser(String prefix, UserRole role) {
        return new User(prefix + "-" + UUID.randomUUID() + "@assistia.local", "Test", prefix, role, "IT");
    }
}
