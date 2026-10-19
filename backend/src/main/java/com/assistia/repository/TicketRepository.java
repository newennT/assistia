package com.assistia.repository;

import com.assistia.entity.Ticket;
import com.assistia.entity.TicketPriority;
import com.assistia.entity.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByStatus(TicketStatus status);

    List<Ticket> findByPriority(TicketPriority priority);

    
    List<Ticket> findByStatusAndPriority(TicketStatus status, TicketPriority priority);

    List<Ticket> findByCreator_Id(Long creatorId);

    List<Ticket> findByCreator_IdAndStatus(Long creatorId, TicketStatus status);

    List<Ticket> findByCreator_IdAndPriority(Long creatorId, TicketPriority priority);

    List<Ticket> findByCreator_IdAndStatusAndPriority(Long creatorId, TicketStatus status, TicketPriority priority);
}