package io.mseemann.oteldemo;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Manual LangChain4j configuration.
 *
 * The auto-configuration shipped with the LangChain4j Spring Boot starter is excluded
 * (see application.yml: spring.autoconfigure.exclude) so we can wire the OTel listener
 * directly into the builder instead of relying on property-based bean creation.
 */
@Configuration
public class LangChain4jConfig {

    @Value("${ANTHROPIC_API_KEY}")
    private String apiKey;

    @Bean
    public AnthropicChatModel lc4jChatModel(LangChain4jOtelListener otelListener) {
        return AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName("claude-haiku-4-5-20251001")
                .maxTokens(1024)
                .listeners(List.of(otelListener))
                .build();
    }
}
