package com.medflow.client;

import com.fasterxml.jackson.databind.*;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class GlmClient {
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;

    public GlmClient(String apiUrl, String apiKey, String model) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
        this.mapper = new ObjectMapper();
    }

    public GlmResponse chat(GlmRequest request) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", request.getMessages());
        body.put("temperature", request.getTemperature());

        if (request.getResponseFormat() != null) {
            body.put("response_format", request.getResponseFormat());
        }

        String jsonBody = mapper.writeValueAsString(body);

        Request httpRequest = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("GLM API error: " + response.code() + " - " + response.body().string());
            }
            String responseBody = response.body().string();
            JsonNode root = mapper.readTree(responseBody);
            String content = root.at("/choices/0/message/content").asText();
            return new GlmResponse(content);
        }
    }

    public static class GlmRequest {
        private List<Map<String, String>> messages;
        private double temperature;
        private Map<String, Object> responseFormat;

        public GlmRequest() {
            this.messages = new ArrayList<>();
            this.temperature = 0.3;
        }

        public GlmRequest system(String content) {
            messages.add(Map.of("role", "system", "content", content));
            return this;
        }

        public GlmRequest user(String content) {
            messages.add(Map.of("role", "user", "content", content));
            return this;
        }

        public GlmRequest assistant(String content) {
            messages.add(Map.of("role", "assistant", "content", content));
            return this;
        }

        public GlmRequest jsonMode() {
            this.responseFormat = Map.of("type", "json_object");
            return this;
        }

        public List<Map<String, String>> getMessages() { return messages; }
        public double getTemperature() { return temperature; }
        public Map<String, Object> getResponseFormat() { return responseFormat; }
    }

    public static class GlmResponse {
        private final String content;

        public GlmResponse(String content) {
            this.content = content;
        }

        public String getContent() { return content; }
    }
}
