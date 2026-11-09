package com.assistia.service;

import com.google.genai.Models;
import com.google.genai.errors.ApiException;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiEmbeddingProviderTest {
    private static final String MODEL = "gemini-embedding-2";

    @Test
    void requests1536DimensionsAndAcceptsOneValidEmbedding() {
        Models models = mock(Models.class);
        EmbedContentResponse validResponse = responseWith(values(0.25f));
        when(models.embedContent(eq(MODEL), anyString(), any(EmbedContentConfig.class)))
                .thenReturn(validResponse);
        GeminiEmbeddingProvider provider = new GeminiEmbeddingProvider(models, MODEL, 1536, ignored -> { });

        float[] vector = provider.embed("Guide réseau", "Texte synthétique du fragment.");

        assertThat(vector).hasSize(1536);
        assertThat(vector).containsOnly(0.25f);
        org.mockito.ArgumentCaptor<String> text = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<EmbedContentConfig> config =
                org.mockito.ArgumentCaptor.forClass(EmbedContentConfig.class);
        verify(models).embedContent(eq(MODEL), text.capture(), config.capture());
        assertThat(text.getValue()).isEqualTo("title: Guide réseau | text: Texte synthétique du fragment.");
        assertThat(config.getValue().outputDimensionality()).contains(1536);
    }

    @Test
    void rejectsMissingOrMultipleEmbeddingsAndWrongDimensions() {
        assertInvalidResponse(Optional.empty());
        assertInvalidResponse(Optional.of(List.of(embedding(values(0.5f)), embedding(values(0.5f)))));
        assertInvalidResponse(Optional.of(List.of(embedding(List.of(0.1f, 0.2f)))));
        ContentEmbedding withoutValues = mock(ContentEmbedding.class);
        when(withoutValues.values()).thenReturn(Optional.empty());
        assertInvalidResponse(Optional.of(List.of(withoutValues)));
    }

    @Test
    void rejectsNullAndNonFiniteValues() {
        List<Float> nullValue = values(1.0f);
        nullValue.set(12, null);
        assertInvalidResponse(Optional.of(List.of(embedding(nullValue))));

        List<Float> nanValue = values(1.0f);
        nanValue.set(20, Float.NaN);
        assertInvalidResponse(Optional.of(List.of(embedding(nanValue))));

        List<Float> infiniteValue = values(1.0f);
        infiniteValue.set(30, Float.POSITIVE_INFINITY);
        assertInvalidResponse(Optional.of(List.of(embedding(infiniteValue))));
    }

    @Test
    void retriesTemporaryApiFailuresWithBoundedAttemptsAndDoesNotRetryPermanentErrors() {
        Models transientModels = mock(Models.class);
        ApiException limited = mock(ApiException.class);
        when(limited.code()).thenReturn(429);
        when(transientModels.embedContent(eq(MODEL), anyString(), any(EmbedContentConfig.class)))
                .thenThrow(limited)
                .thenReturn(responseWith(values(0.2f)));
        AtomicInteger delays = new AtomicInteger();
        GeminiEmbeddingProvider retrying = new GeminiEmbeddingProvider(
                transientModels, MODEL, 1536, ignored -> delays.incrementAndGet());

        assertThat(retrying.embed("Guide", "Texte synthétique")).hasSize(1536);
        assertThat(delays).hasValue(1);

        Models permanentModels = mock(Models.class);
        ApiException unauthorized = mock(ApiException.class);
        when(unauthorized.code()).thenReturn(401);
        when(permanentModels.embedContent(eq(MODEL), anyString(), any(EmbedContentConfig.class)))
                .thenThrow(unauthorized);
        GeminiEmbeddingProvider noRetry = new GeminiEmbeddingProvider(
                permanentModels, MODEL, 1536, ignored -> { });
        assertThatThrownBy(() -> noRetry.embed("Guide", "Texte synthétique"))
                .isInstanceOf(EmbeddingProviderException.class)
                .hasMessage("Gemini rejected the embedding request.");
        verify(permanentModels).embedContent(eq(MODEL), anyString(), any(EmbedContentConfig.class));
    }

    @Test
    void retriesTimeoutAtMostThreeTimes() {
        Models models = mock(Models.class);
        when(models.embedContent(eq(MODEL), anyString(), any(EmbedContentConfig.class)))
                .thenThrow(new RuntimeException(new SocketTimeoutException()))
                .thenThrow(new RuntimeException(new SocketTimeoutException()))
                .thenThrow(new RuntimeException(new SocketTimeoutException()));
        AtomicInteger delays = new AtomicInteger();
        GeminiEmbeddingProvider provider = new GeminiEmbeddingProvider(
                models, MODEL, 1536, ignored -> delays.incrementAndGet());

        assertThatThrownBy(() -> provider.embed("Guide", "Texte synthétique"))
                .isInstanceOf(EmbeddingProviderException.class)
                .hasMessage("Gemini embedding request timed out.");
        assertThat(delays).hasValue(2);
        verify(models, org.mockito.Mockito.times(3))
                .embedContent(eq(MODEL), anyString(), any(EmbedContentConfig.class));
    }

    private static void assertInvalidResponse(Optional<List<ContentEmbedding>> embeddings) {
        Models models = mock(Models.class);
        EmbedContentResponse response = mock(EmbedContentResponse.class);
        when(response.embeddings()).thenReturn(embeddings);
        when(models.embedContent(eq(MODEL), anyString(), any(EmbedContentConfig.class))).thenReturn(response);
        GeminiEmbeddingProvider provider = new GeminiEmbeddingProvider(models, MODEL, 1536, ignored -> { });
        assertThatThrownBy(() -> provider.embed("Guide", "Texte synthétique"))
                .isInstanceOf(EmbeddingProviderException.class)
                .extracting(exception -> ((EmbeddingProviderException) exception).isRetryable())
                .isEqualTo(false);
    }

    private static EmbedContentResponse responseWith(List<Float> values) {
        return response(Optional.of(List.of(embedding(values))));
    }

    private static EmbedContentResponse response(Optional<List<ContentEmbedding>> embeddings) {
        EmbedContentResponse response = mock(EmbedContentResponse.class);
        when(response.embeddings()).thenReturn(embeddings);
        return response;
    }

    private static ContentEmbedding embedding(List<Float> values) {
        ContentEmbedding embedding = mock(ContentEmbedding.class);
        when(embedding.values()).thenReturn(Optional.of(values));
        return embedding;
    }

    private static List<Float> values(float value) {
        return new java.util.ArrayList<>(java.util.Collections.nCopies(1536, value));
    }
}
