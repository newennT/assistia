package com.assistia.service;

import com.assistia.dto.TicketAnalysisContent;
import com.assistia.dto.TicketAnalysisResponse;
import com.assistia.entity.AIAnalysis;
import com.assistia.entity.Ticket;
import com.assistia.entity.UserRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

@Service
public class TicketAnalysisService {

    private static final int MAX_DESCRIPTION_LENGTH = 12_000;

    private final TicketService ticketService;
    private final AIAnalysisPersistenceService persistenceService;
    private final ObjectProvider<GeminiAnalysisProvider> providerProvider;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final String model;

    public TicketAnalysisService(
            TicketService ticketService,
            AIAnalysisPersistenceService persistenceService,
            ObjectProvider<GeminiAnalysisProvider> providerProvider,
            ObjectMapper objectMapper,
            Validator validator,
            @Value("${assistia.gemini.model:gemini-3.8-flash}") String model) {
        this.ticketService = ticketService;
        this.persistenceService = persistenceService;
        this.providerProvider = providerProvider;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.model = model;
    }

    public TicketAnalysisResponse analyze(Long ticketId, Long userId, UserRole role) {
        Ticket ticket = authorizedTicket(ticketId, userId, role);
        if (ticket.getDescription().length() > MAX_DESCRIPTION_LENGTH) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "La description est trop longue pour être analysée.");
        }
        GeminiAnalysisProvider provider = providerProvider.getIfAvailable();
        if (provider == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "L'analyse IA n'est pas configurée. Ajoutez GEMINI_API_KEY au backend.");
        }
        if (!StringUtils.hasText(model)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Le modèle Gemini n'est pas configuré.");
        }

        final TicketAnalysisContent content;
        try {
            String response = provider.generate(new AnalysisTicketContext(
                    ticket.getTitle(), ticket.getDescription(), ticket.getCategory()));
            content = objectMapper.readValue(response, TicketAnalysisContent.class);
        } catch (GeminiProviderException exception) {
            throw providerError(exception.getStatusCode());
        } catch (InvalidGeminiResponseException | JsonProcessingException exception) {
            throw invalidResponse();
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Le service Gemini est momentanément indisponible.");
        }

        Set<ConstraintViolation<TicketAnalysisContent>> violations = validator.validate(content);
        if (!violations.isEmpty()) {
            throw invalidResponse();
        }

        try {
            AIAnalysis saved = persistenceService.persist(
                    ticketId,
                    content.summary().trim(),
                    content.suggestedCategory(),
                    content.suggestedPriority(),
                    objectMapper.writeValueAsString(content.possibleCauses()),
                    objectMapper.writeValueAsString(content.suggestedActions()),
                    content.confidence().setScale(4, RoundingMode.HALF_UP),
                    model);
            return response(saved, content.possibleCauses(), content.suggestedActions());
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "L'analyse a été générée mais n'a pas pu être enregistrée.");
        } catch (DataAccessException | EntityNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "L'analyse a été générée mais n'a pas pu être enregistrée.");
        }
    }

    public List<TicketAnalysisResponse> getHistory(Long ticketId, Long userId, UserRole role) {
        authorizedTicket(ticketId, userId, role);
        try {
            return persistenceService.history(ticketId).stream().map(this::response).toList();
        } catch (DataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "L'historique des analyses est momentanément indisponible.");
        }
    }

    private Ticket authorizedTicket(Long ticketId, Long userId, UserRole role) {
        try {
            return role == UserRole.USER
                    ? ticketService.getTicketByIdForCreator(ticketId, userId)
                    : ticketService.getTicketById(ticketId);
        } catch (EntityNotFoundException exception) {
            throw exception;
        } catch (AccessDeniedException exception) {
            throw new EntityNotFoundException("Ticket introuvable : " + ticketId);
        }
    }

    private TicketAnalysisResponse response(AIAnalysis analysis) {
        try {
            List<String> causes = objectMapper.readValue(
                    analysis.getPossibleCauses(), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            List<String> actions = objectMapper.readValue(
                    analysis.getProposedActions(), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            return response(analysis, causes, actions);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Une analyse enregistrée est illisible.");
        }
    }

    private TicketAnalysisResponse response(AIAnalysis analysis, List<String> causes, List<String> actions) {
        return new TicketAnalysisResponse(
                analysis.getId(), analysis.getTicket().getId(), analysis.getSummary(),
                analysis.getSuggestedCategory(), analysis.getSuggestedPriority(), causes, actions,
                analysis.getConfidenceScore(), analysis.getModelUsed(), analysis.getCreatedAt());
    }

    private ResponseStatusException providerError(int statusCode) {
        if (statusCode == 429) {
            return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "La limite temporaire de requêtes Gemini est atteinte. Réessayez plus tard.");
        }
        if (statusCode == 408 || statusCode == 504) {
            return new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "Gemini n'a pas répondu à temps. Réessayez.");
        }
        if (statusCode == 401 || statusCode == 403) {
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La configuration Gemini est invalide ou indisponible.");
        }
        if (statusCode >= 400 && statusCode < 500) {
            return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Gemini n'a pas pu produire une analyse valide.");
        }
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Le service Gemini est momentanément indisponible.");
    }

    private ResponseStatusException invalidResponse() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Gemini a renvoyé une analyse incomplète ou invalide. Aucune analyse n'a été enregistrée.");
    }
}
