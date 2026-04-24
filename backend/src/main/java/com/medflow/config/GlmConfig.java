package com.medflow.config;

import com.medflow.client.GlmClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class GlmConfig {

    @Value("${glm.api.url}")
    private String apiUrl;

    @Value("${glm.api.key}")
    private String apiKey;

    @Value("${glm.api.model}")
    private String model;

    @Bean
    public GlmClient glmClient() {
        return new GlmClient(apiUrl, apiKey, model);
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}
