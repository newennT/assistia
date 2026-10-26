package com.assistia.controller;

import com.assistia.dto.AssignTicketRequest;
import com.assistia.dto.ChangeTicketPriorityRequest;
import com.assistia.dto.ChangeTicketStatusRequest;
import com.assistia.dto.CreateTicketRequest;
import com.assistia.dto.TicketResponse;
import com.assistia.dto.TicketAnalysisResponse;
import com.assistia.dto.UpdateTicketRequest;
import com.assistia.entity.TicketPriority;
import com.assistia.entity.TicketStatus;
import com.assistia.security.AssistiaPrincipal;
import com.assistia.service.TicketService;
import com.assistia.service.TicketAnalysisService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final TicketAnalysisService ticketAnalysisService;

    public TicketController(TicketService ticketService, TicketAnalysisService ticketAnalysisService) {
        this.ticketService = ticketService;
        this.ticketAnalysisService = ticketAnalysisService;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(
            @Valid @RequestBody CreateTicketRequest request,
            Authentication authentication) {
        AssistiaPrincipal principal = (AssistiaPrincipal) authentication.getPrincipal();
        TicketStatus status = principal.isRegularUser() || request.status() == null
                ? TicketStatus.OPEN
                : request.status();
        TicketPriority priority = principal.isRegularUser() || request.priority() == null
                ? TicketPriority.MEDIUM
                : request.priority();
        TicketResponse response = TicketResponse.from(ticketService.createTicket(
                request.title(),
                request.description(),
                status,
                priority,
                request.category(),
                principal.id()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<TicketResponse> getTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            Authentication authentication) {
        AssistiaPrincipal principal = (AssistiaPrincipal) authentication.getPrincipal();
        if (principal.isRegularUser()) {
            return ticketService.findByCreator(principal.id(), status, priority).stream()
                    .map(TicketResponse::from)
                    .toList();
        }
        if (status != null && priority != null) {
            return ticketService.findByStatusAndPriority(status, priority).stream()
                    .map(TicketResponse::from)
                    .toList();
        }
        if (status != null) {
            return ticketService.findByStatus(status).stream().map(TicketResponse::from).toList();
        }
        if (priority != null) {
            return ticketService.findByPriority(priority).stream().map(TicketResponse::from).toList();
        }
        return ticketService.getAllTickets().stream().map(TicketResponse::from).toList();
    }

    @GetMapping("/{id}")
    public TicketResponse getTicket(@PathVariable Long id, Authentication authentication) {
        AssistiaPrincipal principal = (AssistiaPrincipal) authentication.getPrincipal();
        return TicketResponse.from(principal.isRegularUser()
                ? ticketService.getTicketByIdForCreator(id, principal.id())
                : ticketService.getTicketById(id));
    }

    @PutMapping("/{id}")
    public TicketResponse updateTicket(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTicketRequest request,
            Authentication authentication) {
        AssistiaPrincipal principal = (AssistiaPrincipal) authentication.getPrincipal();
        return TicketResponse.from(principal.isRegularUser()
                ? ticketService.updateTicketForCreator(
                        id, principal.id(), request.title(), request.description(), request.category())
                : ticketService.updateTicket(id, request.title(), request.description(), request.category()));
    }

    @PatchMapping("/{id}/status")
    public TicketResponse changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody ChangeTicketStatusRequest request) {
        return TicketResponse.from(ticketService.changeStatus(id, request.status()));
    }

    @PatchMapping("/{id}/priority")
    public TicketResponse changePriority(
            @PathVariable Long id,
            @Valid @RequestBody ChangeTicketPriorityRequest request) {
        return TicketResponse.from(ticketService.changePriority(id, request.priority()));
    }

    @PatchMapping("/{id}/assignment")
    public TicketResponse assignTechnician(
            @PathVariable Long id,
            @Valid @RequestBody AssignTicketRequest request) {
        return TicketResponse.from(ticketService.assignTechnician(id, request.technicianId()));
    }

    @PostMapping("/{id}/analysis")
    public TicketAnalysisResponse analyzeTicket(@PathVariable Long id, Authentication authentication) {
        AssistiaPrincipal principal = (AssistiaPrincipal) authentication.getPrincipal();
        return ticketAnalysisService.analyze(id, principal.id(), principal.role());
    }

    @GetMapping("/{id}/analysis")
    public List<TicketAnalysisResponse> getTicketAnalyses(@PathVariable Long id, Authentication authentication) {
        AssistiaPrincipal principal = (AssistiaPrincipal) authentication.getPrincipal();
        return ticketAnalysisService.getHistory(id, principal.id(), principal.role());
    }
}
