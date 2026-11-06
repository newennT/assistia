package com.assistia.dto.knowledge;

import com.assistia.entity.KnowledgeChunk;

public record KnowledgeChunkResponse(
        Long id,
        Long documentId,
        Integer documentVersion,
        Integer chunkPosition,
        String content,
        Integer estimatedTokenCount,
        Integer sourcePage,
        Long sourceStartOffset,
        Long sourceEndOffset) {

    public static KnowledgeChunkResponse from(KnowledgeChunk chunk) {
        return new KnowledgeChunkResponse(
                chunk.getId(),
                chunk.getDocument().getId(),
                chunk.getDocumentVersion(),
                chunk.getChunkPosition(),
                chunk.getContent(),
                chunk.getEstimatedTokenCount(),
                chunk.getSourcePage(),
                chunk.getSourceStartOffset(),
                chunk.getSourceEndOffset());
    }
}
