package com.roomora.server.domain.rag;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleEmbeddingClient implements RagEmbeddingClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String embeddingModel;

    public OpenAiCompatibleEmbeddingClient(
            @Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.api-key:}") String apiKey,
            @Value("${app.ai.embedding-model}") String embeddingModel,
            @Value("${app.ai.timeout-seconds:8}") int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.apiKey = apiKey;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String text) {
        if (!StringUtils.hasText(apiKey)) {
            return deterministicLocalVector(text);
        }

        Map<String, Object> body = Map.of(
                "model", embeddingModel,
                "input", text
        );

        JsonNode response = restClient.post()
                .uri("/embeddings")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.has("data") || response.get("data").isEmpty()) {
            throw new IllegalStateException("Empty embedding response from AI provider");
        }
        JsonNode vecNode = response.get("data").get(0).get("embedding");
        float[] result = new float[VECTOR_DIMENSION];
        int limit = Math.min(vecNode.size(), VECTOR_DIMENSION);
        for (int i = 0; i < limit; i++) {
            result[i] = (float) vecNode.get(i).asDouble();
        }
        return result;
    }

    /**
     * AI_API_KEY 미설정(로컬 개발/오프라인 테스트) 환경용 결정적 1536차원 단위 벡터 생성기.
     * 토큰 해시 기반으로 유사 단어 포함 시 코사인 유사도가 높아지도록 구성한다.
     */
    static float[] deterministicLocalVector(String text) {
        float[] vec = new float[VECTOR_DIMENSION];
        if (text == null || text.isBlank()) {
            vec[0] = 1.0f;
            return vec;
        }
        String[] tokens = text.toLowerCase().split("[\\s\\[\\]|,.:;!?/()\"']+");
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            int idx = Math.floorMod(sha256Int(token), VECTOR_DIMENSION);
            vec[idx] += 1.0f;
            // Bigram-like character feature for Korean partial matching
            for (int i = 0; i < token.length() - 1; i++) {
                String sub = token.substring(i, i + 2);
                int subIdx = Math.floorMod(sha256Int(sub), VECTOR_DIMENSION);
                vec[subIdx] += 0.5f;
            }
        }
        double norm = 0.0;
        for (float v : vec) {
            norm += v * v;
        }
        if (norm == 0.0) {
            vec[0] = 1.0f;
            return vec;
        }
        float invNorm = (float) (1.0 / Math.sqrt(norm));
        for (int i = 0; i < vec.length; i++) {
            vec[i] *= invNorm;
        }
        return vec;
    }

    private static int sha256Int(String s) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return ((digest[0] & 0xFF) << 24)
                    | ((digest[1] & 0xFF) << 16)
                    | ((digest[2] & 0xFF) << 8)
                    | (digest[3] & 0xFF);
        } catch (Exception e) {
            return s.hashCode();
        }
    }
}
