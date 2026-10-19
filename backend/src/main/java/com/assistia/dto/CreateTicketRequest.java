package com.assistia.dto;

import com.assistia.entity.TicketCategory;
import com.assistia.entity.TicketPriority;
import com.assistia.entity.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank String description,
        TicketStatus status,
        TicketPriority priority,
        @NotNull TicketCategory category) {
}
