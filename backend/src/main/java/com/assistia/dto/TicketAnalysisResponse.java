package com.assistia.dto;

import com.assistia.entity.AIAnalysis;
import com.assistia.entity.TicketCategory;
import com.assistia.entity.TicketPriority;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TicketAnalysisResponse(
        Long id,
        Long ticketId,
        String summary,
        TicketCategory suggestedCategory,
        TicketPriority suggestedPriority,
        List<String> possibleCauses,
        List<String> suggestedActions,
        BigDecimal confidence,
        String modelUsed,
        LocalDateTime createdAt) {

    public static TicketAnalysisResponse from(AIAnalysis analysis, List<String> possibleCauses, List<String> actions) {
        return new TicketAnalysisResponse(
                analysis.getId(),
                analysis.getTicket().getId(),
                analysis.getSummary(),
                analysis.getSuggestedCategory(),
                analysis.getSuggestedPriority(),
                possibleCauses,
                actions,
                analysis.getConfidenceScore(),
                analysis.getModelUsed(),
                analysis.getCreatedAt());
    }
}
