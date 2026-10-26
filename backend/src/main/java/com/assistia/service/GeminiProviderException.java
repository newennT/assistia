package com.assistia.service;

public class GeminiProviderException extends RuntimeException {
    private final int statusCode;

    public GeminiProviderException(int statusCode) {
        super("Gemini request failed.");
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
