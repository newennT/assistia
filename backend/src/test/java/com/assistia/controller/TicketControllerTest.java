package com.assistia.controller;

import com.assistia.entity.Ticket;
import com.assistia.entity.TicketCategory;
import com.assistia.entity.TicketPriority;
import com.assistia.entity.TicketStatus;
import com.assistia.entity.User;
import com.assistia.entity.UserRole;
import com.assistia.dto.TicketAnalysisResponse;
import com.assistia.security.AssistiaPrincipal;
import com.assistia.service.TicketService;
import com.assistia.service.TicketAnalysisService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService ticketService;

    @MockitoBean
    private TicketAnalysisService ticketAnalysisService;

    private MockHttpServletRequestBuilder asTechnician(MockHttpServletRequestBuilder request) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(12L);
        when(user.getEmail()).thenReturn("tech@assistia.local");
        when(user.getPasswordHash()).thenReturn("unused-in-controller-test");
        when(user.getRole()).thenReturn(UserRole.TECHNICIAN);
        AssistiaPrincipal principal = new AssistiaPrincipal(user);
        return request.principal(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
    }

    @Test
    void createsTicketAndReturnsDtoWithCreatedLocation() throws Exception {
        Ticket created = ticket(TicketStatus.OPEN, TicketPriority.HIGH, null);
        when(ticketService.createTicket(
                "VPN indisponible",
                "La connexion échoue.",
                TicketStatus.OPEN,
                TicketPriority.HIGH,
                TicketCategory.VPN,
                12L)).thenReturn(created);

        mockMvc.perform(asTechnician(post("/api/tickets")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "VPN indisponible",
                                  "description": "La connexion échoue.",
                                  "status": "OPEN",
                                  "priority": "HIGH",
                                  "category": "VPN",
                                  "creatorId": 12
                                }
                                """)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/tickets/42"))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.creatorId").value(12))
                .andExpect(jsonPath("$.assigneeId").doesNotExist())
                .andExpect(jsonPath("$.createdAt").value("2026-10-19T12:00:00"))
                .andExpect(jsonPath("$.creator").doesNotExist());
    }

    @Test
    void retrievesTicketAsDto() throws Exception {
        Ticket retrieved = ticket(TicketStatus.OPEN, TicketPriority.HIGH, null);
        when(ticketService.getTicketById(42L)).thenReturn(retrieved);

        mockMvc.perform(asTechnician(get("/api/tickets/42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.title").value("Ticket de test"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.category").value("VPN"))
                .andExpect(jsonPath("$.creatorId").value(12));
    }

    @Test
    void returnsConsistentNotFoundErrorForUnknownTicket() throws Exception {
        when(ticketService.getTicketById(404L)).thenThrow(new EntityNotFoundException("Ticket introuvable : 404"));

        mockMvc.perform(asTechnician(get("/api/tickets/404")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Ticket introuvable : 404"))
                .andExpect(jsonPath("$.path").value("/api/tickets/404"));
    }

    @Test
    void rejectsInvalidRequestAndUnknownEnum() throws Exception {
        mockMvc.perform(asTechnician(post("/api/tickets")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "title": " ",
                                  "description": "Description",
                                  "status": "OPEN",
                                  "priority": "HIGH",
                                  "category": "VPN",
                                  "creatorId": 12
                                }
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/tickets"));

        mockMvc.perform(asTechnician(post("/api/tickets")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Ticket",
                                  "description": "Description",
                                  "status": "UNKNOWN",
                                  "priority": "HIGH",
                                  "category": "VPN",
                                  "creatorId": 12
                                }
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "La requête contient des données invalides ou une valeur d'enum inconnue."));
    }

    @Test
    void changesStatus() throws Exception {
        Ticket updated = ticket(TicketStatus.IN_PROGRESS, TicketPriority.HIGH, null);
        when(ticketService.changeStatus(42L, TicketStatus.IN_PROGRESS))
                .thenReturn(updated);

        mockMvc.perform(asTechnician(patch("/api/tickets/42/status")
                        .contentType(APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void changesPriority() throws Exception {
        Ticket updated = ticket(TicketStatus.OPEN, TicketPriority.CRITICAL, null);
        when(ticketService.changePriority(42L, TicketPriority.CRITICAL))
                .thenReturn(updated);

        mockMvc.perform(asTechnician(patch("/api/tickets/42/priority")
                        .contentType(APPLICATION_JSON)
                        .content("{\"priority\":\"CRITICAL\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("CRITICAL"));
    }

    @Test
    void assignsTechnician() throws Exception {
        User technician = mock(User.class);
        when(technician.getId()).thenReturn(55L);
        Ticket updated = ticket(TicketStatus.OPEN, TicketPriority.HIGH, technician);
        when(ticketService.assignTechnician(42L, 55L))
                .thenReturn(updated);

        mockMvc.perform(asTechnician(patch("/api/tickets/42/assignment")
                        .contentType(APPLICATION_JSON)
                        .content("{\"technicianId\":55}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(55));
    }

    @Test
    void createsAndListsTicketAnalysesForAuthenticatedPrincipal() throws Exception {
        TicketAnalysisResponse analysis = new TicketAnalysisResponse(
                3L, 42L, "Analyse factuelle", TicketCategory.VPN, TicketPriority.HIGH,
                List.of("DNS"), List.of("Tester le DNS"), java.math.BigDecimal.valueOf(0.75),
                "gemini-3.8-flash", LocalDateTime.of(2026, 10, 26, 12, 0));
        when(ticketAnalysisService.analyze(42L, 12L, UserRole.TECHNICIAN)).thenReturn(analysis);
        when(ticketAnalysisService.getHistory(42L, 12L, UserRole.TECHNICIAN)).thenReturn(List.of(analysis));

        mockMvc.perform(asTechnician(post("/api/tickets/42/analysis")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.suggestedCategory").value("VPN"))
                .andExpect(jsonPath("$.modelUsed").value("gemini-3.8-flash"));
        mockMvc.perform(asTechnician(get("/api/tickets/42/analysis")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticketId").value(42));
    }

    @Test
    void filtersTicketListByStatusAndPriority() throws Exception {
        Ticket filtered = ticket(TicketStatus.OPEN, TicketPriority.HIGH, null);
        when(ticketService.findByStatusAndPriority(TicketStatus.OPEN, TicketPriority.HIGH))
                .thenReturn(List.of(filtered));

        mockMvc.perform(asTechnician(
                get("/api/tickets").param("status", "OPEN").param("priority", "HIGH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(42));
    }

    private Ticket ticket(TicketStatus status, TicketPriority priority, User assignee) {
        User creator = mock(User.class);
        when(creator.getId()).thenReturn(12L);

        Ticket ticket = mock(Ticket.class);
        when(ticket.getId()).thenReturn(42L);
        when(ticket.getTitle()).thenReturn("Ticket de test");
        when(ticket.getDescription()).thenReturn("Description du ticket.");
        when(ticket.getStatus()).thenReturn(status);
        when(ticket.getPriority()).thenReturn(priority);
        when(ticket.getCategory()).thenReturn(TicketCategory.VPN);
        when(ticket.getCreator()).thenReturn(creator);
        when(ticket.getAssignee()).thenReturn(assignee);
        when(ticket.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 10, 19, 12, 0));
        when(ticket.getUpdatedAt()).thenReturn(LocalDateTime.of(2026, 10, 19, 12, 30));
        return ticket;
    }
}
