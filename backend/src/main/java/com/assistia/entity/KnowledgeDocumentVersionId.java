package com.assistia.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class KnowledgeDocumentVersionId implements Serializable {
    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "document_version", nullable = false)
    private Integer documentVersion;

    protected KnowledgeDocumentVersionId() {
    }

    public KnowledgeDocumentVersionId(Long documentId, Integer documentVersion) {
        this.documentId = documentId;
        this.documentVersion = documentVersion;
    }

    public Long getDocumentId() { return documentId; }
    public Integer getDocumentVersion() { return documentVersion; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof KnowledgeDocumentVersionId that)) return false;
        return Objects.equals(documentId, that.documentId)
                && Objects.equals(documentVersion, that.documentVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentId, documentVersion);
    }
}
