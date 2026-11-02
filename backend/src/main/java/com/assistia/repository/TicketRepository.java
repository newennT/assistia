package com.assistia.repository;

import com.assistia.entity.Ticket;
import com.assistia.entity.TicketPriority;
import com.assistia.entity.TicketStatus;
import com.assistia.entity.TicketCategory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    @Query("select t from Ticket t where (:creatorId is null or t.creator.id = :creatorId) " +
            "and (:category is null or t.category = :category) " +
            "and (lower(t.title) like lower(concat('%', :query, '%')) or lower(cast(t.description as string)) like lower(concat('%', :query, '%'))) " +
            "order by t.updatedAt desc")
    List<Ticket> searchDiagnosticTickets(@Param("query") String query, @Param("category") TicketCategory category,
                                        @Param("creatorId") Long creatorId, Pageable pageable);
    List<Ticket> findByStatus(TicketStatus status);

    List<Ticket> findByPriority(TicketPriority priority);

    
    List<Ticket> findByStatusAndPriority(TicketStatus status, TicketPriority priority);

    List<Ticket> findByCreator_Id(Long creatorId);

    List<Ticket> findByCreator_IdAndStatus(Long creatorId, TicketStatus status);

    List<Ticket> findByCreator_IdAndPriority(Long creatorId, TicketPriority priority);

    List<Ticket> findByCreator_IdAndStatusAndPriority(Long creatorId, TicketStatus status, TicketPriority priority);
}
