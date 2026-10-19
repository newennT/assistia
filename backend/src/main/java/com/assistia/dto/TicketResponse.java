package com.assistia.dto;

import com.assistia.entity.Ticket;
import com.assistia.entity.TicketCategory;
import com.assistia.entity.TicketPriority;
import com.assistia.entity.TicketStatus;

import java.time.LocalDateTime;

public record TicketResponse(
        Long id,
        String title,
        String description,
        TicketStatus status,
        TicketPriority priority,
        TicketCategory category,
        Long creatorId,
        Long assigneeId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getCategory(),
                ticket.getCreator().getId(),
                ticket.getAssignee() == null ? null : ticket.getAssignee().getId(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }
}
