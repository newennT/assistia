package com.assistia.service.ingestion;

public record StoredDocument(Long documentId, int version, String storageKey) {
}
