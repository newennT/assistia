package com.assistia.service;

public class InvalidGeminiResponseException extends RuntimeException {
    public InvalidGeminiResponseException() {
        super("Gemini returned an invalid structured response.");
    }
}
