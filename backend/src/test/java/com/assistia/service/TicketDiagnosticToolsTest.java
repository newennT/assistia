package com.assistia.service;

import com.assistia.entity.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TicketDiagnosticToolsTest {
    private final TicketService ticketService = mock(TicketService.class);
    private final TicketDiagnosticTools tools = new TicketDiagnosticTools(ticketService);

    @Test void rejectsUserAccessToAnotherUsersTicketWithoutRevealingExistence() {
        when(ticketService.getTicketByIdForCreator(99L, 7L)).thenThrow(new EntityNotFoundException());
        assertThat(tools.execute("getTicket", Map.of("ticketId", 99), 7L, UserRole.USER))
                .containsEntry("error", "tool_unavailable");
        verify(ticketService).getTicketByIdForCreator(99L, 7L);
        verify(ticketService, never()).getTicketById(99L);
    }

    @Test void rejectsInvalidIdsAndMalformedArguments() {
        assertThat(tools.execute("getTicket", Map.of("ticketId", -1), 1L, UserRole.ADMIN))
                .containsEntry("error", "invalid_arguments");
        assertThat(tools.execute("getTicket", Map.of("ticketId", "1"), 1L, UserRole.ADMIN))
                .containsEntry("error", "invalid_arguments");
        assertThat(tools.execute("searchTickets", Map.of("query", "vpn", "limit", 6), 1L, UserRole.USER))
                .containsEntry("error", "invalid_arguments");
        verifyNoInteractions(ticketService);
    }

    @Test void searchScopesUsersAndUsesStrictResultCap() {
        when(ticketService.searchDiagnosticTickets("vpn", TicketCategory.VPN, 7L, 5)).thenReturn(java.util.List.of());
        assertThat(tools.execute("searchTickets", Map.of("query", "vpn", "category", "VPN", "limit", 5), 7L, UserRole.USER))
                .containsEntry("tickets", java.util.List.of());
        verify(ticketService).searchDiagnosticTickets("vpn", TicketCategory.VPN, 7L, 5);
    }

    @Test void convertsToolExecutionFailureToGenericDiagnosticError() {
        when(ticketService.searchDiagnosticTickets("vpn", null, null, 5))
                .thenThrow(new IllegalStateException("database detail must not escape"));
        assertThat(tools.execute("searchTickets", Map.of("query", "vpn"), 7L, UserRole.ADMIN))
                .containsEntry("error", "tool_unavailable")
                .doesNotContainKey("exception");
    }
}
