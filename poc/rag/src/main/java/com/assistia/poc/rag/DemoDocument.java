package com.assistia.poc.rag;

public record DemoDocument(int id, String title, String content) {
    public String embeddingInput() {
        return "title: " + title + " | text: " + content;
    }
}
