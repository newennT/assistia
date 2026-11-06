package com.assistia.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_document_versions")
public class KnowledgeDocumentVersion {
    @EmbeddedId
    private KnowledgeDocumentVersionId id;

    @MapsId("documentId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_kdv_document"))
    private KnowledgeDocument document;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "content_sha256", nullable = false, length = 64, unique = true)
    private String contentSha256;

    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected KnowledgeDocumentVersion() {
    }

    public KnowledgeDocumentVersion(
            KnowledgeDocument document,
            Integer version,
            String originalFilename,
            String mimeType,
            Long fileSizeBytes,
            String contentSha256,
            String storagePath) {
        this.document = document;
        this.id = new KnowledgeDocumentVersionId(document.getId(), version);
        this.originalFilename = originalFilename;
        this.mimeType = mimeType;
        this.fileSizeBytes = fileSizeBytes;
        this.contentSha256 = contentSha256;
        this.storagePath = storagePath;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public KnowledgeDocumentVersionId getId() { return id; }
    public KnowledgeDocument getDocument() { return document; }
    public String getOriginalFilename() { return originalFilename; }
    public String getMimeType() { return mimeType; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public String getContentSha256() { return contentSha256; }
    public String getStoragePath() { return storagePath; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
}
