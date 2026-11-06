package com.assistia.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_documents", indexes = {
        @Index(name = "idx_kd_category", columnList = "category")
})
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Lob
    @Nationalized
    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private KnowledgeDocumentCategory category;

    @Column(name = "source_reference", length = 255)
    private String sourceReference;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @Column(name = "storage_path", length = 512)
    private String storagePath;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "current_version", nullable = false)
    private Integer currentVersion = 0;

    /** Version currently being prepared; currentVersion stays usable until promotion. */
    @Column(name = "indexing_version", nullable = false)
    private Integer indexingVersion = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "indexing_status", nullable = false, length = 20)
    private KnowledgeIndexingStatus indexingStatus = KnowledgeIndexingStatus.PENDING;

    @Column(name = "imported_at")
    private LocalDateTime importedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imported_by_id", foreignKey = @ForeignKey(name = "fk_kd_imported_by"))
    private User importedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected KnowledgeDocument() {
    }

    public KnowledgeDocument(String title,
            String content,
            KnowledgeDocumentCategory category,
            String sourceReference) {
        this.title = title;
        this.content = content;
        this.category = category;
        this.sourceReference = sourceReference;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public KnowledgeDocumentCategory getCategory() {
        return category;
    }

    public void setCategory(KnowledgeDocumentCategory category) {
        this.category = category;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public void setSourceReference(String sourceReference) {
        this.sourceReference = sourceReference;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public String getContentSha256() { return contentSha256; }
    public void setContentSha256(String contentSha256) { this.contentSha256 = contentSha256; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }
    public Integer getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(Integer currentVersion) { this.currentVersion = currentVersion; }
    public Integer getIndexingVersion() { return indexingVersion; }
    public void setIndexingVersion(Integer indexingVersion) { this.indexingVersion = indexingVersion; }
    public KnowledgeIndexingStatus getIndexingStatus() { return indexingStatus; }
    public void setIndexingStatus(KnowledgeIndexingStatus indexingStatus) { this.indexingStatus = indexingStatus; }
    public LocalDateTime getImportedAt() { return importedAt; }
    public void setImportedAt(LocalDateTime importedAt) { this.importedAt = importedAt; }
    public User getImportedBy() { return importedBy; }
    public void setImportedBy(User importedBy) { this.importedBy = importedBy; }
}
