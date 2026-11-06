package com.assistia.dto.knowledge;

import com.assistia.entity.KnowledgeDocument;
import com.assistia.entity.KnowledgeIndexingStatus;

import java.time.LocalDateTime;

public record KnowledgeDocumentResponse(
        Long id,
        String title,
        String category,
        String sourceReference,
        String originalFilename,
        String mimeType,
        Long fileSizeBytes,
        String contentSha256,
        Integer currentVersion,
        Integer indexingVersion,
        KnowledgeIndexingStatus indexingStatus,
        LocalDateTime importedAt,
        LocalDateTime archivedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static KnowledgeDocumentResponse from(KnowledgeDocument document) {
        return new KnowledgeDocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getCategory().name(),
                document.getSourceReference(),
                document.getOriginalFilename(),
                document.getMimeType(),
                document.getFileSizeBytes(),
                document.getContentSha256(),
                document.getCurrentVersion(),
                document.getIndexingVersion(),
                document.getIndexingStatus(),
                document.getImportedAt(),
                document.getArchivedAt(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }
}
