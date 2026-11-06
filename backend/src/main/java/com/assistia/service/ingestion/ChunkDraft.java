package com.assistia.service.ingestion;

public record ChunkDraft(
        int position,
        String content,
        int estimatedTokenCount,
        Integer sourcePage,
        Long sourceStartOffset,
        Long sourceEndOffset) {
}
