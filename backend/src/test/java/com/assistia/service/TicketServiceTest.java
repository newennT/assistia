package com.assistia.service;

import com.assistia.entity.Ticket;
import com.assistia.entity.TicketCategory;
import com.assistia.entity.TicketPriority;
import com.assistia.entity.TicketStatus;
import com.assistia.entity.User;
import com.assistia.entity.UserRole;
import com.assistia.repository.TicketRepository;
import com.assistia.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TicketService.class)
class TicketServiceTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void createsAndRetrievesTicket() {
        User creator = newUser("creator", UserRole.USER);
        userRepository.saveAndFlush(creator);

        Ticket created = ticketService.createTicket(
                "VPN indisponible",
                "La connexion VPN échoue.",
                TicketStatus.OPEN,
                TicketPriority.HIGH,
                TicketCategory.VPN,
                creator.getId());

        Ticket retrieved = ticketService.getTicketById(created.getId());

        assertThat(retrieved.getId()).isEqualTo(created.getId());
        assertThat(retrieved.getCreator().getId()).isEqualTo(creator.getId());
        assertThat(retrieved.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(retrieved.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(retrieved.getCreatedAt()).isNotNull();
    }

    @Test
    void refusesCreationWhenCreatorDoesNotExist() {
        assertThatThrownBy(() -> ticketService.createTicket(
                "Ticket sans créateur",
                "Description",
                TicketStatus.OPEN,
                TicketPriority.LOW,
                TicketCategory.GENERAL,
                999_999L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void refusesMissingTicketAndInvalidStatus() {
        assertThatThrownBy(() -> ticketService.getTicketById(999_999L))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> ticketService.changeStatus(999_999L, TicketStatus.OPEN))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> ticketService.changeStatus(1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

        @Test
        void updatesOnlyEditableTicketInformation() {
                User creator = newUser("creator", UserRole.USER);
                userRepository.saveAndFlush(creator);
                Ticket ticket = createTicket(creator, TicketStatus.OPEN, TicketPriority.HIGH);
                Long ticketId = ticket.getId();
                var createdAt = ticket.getCreatedAt();

                Ticket updated = ticketService.updateTicket(
                                ticketId,
                                "Nouveau titre",
                                "Nouvelle description",
                                TicketCategory.NETWORK);

                assertThat(updated.getId()).isEqualTo(ticketId);
                assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
                assertThat(updated.getTitle()).isEqualTo("Nouveau titre");
                assertThat(updated.getDescription()).isEqualTo("Nouvelle description");
                assertThat(updated.getCategory()).isEqualTo(TicketCategory.NETWORK);
                assertThat(updated.getStatus()).isEqualTo(TicketStatus.OPEN);
                assertThat(updated.getPriority()).isEqualTo(TicketPriority.HIGH);
                assertThat(ticketService.getAllTickets()).extracting(Ticket::getId).contains(ticketId);
        }

    @Test
    void changesStatusAndPriorityAndSupportsFiltering() {
        User creator = newUser("creator", UserRole.USER);
        userRepository.saveAndFlush(creator);
        Ticket ticket = createTicket(creator, TicketStatus.OPEN, TicketPriority.LOW);

        ticketService.changeStatus(ticket.getId(), TicketStatus.IN_PROGRESS);
        ticketService.changePriority(ticket.getId(), TicketPriority.CRITICAL);

        assertThat(ticketService.getTicketById(ticket.getId()).getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticketService.getTicketById(ticket.getId()).getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(ticketService.findByStatus(TicketStatus.IN_PROGRESS)).extracting(Ticket::getId)
                .contains(ticket.getId());
        assertThat(ticketService.findByPriority(TicketPriority.CRITICAL)).extracting(Ticket::getId)
                .contains(ticket.getId());
        assertThat(ticketService.findByStatusAndPriority(TicketStatus.IN_PROGRESS, TicketPriority.CRITICAL))
                .extracting(Ticket::getId)
                .containsExactly(ticket.getId());
    }

    @Test
    void assignsOnlyExistingTechnicians() {
        User creator = newUser("creator", UserRole.USER);
        User technician = newUser("technician", UserRole.TECHNICIAN);
        User regularUser = newUser("regular", UserRole.USER);
        userRepository.saveAndFlush(creator);
        userRepository.saveAndFlush(technician);
        userRepository.saveAndFlush(regularUser);
        Ticket ticket = createTicket(creator, TicketStatus.OPEN, TicketPriority.MEDIUM);

        Ticket assigned = ticketService.assignTechnician(ticket.getId(), technician.getId());

        assertThat(assigned.getAssignee().getId()).isEqualTo(technician.getId());
        assertThatThrownBy(() -> ticketService.assignTechnician(ticket.getId(), 999_999L))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> ticketService.assignTechnician(ticket.getId(), regularUser.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackTicketCreationWhenTheTransactionFails() {
        User creator = userRepository.saveAndFlush(newUser("creator", UserRole.USER));
        long ticketCountBefore = ticketRepository.count();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            ticketService.createTicket(
                    "Ticket annulé",
                    "La transaction va échouer.",
                    TicketStatus.OPEN,
                    TicketPriority.LOW,
                    TicketCategory.GENERAL,
                    creator.getId());
            throw new IllegalStateException("rollback demandé par le test");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(ticketRepository.count()).isEqualTo(ticketCountBefore);
    }

    private Ticket createTicket(User creator, TicketStatus status, TicketPriority priority) {
        return ticketService.createTicket(
                "Ticket de test",
                "Description de test",
                status,
                priority,
                TicketCategory.GENERAL,
                creator.getId());
    }

    private User newUser(String prefix, UserRole role) {
        return new User(prefix + "-" + UUID.randomUUID() + "@assistia.local", "Test", prefix, role, "IT");
    }
}