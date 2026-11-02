package com.ssafy.migrationservice.global.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.Map;

/**
 * LLM 호출 (OpenAI Responses API 형식, 기본은 SSAFY GMS). 키가 없으면 enabled()=false.
 * 사용자 토큰을 넘기지 않도록 RestClient를 직접 만든다 (자동 등록되는 Bearer 전달 인터셉터를 붙이지 않음).
 */
@Component
public class LlmClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public LlmClient(@Value("${migration.llm.base-url}") String baseUrl, @Value("${migration.llm.api-key:}") String apiKey,
                     @Value("${migration.llm.model}") String model) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String model() {
        return model;
    }

    /** 응답 텍스트 (output_text를 이어 붙임). 실패하면 RestClientException 등 런타임 예외 */
    public String call(String instructions, String input) {
        JsonNode res = restClient.post().uri("/responses")
                .header("Authorization", "Bearer " + apiKey)
                .body(Map.of("model", model, "instructions", instructions, "input", input))
                .retrieve()
                .body(JsonNode.class);
        StringBuilder text = new StringBuilder();
        for (JsonNode out : res.path("output")) {
            for (JsonNode c : out.path("content")) {
                if ("output_text".equals(c.path("type").asString())) {
                    text.append(c.path("text").asString());
                }
            }
        }
        return text.toString();
    }
}
