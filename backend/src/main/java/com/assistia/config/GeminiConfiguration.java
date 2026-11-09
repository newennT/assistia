package com.assistia.config;

import com.assistia.service.GeminiAnalysisProvider;
import com.assistia.service.EmbeddingProvider;
import com.assistia.service.GeminiEmbeddingProvider;
import com.assistia.service.GeminiStructuredOutputProvider;
import com.assistia.service.TicketDiagnosticTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;

@Configuration
public class GeminiConfiguration {

    @Bean(destroyMethod = "close")
    @Conditional(GeminiApiKeyCondition.class)
    Client geminiClient(Environment environment, @Value("${assistia.gemini.timeout-ms:30000}") int timeoutMillis) {
        return Client.builder()
                .apiKey(environment.getProperty("GEMINI_API_KEY"))
                .httpOptions(HttpOptions.builder().apiVersion("v1beta").timeout(timeoutMillis).build())
                .build();
    }

    @Bean
    @Conditional(GeminiApiKeyCondition.class)
    GeminiAnalysisProvider geminiAnalysisProvider(
            Client geminiClient,
            ObjectMapper objectMapper,
            TicketDiagnosticTools diagnosticTools,
            @Value("${assistia.gemini.model:gemini-3.8-flash}") String model) {
        return new GeminiStructuredOutputProvider(geminiClient.models, objectMapper, model, diagnosticTools);
    }

    @Bean
    @Conditional(GeminiApiKeyCondition.class)
    @ConditionalOnProperty(name = "assistia.rag.enabled", havingValue = "true")
    EmbeddingProvider geminiEmbeddingProvider(
            Client geminiClient,
            @Value("${assistia.embedding.model:gemini-embedding-2}") String model,
            @Value("${assistia.embedding.output-dimensions:1536}") int outputDimensions) {
        return new GeminiEmbeddingProvider(geminiClient.models, model, outputDimensions);
    }
}
