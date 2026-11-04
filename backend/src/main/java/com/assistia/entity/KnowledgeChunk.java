package com.assistia.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

/** Relational metadata only. VECTOR is intentionally accessed outside Hibernate. */
@Entity
@Table(name = "knowledge_chunks", indexes = {
        @Index(name = "idx_kc_document_version", columnList = "document_id, document_version, chunk_position")
})
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_knowledge_chunks_document"))
    private KnowledgeDocument document;

    @Column(name = "document_version", nullable = false)
    private Integer documentVersion;

    @Column(name = "chunk_position", nullable = false)
    private Integer chunkPosition;

    @Lob
    @Nationalized
    @Column(nullable = false)
    private String content;

    @Column(name = "estimated_token_count", nullable = false)
    private Integer estimatedTokenCount;

    @Column(name = "source_page")
    private Integer sourcePage;

    @Column(name = "source_start_offset")
    private Long sourceStartOffset;

    @Column(name = "source_end_offset")
    private Long sourceEndOffset;

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    @Column(name = "embedding_dimension")
    private Integer embeddingDimension;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected KnowledgeChunk() { }

    public KnowledgeChunk(KnowledgeDocument document, Integer documentVersion, Integer chunkPosition,
            String content, Integer estimatedTokenCount) {
        this.document = document;
        this.documentVersion = documentVersion;
        this.chunkPosition = chunkPosition;
        this.content = content;
        this.estimatedTokenCount = estimatedTokenCount;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public KnowledgeDocument getDocument() { return document; }
    public Integer getDocumentVersion() { return documentVersion; }
    public Integer getChunkPosition() { return chunkPosition; }
    public String getContent() { return content; }
    public Integer getEstimatedTokenCount() { return estimatedTokenCount; }
    public Integer getSourcePage() { return sourcePage; }
    public void setSourcePage(Integer sourcePage) { this.sourcePage = sourcePage; }
    public Long getSourceStartOffset() { return sourceStartOffset; }
    public void setSourceStartOffset(Long sourceStartOffset) { this.sourceStartOffset = sourceStartOffset; }
    public Long getSourceEndOffset() { return sourceEndOffset; }
    public void setSourceEndOffset(Long sourceEndOffset) { this.sourceEndOffset = sourceEndOffset; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public Integer getEmbeddingDimension() { return embeddingDimension; }
    public void setEmbeddingDimension(Integer embeddingDimension) { this.embeddingDimension = embeddingDimension; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
