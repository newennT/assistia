package com.assistia.service;

import com.assistia.dto.TicketAnalysisResponse;
import com.assistia.entity.Ticket;
import com.assistia.entity.TicketCategory;
import com.assistia.entity.TicketPriority;
import com.assistia.entity.TicketStatus;
import com.assistia.entity.User;
import com.assistia.entity.UserRole;
import com.assistia.service.TicketAnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketAnalysisServiceTest {

    private TicketService ticketService;
    private AIAnalysisPersistenceService persistenceService;
    private ObjectProvider<GeminiAnalysisProvider> providerProvider;
    private GeminiAnalysisProvider provider;
    private TicketAnalysisService analysisService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ticketService = mock(TicketService.class);
        persistenceService = mock(AIAnalysisPersistenceService.class);
        providerProvider = mock(ObjectProvider.class);
        provider = mock(GeminiAnalysisProvider.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        analysisService = new TicketAnalysisService(
                ticketService, persistenceService, providerProvider, objectMapper, validator, "gemini-3.8-flash");
    }

    @Test
    void validatesStructuredOutputAndPersistsItAsHistoricalAnalysis() throws Exception {
        Ticket ticket = ticket();
        when(ticketService.getTicketByIdForCreator(42L, 8L)).thenReturn(ticket);
        when(providerProvider.getIfAvailable()).thenReturn(provider);
        when(provider.generate(any())).thenReturn(validJson());
        com.assistia.entity.AIAnalysis saved = persistedAnalysis(ticket);
        when(persistenceService.persist(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(saved);

        TicketAnalysisResponse result = analysisService.analyze(42L, 8L, UserRole.USER);

        assertThat(result.summary()).isEqualTo("VPN indisponible après une mise à jour.");
        assertThat(result.suggestedCategory()).isEqualTo(TicketCategory.VPN);
        assertThat(result.suggestedPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(result.possibleCauses()).containsExactly("Résolution DNS défaillante");
        assertThat(result.suggestedActions()).containsExactly("Tester la résolution DNS");
        assertThat(result.confidence()).isEqualByComparingTo("0.7500");
        verify(ticketService).getTicketByIdForCreator(42L, 8L);
        verify(persistenceService).persist(42L, "VPN indisponible après une mise à jour.",
                TicketCategory.VPN, TicketPriority.HIGH, "[\"Résolution DNS défaillante\"]",
                "[\"Tester la résolution DNS\"]", new BigDecimal("0.7500"), "gemini-3.8-flash");
        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void refusesTicketsOutsideUsersScopeBeforeCallingGemini() {
        when(ticketService.getTicketByIdForCreator(42L, 9L))
                .thenThrow(new EntityNotFoundException("Ticket introuvable"));

        assertThatThrownBy(() -> analysisService.analyze(42L, 9L, UserRole.USER))
                .isInstanceOf(EntityNotFoundException.class);
        verify(providerProvider, never()).getIfAvailable();
    }

    @Test
    void letsTechniciansUseGeneralTicketAccess() {
        when(ticketService.getTicketById(42L)).thenReturn(ticket());
        when(providerProvider.getIfAvailable()).thenReturn(provider);
        when(provider.generate(any())).thenReturn("invalid JSON");

        assertThatThrownBy(() -> analysisService.analyze(42L, 12L, UserRole.TECHNICIAN))
                .isInstanceOf(ResponseStatusException.class);
        verify(ticketService).getTicketById(42L);
    }

    @Test
    void reportsMissingConfigurationWithoutBlockingApplication() {
        when(ticketService.getTicketByIdForCreator(42L, 8L)).thenReturn(ticket());
        when(providerProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> analysisService.analyze(42L, 8L, UserRole.USER))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void rejectsOutputWithUnknownEnumOrInvalidConfidenceAndDoesNotPersist() {
        when(ticketService.getTicketById(42L)).thenReturn(ticket());
        when(providerProvider.getIfAvailable()).thenReturn(provider);
        when(provider.generate(any())).thenReturn(validJson().replace("\"VPN\"", "\"UNSUPPORTED\"")
                .replace("0.75", "1.5"));

        assertThatThrownBy(() -> analysisService.analyze(42L, 12L, UserRole.ADMIN))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void mapsGeminiRateLimitErrorsWithoutLeakingProviderDetails() {
        when(ticketService.getTicketById(42L)).thenReturn(ticket());
        when(providerProvider.getIfAvailable()).thenReturn(provider);
        when(provider.generate(any())).thenThrow(new GeminiProviderException(429));

        assertThatThrownBy(() -> analysisService.analyze(42L, 12L, UserRole.TECHNICIAN))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(persistenceService, never()).persist(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void mapsGeminiTimeoutsToGatewayTimeout() {
        when(ticketService.getTicketById(42L)).thenReturn(ticket());
        when(providerProvider.getIfAvailable()).thenReturn(provider);
        when(provider.generate(any())).thenThrow(new GeminiProviderException(504));

        assertThatThrownBy(() -> analysisService.analyze(42L, 12L, UserRole.TECHNICIAN))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
    }

    @Test
    void returnsPreviouslySavedAnalysesForAuthorizedTicket() {
        Ticket ticket = ticket();
        when(ticketService.getTicketByIdForCreator(42L, 8L)).thenReturn(ticket);
        com.assistia.entity.AIAnalysis stored = persistedAnalysis(ticket);
        when(persistenceService.history(42L)).thenReturn(List.of(stored));

        List<TicketAnalysisResponse> history = analysisService.getHistory(42L, 8L, UserRole.USER);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).ticketId()).isEqualTo(42L);
        assertThat(history.get(0).suggestedCategory()).isEqualTo(TicketCategory.VPN);
    }

    private Ticket ticket() {
        User creator = new User("owner@assistia.local", "Owner", "Test", UserRole.USER, "IT");
        Ticket ticket = new Ticket("VPN indisponible", "La connexion a cessé après une mise à jour.",
                TicketStatus.OPEN, TicketPriority.MEDIUM, TicketCategory.VPN, creator, null);
        return ticket;
    }

    private com.assistia.entity.AIAnalysis persistedAnalysis(Ticket ticket) {
        com.assistia.entity.AIAnalysis analysis = mock(com.assistia.entity.AIAnalysis.class);
        Ticket ticketReference = mock(Ticket.class);
        when(ticketReference.getId()).thenReturn(42L);
        when(analysis.getId()).thenReturn(5L);
        when(analysis.getTicket()).thenReturn(ticketReference);
        when(analysis.getSummary()).thenReturn("VPN indisponible après une mise à jour.");
        when(analysis.getSuggestedCategory()).thenReturn(TicketCategory.VPN);
        when(analysis.getSuggestedPriority()).thenReturn(TicketPriority.HIGH);
        when(analysis.getPossibleCauses()).thenReturn("[\"Résolution DNS défaillante\"]");
        when(analysis.getProposedActions()).thenReturn("[\"Tester la résolution DNS\"]");
        when(analysis.getConfidenceScore()).thenReturn(new BigDecimal("0.7500"));
        when(analysis.getModelUsed()).thenReturn("gemini-3.8-flash");
        return analysis;
    }

    private String validJson() {
        return """
                {"summary":"VPN indisponible après une mise à jour.","suggestedCategory":"VPN",
                 "suggestedPriority":"HIGH","possibleCauses":["Résolution DNS défaillante"],
                 "suggestedActions":["Tester la résolution DNS"],"confidence":0.75}
                """;
    }
}
