package com.assistia.service;

import com.assistia.entity.AIAnalysis;
import com.assistia.entity.Ticket;
import com.assistia.entity.TicketCategory;
import com.assistia.entity.TicketPriority;
import com.assistia.entity.TicketStatus;
import com.assistia.entity.User;
import com.assistia.entity.UserRole;
import com.assistia.repository.AIAnalysisRepository;
import com.assistia.repository.TicketRepository;
import com.assistia.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(AIAnalysisPersistenceService.class)
class AIAnalysisPersistenceServiceTest {

    @Autowired
    private AIAnalysisPersistenceService persistenceService;

    @Autowired
    private AIAnalysisRepository analysisRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void storesMultipleHistoricalAnalysesForTheirTicket() throws InterruptedException {
        User creator = userRepository.saveAndFlush(new User(
                "analysis-" + UUID.randomUUID() + "@assistia.local", "Alex", "Test", UserRole.USER, "IT"));
        Ticket ticket = ticketRepository.saveAndFlush(new Ticket(
                "VPN en panne", "La connexion échoue.", TicketStatus.OPEN, TicketPriority.MEDIUM,
                TicketCategory.VPN, creator, null));

        AIAnalysis first = persistenceService.persist(ticket.getId(), "Première analyse", TicketCategory.VPN,
                TicketPriority.MEDIUM, "[\"DNS\"]", "[\"Tester DNS\"]", new BigDecimal("0.7200"), "gemini-model-a");
        Thread.sleep(5);
        AIAnalysis second = persistenceService.persist(ticket.getId(), "Deuxième analyse", TicketCategory.NETWORK,
                TicketPriority.HIGH, "[\"Route\"]", "[\"Tester route\"]", new BigDecimal("0.9100"), "gemini-model-b");

        assertThat(analysisRepository.findAllByTicket_IdOrderByCreatedAtDesc(ticket.getId()))
                .extracting(AIAnalysis::getId)
                .containsExactly(second.getId(), first.getId());
        assertThat(second.getTicket().getId()).isEqualTo(ticket.getId());
        assertThat(second.getModelUsed()).isEqualTo("gemini-model-b");
        assertThat(second.getConfidenceScore()).isEqualByComparingTo("0.9100");
        assertThat(second.getCreatedAt()).isNotNull();
    }
}
