package com.assistia.entity;

public enum KnowledgeIndexingStatus {
    PENDING,
    PROCESSING,
    AWAITING_EMBEDDINGS,
    READY_TO_PUBLISH,
    INDEXED,
    FAILED,
    OUTDATED
}
