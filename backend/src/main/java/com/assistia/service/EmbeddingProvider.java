package com.assistia.service;

public interface EmbeddingProvider {
    float[] embed(String documentTitle, String chunkText);
}
