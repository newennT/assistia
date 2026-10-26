package com.assistia.dto;

import com.assistia.entity.TicketCategory;
import com.assistia.entity.TicketPriority;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record TicketAnalysisContent(
        @NotBlank @Size(max = 4000) String summary,
        @NotNull TicketCategory suggestedCategory,
        @NotNull TicketPriority suggestedPriority,
        @NotEmpty @Size(max = 8) List<@NotBlank @Size(max = 1000) String> possibleCauses,
        @NotEmpty @Size(max = 8) List<@NotBlank @Size(max = 1000) String> suggestedActions,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence) {
}
