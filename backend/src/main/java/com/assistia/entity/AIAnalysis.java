package com.assistia.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_analyses", indexes = {
        @Index(name = "idx_ai_analyses_ticket", columnList = "ticket_id")
})
public class AIAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ai_analyses_ticket"))
    private Ticket ticket;

    @Lob
    @Nationalized
    @Column(nullable = false)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_category", length = 40)
    private TicketCategory suggestedCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_priority", length = 30)
    private TicketPriority suggestedPriority;

    @Lob
    @Nationalized
    @Column(name = "possible_causes")
    private String possibleCauses;

    @Lob
    @Nationalized
    @Column(name = "proposed_actions")
    private String proposedActions;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "model_used", nullable = false, length = 100)
    private String modelUsed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AIAnalysis() {
    }

    public AIAnalysis(Ticket ticket,
            String summary,
            TicketCategory suggestedCategory,
            TicketPriority suggestedPriority,
            String possibleCauses,
            String proposedActions,
            BigDecimal confidenceScore,
            String modelUsed) {
        this.ticket = ticket;
        this.summary = summary;
        this.suggestedCategory = suggestedCategory;
        this.suggestedPriority = suggestedPriority;
        this.possibleCauses = possibleCauses;
        this.proposedActions = proposedActions;
        this.confidenceScore = confidenceScore;
        this.modelUsed = modelUsed;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public void setTicket(Ticket ticket) {
        this.ticket = ticket;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public TicketCategory getSuggestedCategory() {
        return suggestedCategory;
    }

    public void setSuggestedCategory(TicketCategory suggestedCategory) {
        this.suggestedCategory = suggestedCategory;
    }

    public TicketPriority getSuggestedPriority() {
        return suggestedPriority;
    }

    public void setSuggestedPriority(TicketPriority suggestedPriority) {
        this.suggestedPriority = suggestedPriority;
    }

    public String getPossibleCauses() {
        return possibleCauses;
    }

    public void setPossibleCauses(String possibleCauses) {
        this.possibleCauses = possibleCauses;
    }

    public String getProposedActions() {
        return proposedActions;
    }

    public void setProposedActions(String proposedActions) {
        this.proposedActions = proposedActions;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
