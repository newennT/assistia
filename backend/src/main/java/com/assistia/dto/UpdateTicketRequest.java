package com.assistia.dto;

import com.assistia.entity.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTicketRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank String description,
        @NotNull TicketCategory category) {
}
