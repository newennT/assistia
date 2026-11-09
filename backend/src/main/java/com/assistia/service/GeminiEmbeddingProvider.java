package com.assistia.service;

import com.google.genai.Models;
import com.google.genai.errors.ApiException;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class GeminiEmbeddingProvider implements EmbeddingProvider {
    public static final int REQUIRED_DIMENSIONS = 1536;
    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_DELAY_MILLIS = 250;

    private final Models models;
    private final String model;
    private final int dimensions;
    private final Sleeper sleeper;

    public GeminiEmbeddingProvider(Models models, String model, int dimensions) {
        this(models, model, dimensions, Thread::sleep);
    }

    GeminiEmbeddingProvider(Models models, String model, int dimensions, Sleeper sleeper) {
        if (models == null || model == null || model.isBlank() || model.length() > 100) {
            throw new IllegalArgumentException("Embedding provider configuration is invalid.");
        }
        if (dimensions != REQUIRED_DIMENSIONS) {
            throw new IllegalArgumentException("Embedding output dimensions must be 1536.");
        }
        this.models = models;
        this.model = model;
        this.dimensions = dimensions;
        this.sleeper = sleeper;
    }

    @Override
    public float[] embed(String documentTitle, String chunkText) {
        if (documentTitle == null || documentTitle.isBlank() || chunkText == null || chunkText.isBlank()) {
            throw new EmbeddingProviderException("Embedding input is empty.", false);
        }
        String input = "title: " + documentTitle.strip() + " | text: " + chunkText;
        EmbedContentConfig config = EmbedContentConfig.builder()
                .outputDimensionality(dimensions)
                .build();

        for (int attempt = 1; ; attempt++) {
            try {
                return validate(models.embedContent(model, input, config));
            } catch (ApiException exception) {
                boolean retryable = isRetryableStatus(exception.code()) || isTimeout(exception);
                if (!retryable || attempt >= MAX_ATTEMPTS) {
                    throw new EmbeddingProviderException(
                            retryable ? "Gemini embedding request temporarily failed."
                                    : "Gemini rejected the embedding request.",
                            retryable, exception);
                }
                pause(attempt);
            } catch (EmbeddingProviderException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                boolean retryable = isTimeout(exception);
                if (!retryable || attempt >= MAX_ATTEMPTS) {
                    throw new EmbeddingProviderException(
                            retryable ? "Gemini embedding request timed out."
                                    : "Gemini embedding response could not be processed.",
                            retryable, exception);
                }
                pause(attempt);
            }
        }
    }

    private float[] validate(EmbedContentResponse response) {
        if (response == null) {
            throw new EmbeddingProviderException("Gemini returned no embedding response.", false);
        }
        List<ContentEmbedding> embeddings = response.embeddings()
                .orElseThrow(() -> new EmbeddingProviderException("Gemini returned no embeddings.", false));
        if (embeddings.size() != 1 || embeddings.getFirst() == null) {
            throw new EmbeddingProviderException("Gemini returned an unexpected number of embeddings.", false);
        }
        List<Float> values = embeddings.getFirst().values()
                .orElseThrow(() -> new EmbeddingProviderException("Gemini returned an embedding without values.", false));
        if (values.size() != dimensions) {
            throw new EmbeddingProviderException("Gemini returned an embedding with an unexpected dimension.", false);
        }
        float[] vector = new float[dimensions];
        for (int i = 0; i < values.size(); i++) {
            Float value = values.get(i);
            if (value == null || !Float.isFinite(value)) {
                throw new EmbeddingProviderException("Gemini returned an invalid embedding value.", false);
            }
            vector[i] = value;
        }
        return vector;
    }

    private void pause(int attempt) {
        long exponential = BASE_DELAY_MILLIS << (attempt - 1);
        long delay = ThreadLocalRandom.current().nextLong(exponential / 2, exponential + exponential / 2 + 1);
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EmbeddingProviderException("Gemini retry was interrupted.", true, exception);
        }
    }

    private static boolean isRetryableStatus(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private static boolean isTimeout(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException
                    || cause instanceof InterruptedIOException
                    || cause instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long milliseconds) throws InterruptedException;
    }
}
