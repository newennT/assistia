package com.assistia.service;

public class EmbeddingProviderException extends RuntimeException {
    private final boolean retryable;

    public EmbeddingProviderException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public EmbeddingProviderException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
