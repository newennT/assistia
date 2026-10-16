package com.assistia.entity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class EntityModelTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void ticketRelationsArePersisted() {
        User creator = new User("alice@assistia.local", "Alice", "Martin", UserRole.USER, "IT");
        User technician = new User("bob@assistia.local", "Bob", "Dumont", UserRole.TECHNICIAN, "Infrastructure");

        entityManager.persist(creator);
        entityManager.persist(technician);

        Ticket ticket = new Ticket(
                "VPN unavailable",
                "The VPN stops after the latest update.",
                TicketStatus.OPEN,
                TicketPriority.HIGH,
                TicketCategory.VPN,
                creator,
                technician
        );

        entityManager.persist(ticket);
        entityManager.flush();
        entityManager.clear();

        Ticket managed = entityManager.find(Ticket.class, ticket.getId());

        assertThat(managed).isNotNull();
        assertThat(managed.getCreator()).isNotNull();
        assertThat(managed.getCreator().getEmail()).isEqualTo("alice@assistia.local");
        assertThat(managed.getAssignee()).isNotNull();
        assertThat(managed.getAssignee().getRole()).isEqualTo(UserRole.TECHNICIAN);
    }
}
