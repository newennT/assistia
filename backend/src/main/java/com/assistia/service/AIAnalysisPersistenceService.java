package com.assistia.service;

import com.assistia.entity.AIAnalysis;
import com.assistia.entity.Ticket;
import com.assistia.entity.TicketCategory;
import com.assistia.entity.TicketPriority;
import com.assistia.repository.AIAnalysisRepository;
import com.assistia.repository.TicketRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AIAnalysisPersistenceService {

    private final AIAnalysisRepository analysisRepository;
    private final TicketRepository ticketRepository;

    public AIAnalysisPersistenceService(AIAnalysisRepository analysisRepository, TicketRepository ticketRepository) {
        this.analysisRepository = analysisRepository;
        this.ticketRepository = ticketRepository;
    }

    @Transactional
    public AIAnalysis persist(
            Long ticketId,
            String summary,
            TicketCategory category,
            TicketPriority priority,
            String possibleCausesJson,
            String suggestedActionsJson,
            BigDecimal confidence,
            String model) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Ticket introuvable : " + ticketId));
        AIAnalysis analysis = new AIAnalysis(
                ticket, summary, category, priority, possibleCausesJson, suggestedActionsJson,
                confidence, model);
        return analysisRepository.saveAndFlush(analysis);
    }

    @Transactional(readOnly = true)
    public List<AIAnalysis> history(Long ticketId) {
        return analysisRepository.findAllByTicket_IdOrderByCreatedAtDesc(ticketId);
    }
}
