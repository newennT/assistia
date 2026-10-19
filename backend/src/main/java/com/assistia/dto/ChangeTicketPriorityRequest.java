package com.assistia.dto;

import com.assistia.entity.TicketPriority;
import jakarta.validation.constraints.NotNull;

public record ChangeTicketPriorityRequest(@NotNull TicketPriority priority) {
}
