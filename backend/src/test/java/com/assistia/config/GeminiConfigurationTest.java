package com.assistia.config;

import com.assistia.service.GeminiAnalysisProvider;
import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("GEMINI_API_KEY=")
            .withUserConfiguration(GeminiConfiguration.class);

    @Test
    void leavesApplicationUsableWhenGeminiKeyIsAbsent() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(Client.class);
            assertThat(context).doesNotHaveBean(GeminiAnalysisProvider.class);
        });
    }
}
