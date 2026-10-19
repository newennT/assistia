package com.assistia.dto;

import com.assistia.entity.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeTicketStatusRequest(@NotNull TicketStatus status) {
}
