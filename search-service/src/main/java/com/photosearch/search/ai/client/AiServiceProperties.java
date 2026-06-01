package com.photosearch.search.ai.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter @Setter @Component
@ConfigurationProperties(prefix = "app.ai-service")
public class AiServiceProperties {
    private String baseUrl = "http://localhost:8000";
    private String internalApiKey = "dev-ai-service-key";
}
