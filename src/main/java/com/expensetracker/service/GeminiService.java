package com.expensetracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private static final List<String> ALLOWED_CATEGORIES =
            List.of("Food", "Travel", "Bills", "Shopping", "Entertainment", "Other");
    private static final String DEFAULT_CATEGORY = "Other";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    public GeminiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Calls Gemini to classify a free-text expense description into one of the
     * fixed categories. Any failure (missing key, network error, bad response)
     * falls back to "Other" rather than propagating an error to the caller.
     */
    public String categorize(String description) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API key is not configured, defaulting to '{}'", DEFAULT_CATEGORY);
            return DEFAULT_CATEGORY;
        }

        try {
            String prompt = buildPrompt(description);

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", prompt)))
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String url = apiUrl + "?key=" + apiKey;
            String rawResponse = restTemplate.postForObject(url, entity, String.class);

            String rawCategory = extractTextFromResponse(rawResponse);
            return normalizeCategory(rawCategory);

        } catch (Exception ex) {
            log.error("Gemini categorization failed, defaulting to '{}': {}", DEFAULT_CATEGORY, ex.getMessage());
            return DEFAULT_CATEGORY;
        }
    }

    private String buildPrompt(String description) {
        return "Classify the following expense description into exactly one of these categories: "
                + String.join(", ", ALLOWED_CATEGORIES)
                + ". Respond with only the category name, nothing else. Description: \"" + description + "\"";
    }

    private String extractTextFromResponse(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);
        return root.path("candidates").path(0)
                .path("content").path("parts").path(0)
                .path("text").asText("").trim();
    }

    private String normalizeCategory(String rawCategory) {
        for (String category : ALLOWED_CATEGORIES) {
            if (category.equalsIgnoreCase(rawCategory.trim())) {
                return category;
            }
        }
        for (String category : ALLOWED_CATEGORIES) {
            if (rawCategory.toLowerCase().contains(category.toLowerCase())) {
                return category;
            }
        }
        return DEFAULT_CATEGORY;
    }
}
