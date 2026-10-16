package com.assistia.entity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.assistia.repository.TicketRepository;
import com.assistia.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class EntityModelTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void ticketFieldsAndRelationsArePersistedAndReadable() {
        User creator = newUser("creator", UserRole.USER);
        User technician = newUser("technician", UserRole.TECHNICIAN);

        userRepository.saveAndFlush(creator);
        userRepository.saveAndFlush(technician);

        Ticket ticket = new Ticket(
                "VPN indisponible",
                "La connexion VPN échoue après la dernière mise à jour. Éléments à vérifier : réseau, DNS et authentification.",
                TicketStatus.OPEN,
                TicketPriority.HIGH,
                TicketCategory.VPN,
                creator,
                technician);

        Ticket savedTicket = ticketRepository.saveAndFlush(ticket);
        Long ticketId = savedTicket.getId();
        entityManager.clear();

        Ticket managed = ticketRepository.findById(ticketId).orElseThrow();

        assertThat(managed).isNotNull();
        assertThat(managed.getTitle()).isEqualTo("VPN indisponible");
        assertThat(managed.getDescription())
                .isEqualTo(
                        "La connexion VPN échoue après la dernière mise à jour. Éléments à vérifier : réseau, DNS et authentification.");
        assertThat(managed.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(managed.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(managed.getCategory()).isEqualTo(TicketCategory.VPN);
        assertThat(managed.getCreator().getId()).isEqualTo(creator.getId());
        assertThat(managed.getCreator().getEmail()).isEqualTo(creator.getEmail());
        assertThat(managed.getAssignee().getId()).isEqualTo(technician.getId());
        assertThat(managed.getAssignee().getRole()).isEqualTo(UserRole.TECHNICIAN);
        assertThat(managed.getCreatedAt()).isNotNull();
        assertThat(managed.getUpdatedAt()).isNotNull();
    }

    @Test
    void userEmailMustBeUnique() {
        String email = "duplicate-" + UUID.randomUUID() + "@assistia.local";
        userRepository.saveAndFlush(new User(email, "Alice", "Martin", UserRole.USER, "IT"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new User(email, "Bob", "Dumont", UserRole.TECHNICIAN, "IT")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ticketRequiresCreator() {
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
