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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    public TicketService(TicketRepository ticketRepository, UserRepository userRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Ticket createTicket(
            String title,
            String description,
            TicketStatus status,
            TicketPriority priority,
            TicketCategory category,
            Long creatorId) {
        requireValue(title, "Le titre est obligatoire.");
        requireValue(description, "La description est obligatoire.");
        requireValue(status, "Le statut est obligatoire.");
        requireValue(priority, "La priorité est obligatoire.");
        requireValue(category, "La catégorie est obligatoire.");

        User creator = findUser(creatorId, "créateur");
        return ticketRepository.save(new Ticket(title, description, status, priority, category, creator, null));
    }

    public Ticket getTicketById(Long ticketId) {
        return findTicket(ticketId);
    }

    public List<Ticket> getAllTickets() {
        return ticketRepository.findAll();
    }

    public Ticket getTicketByIdForCreator(Long ticketId, Long creatorId) {
        Ticket ticket = findTicket(ticketId);
        if (!ticket.getCreator().getId().equals(creatorId)) {
            throw new EntityNotFoundException("Ticket introuvable : " + ticketId);
        }
        return ticket;
    }

    public List<Ticket> findByCreator(Long creatorId, TicketStatus status, TicketPriority priority) {
        requireId(creatorId, "créateur");
        if (status != null && priority != null) {
            return ticketRepository.findByCreator_IdAndStatusAndPriority(creatorId, status, priority);
        }
        if (status != null) {
            return ticketRepository.findByCreator_IdAndStatus(creatorId, status);
        }
        if (priority != null) {
            return ticketRepository.findByCreator_IdAndPriority(creatorId, priority);
        }
        return ticketRepository.findByCreator_Id(creatorId);
    }

    @Transactional
    public Ticket updateTicket(Long ticketId, String title, String description, TicketCategory category) {
        requireValue(title, "Le titre est obligatoire.");
        requireValue(description, "La description est obligatoire.");

        Ticket ticket = findTicket(ticketId);
        ticket.setTitle(title);
        ticket.setDescription(description);
        if (category != null) {
            ticket.setCategory(category);
        }
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket updateTicketForCreator(
            Long ticketId,
            Long creatorId,
            String title,
            String description,
            TicketCategory category) {
        Ticket ticket = getTicketByIdForCreator(ticketId, creatorId);
        if (category != null && !ticket.getCategory().equals(category)) {
            throw new AccessDeniedException("La catégorie ne peut être modifiée que par un technicien.");
        }
        requireValue(title, "Le titre est obligatoire.");
        requireValue(description, "La description est obligatoire.");
        ticket.setTitle(title);
        ticket.setDescription(description);
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket changeStatus(Long ticketId, TicketStatus status) {
        requireValue(status, "Le statut est obligatoire.");

        Ticket ticket = findTicket(ticketId);
        ticket.setStatus(status);
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket changePriority(Long ticketId, TicketPriority priority) {
        requireValue(priority, "La priorité est obligatoire.");

        Ticket ticket = findTicket(ticketId);
        ticket.setPriority(priority);
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket assignTechnician(Long ticketId, Long technicianId) {
        Ticket ticket = findTicket(ticketId);
        User technician = findUser(technicianId, "technicien");
        if (technician.getRole() != UserRole.TECHNICIAN) {
            throw new IllegalArgumentException("L'utilisateur assigné doit avoir le rôle TECHNICIAN.");
        }

        ticket.setAssignee(technician);
        return ticketRepository.save(ticket);
    }

    public List<Ticket> findByStatus(TicketStatus status) {
        requireValue(status, "Le statut est obligatoire.");
        return ticketRepository.findByStatus(status);
    }

    public List<Ticket> findByPriority(TicketPriority priority) {
        requireValue(priority, "La priorité est obligatoire.");
        return ticketRepository.findByPriority(priority);
    }

    public List<Ticket> findByStatusAndPriority(TicketStatus status, TicketPriority priority) {
        requireValue(status, "Le statut est obligatoire.");
        requireValue(priority, "La priorité est obligatoire.");
        return ticketRepository.findByStatusAndPriority(status, priority);
    }

    private Ticket findTicket(Long ticketId) {
        requireId(ticketId, "ticket");
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Ticket introuvable : " + ticketId));
    }

    private User findUser(Long userId, String description) {
        requireId(userId, description);
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(description + " introuvable : " + userId));
    }

    private void requireId(Long id, String description) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Identifiant de " + description + " invalide.");
        }
    }

    private void requireValue(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }
}