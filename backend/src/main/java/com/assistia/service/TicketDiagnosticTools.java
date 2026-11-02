package com.assistia.service;

import com.assistia.entity.Ticket;
import com.assistia.entity.TicketCategory;
import com.assistia.entity.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TicketDiagnosticTools {
    private static final Logger logger = LoggerFactory.getLogger(TicketDiagnosticTools.class);
    private static final int MAX_RESULTS = 5;
    private static final int MAX_QUERY_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 1200;
    private final TicketService ticketService;

    public TicketDiagnosticTools(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    public Map<String, Object> execute(String name, Map<String, Object> args, Long userId, UserRole role) {
        try {
            Map<String, Object> result = switch (name) {
                case "searchTickets" -> search(args, userId, role);
                case "getTicket" -> get(args, userId, role);
                default -> error("unknown_tool", "Unknown tool.");
            };
            logger.info("Gemini tool executed: name={}, outcome={}", name,
                    result.containsKey("error") ? "rejected" : "success");
            return result;
        } catch (RuntimeException exception) {
            logger.warn("Gemini tool failed: name={}, exceptionType={}", name, exception.getClass().getSimpleName());
            return error("tool_unavailable", "The requested ticket information is unavailable.");
        }
    }

    private Map<String, Object> search(Map<String, Object> args, Long userId, UserRole role) {
        if (!args.keySet().stream().allMatch(List.of("query", "category", "limit")::contains)
                || !(args.get("query") instanceof String query) || query.isBlank() || query.length() > MAX_QUERY_LENGTH) {
            return error("invalid_arguments", "Invalid search arguments.");
        }
        TicketCategory category = null;
        if (args.containsKey("category") && args.get("category") != null) {
            if (!(args.get("category") instanceof String value)) return error("invalid_arguments", "Invalid category.");
            try { category = TicketCategory.valueOf(value); }
            catch (IllegalArgumentException exception) { return error("invalid_arguments", "Invalid category."); }
        }
        int limit = MAX_RESULTS;
        if (args.containsKey("limit")) {
            Object value = args.get("limit");
            if (!(value instanceof Number number) || number.doubleValue() != number.intValue()
                    || number.intValue() < 1 || number.intValue() > MAX_RESULTS) {
                return error("invalid_arguments", "Limit must be between 1 and 5.");
            }
            limit = number.intValue();
        }
        Long creatorId = role == UserRole.USER ? userId : null;
        List<Map<String, Object>> tickets = ticketService.searchDiagnosticTickets(query, category, creatorId, limit)
                .stream().map(this::diagnosticRecord).toList();
        return Map.of("tickets", tickets);
    }

    private Map<String, Object> get(Map<String, Object> args, Long userId, UserRole role) {
        if (!args.keySet().equals(java.util.Set.of("ticketId")) || !(args.get("ticketId") instanceof Number number)
                || number.doubleValue() != number.longValue() || number.longValue() <= 0) {
            return error("invalid_arguments", "ticketId must be a positive integer.");
        }
        Ticket ticket = role == UserRole.USER
                ? ticketService.getTicketByIdForCreator(number.longValue(), userId)
                : ticketService.getTicketById(number.longValue());
        return Map.of("ticket", diagnosticRecord(ticket));
    }

    private Map<String, Object> diagnosticRecord(Ticket ticket) {
        String description = ticket.getDescription();
        if (description.length() > MAX_DESCRIPTION_LENGTH) description = description.substring(0, MAX_DESCRIPTION_LENGTH) + "…";
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("ticketId", ticket.getId()); record.put("title", ticket.getTitle());
        record.put("description", description); record.put("category", ticket.getCategory());
        record.put("priority", ticket.getPriority()); record.put("status", ticket.getStatus());
        record.put("createdAt", ticket.getCreatedAt()); record.put("updatedAt", ticket.getUpdatedAt());
        return record;
    }

    private Map<String, Object> error(String code, String message) { return Map.of("error", code, "message", message); }
}
