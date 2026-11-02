package com.assistia.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Models;
import com.google.genai.types.*;
import com.assistia.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GeminiToolCallingTest {
    private Models models;
    private TicketDiagnosticTools tools;
    private GeminiStructuredOutputProvider provider;

    @BeforeEach void setUp() {
        models = mock(Models.class);
        tools = mock(TicketDiagnosticTools.class);
        provider = new GeminiStructuredOutputProvider(models, new ObjectMapper(), "test-model", tools);
    }

    @Test void acceptsDirectStructuredResponseWithoutTools() {
        when(models.generateContent(anyString(), anyList(), any()))
                .thenReturn(responseText("{\"summary\":\"ok\"}"));
        assertThat(provider.generate(context(), 3L, UserRole.USER)).contains("summary");
        verifyNoInteractions(tools);
    }

    @Test void executesSearchAndReturnsToolResultForFinalTurn() {
        when(models.generateContent(anyString(), anyList(), any()))
                .thenReturn(responseCalls(call("searchTickets", Map.of("query", "vpn"))))
                .thenReturn(responseText("{\"summary\":\"similar ticket found\"}"));
        when(tools.execute(eq("searchTickets"), anyMap(), eq(3L), eq(UserRole.USER))).thenReturn(Map.of("tickets", List.of()));
        assertThat(provider.generate(context(), 3L, UserRole.USER)).contains("similar ticket");
        verify(tools).execute(eq("searchTickets"), anyMap(), eq(3L), eq(UserRole.USER));
        verify(models, times(2)).generateContent(anyString(), anyList(), any());
    }

    @Test void rejectsInvalidFinalResponseAfterTheMaximumToolTurns() {
        when(models.generateContent(anyString(), anyList(), any()))
                .thenReturn(responseCalls(call("searchTickets", Map.of("query", "vpn"))))
                .thenReturn(responseCalls(call("getTicket", Map.of("ticketId", 12))))
                .thenReturn(responseCalls(call("getTicket", Map.of("ticketId", 13))));
        when(tools.execute(anyString(), anyMap(), anyLong(), any())).thenReturn(Map.of("tickets", List.of()));
        assertThatThrownBy(() -> provider.generate(context(), 3L, UserRole.USER))
                .isInstanceOf(InvalidGeminiResponseException.class);
        verify(models, times(3)).generateContent(anyString(), anyList(), any());
    }

    @Test void rejectsMissingFinalAnswerAfterTools() {
        when(models.generateContent(anyString(), anyList(), any()))
                .thenReturn(responseCalls(call("searchTickets", Map.of("query", "vpn"))))
                .thenReturn(responseText(" "));
        when(tools.execute(anyString(), anyMap(), anyLong(), any())).thenReturn(Map.of("tickets", List.of()));
        assertThatThrownBy(() -> provider.generate(context(), 3L, UserRole.USER))
                .isInstanceOf(InvalidGeminiResponseException.class);
    }

    @Test void capsTotalCallsAndDoesNotExecuteRepeatedArguments() {
        when(models.generateContent(anyString(), anyList(), any()))
                .thenReturn(responseCalls(call("searchTickets", Map.of("query", "vpn")), call("searchTickets", Map.of("query", "wifi"))))
                .thenReturn(responseCalls(call("getTicket", Map.of("ticketId", 12)), call("getTicket", Map.of("ticketId", 13))))
                .thenReturn(responseText("{\"summary\":\"done\"}"));
        when(tools.execute(anyString(), anyMap(), anyLong(), any())).thenReturn(Map.of("tickets", List.of()));
        assertThat(provider.generate(context(), 3L, UserRole.USER)).contains("done");
        verify(tools, times(3)).execute(anyString(), anyMap(), anyLong(), any());
        verify(models, times(3)).generateContent(anyString(), anyList(), any());
    }

    @Test void ignoresAnIdenticalRepeatedCall() {
        when(models.generateContent(anyString(), anyList(), any()))
                .thenReturn(responseCalls(call("searchTickets", Map.of("query", "vpn")), call("searchTickets", Map.of("query", "vpn"))))
                .thenReturn(responseText("{\"summary\":\"done\"}"));
        when(tools.execute(anyString(), anyMap(), anyLong(), any())).thenReturn(Map.of("tickets", List.of()));
        assertThat(provider.generate(context(), 3L, UserRole.USER)).contains("done");
        verify(tools, times(1)).execute(anyString(), anyMap(), anyLong(), any());
    }

    private AnalysisTicketContext context() { return new AnalysisTicketContext("VPN", "Disconnected", com.assistia.entity.TicketCategory.VPN); }
    private FunctionCall call(String name, Map<String, Object> args) { return FunctionCall.builder().name(name).args(args).build(); }
    private GenerateContentResponse responseText(String text) {
        return GenerateContentResponse.builder().candidates(Candidate.builder().content(Content.fromParts(Part.fromText(text))).build()).build();
    }
    private GenerateContentResponse responseCalls(FunctionCall... calls) {
        Part[] parts = java.util.Arrays.stream(calls).map(call -> Part.fromFunctionCall(call.name().orElseThrow(), call.args().orElseThrow())).toArray(Part[]::new);
        return GenerateContentResponse.builder().candidates(Candidate.builder().content(Content.builder().role("model").parts(parts).build()).build()).build();
    }
}
