package com.assistia.poc.rag;

import com.google.genai.Client;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;

import java.util.List;

public final class GeminiEmbedder implements AutoCloseable {
    public static final String MODEL = "gemini-embedding-2";
    public static final int DIMENSIONS = 1536;

    private final Client client;

    public GeminiEmbedder(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY must be set in the backend process environment.");
        }
        client = Client.builder().apiKey(apiKey).build();
    }

    public float[] embedDocument(String title, String text) {
        return embed("title: " + title + " | text: " + text);
    }

    public float[] embedQuery(String query) {
        return embed("task: search result | query: " + query);
    }

    private float[] embed(String input) {
        EmbedContentConfig config = EmbedContentConfig.builder()
                .outputDimensionality(DIMENSIONS)
                .build();
        EmbedContentResponse response = client.models.embedContent(MODEL, input, config);
        List<ContentEmbedding> embeddings = response.embeddings()
                .orElseThrow(() -> new IllegalStateException("Gemini returned no embeddings."));
        if (embeddings.size() != 1) {
            throw new IllegalStateException("Expected one embedding, got " + embeddings.size() + ".");
        }
        List<Float> values = embeddings.getFirst().values()
                .orElseThrow(() -> new IllegalStateException("Gemini returned an embedding without values."));
        if (values.size() != DIMENSIONS) {
            throw new IllegalStateException("Expected " + DIMENSIONS + " dimensions, got " + values.size() + ".");
        }

        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Float value = values.get(i);
            if (value == null || !Float.isFinite(value)) {
                throw new IllegalStateException("Gemini returned a non-finite value at vector index " + i + ".");
            }
            vector[i] = value;
        }
        return vector;
    }

    @Override
    public void close() {
        client.close();
    }
}
