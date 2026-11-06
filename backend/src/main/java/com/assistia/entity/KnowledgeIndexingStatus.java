package com.assistia.entity;

public enum KnowledgeIndexingStatus {
    PENDING,
    PROCESSING,
    AWAITING_EMBEDDINGS,
    INDEXED,
    FAILED,
    OUTDATED
}
