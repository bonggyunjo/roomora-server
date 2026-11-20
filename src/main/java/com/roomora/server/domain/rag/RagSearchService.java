package com.roomora.server.domain.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomora.server.domain.rag.RagDocumentChunkRepository.SearchFilter;
import com.roomora.server.domain.rag.dto.RagDtos.*;
import com.roomora.server.global.exception.BusinessException;
import com.roomora.server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RagSearchService {

    private static final String RATE_LIMIT_PREFIX = "rag:rl:";
    private static final String CACHE_PREFIX = "rag:cache:";

    private final RagQueryParser queryParser;
    private final RagAiGateway aiGateway;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.daily-limit:20}")
    private int dailyLimit;

    @Value("${app.ai.cache-ttl-minutes:30}")
    private long cacheTtlMinutes;

    public RagSearchResponse search(RagSearchRequest request, String callerKey) {
        enforceRateLimit(callerKey);

        SearchFilter filter = queryParser.parseFilter(request.query(), request.filters());
        String cacheKey = buildCacheKey(request.query(), filter);

        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, RagSearchResponse.class);
            } catch (JsonProcessingException ignored) {
            }
        }

        RagSearchResponse response = aiGateway.executeRagSearch(request.query(), filter);
        // Do not cache fallback responses
        if (!response.fallback()) {
            try {
                redisTemplate.opsForValue().set(
                        cacheKey,
                        objectMapper.writeValueAsString(response),
                        Duration.ofMinutes(cacheTtlMinutes)
                );
            } catch (JsonProcessingException ignored) {
            }
        }
        return response;
    }

    private void enforceRateLimit(String callerKey) {
        String key = RATE_LIMIT_PREFIX + callerKey + ":" + LocalDate.now();
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofDays(1));
        }
        if (count != null && count > dailyLimit) {
            throw new BusinessException(ErrorCode.RAG_RATE_LIMIT_EXCEEDED);
        }
    }

    private String buildCacheKey(String query, SearchFilter filter) {
        String raw = query + "|" + filter.housingType() + "|" + filter.isNoDrill()
                + "|" + filter.maxBudget() + "|" + filter.maxPyeong();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return CACHE_PREFIX + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return CACHE_PREFIX + raw.hashCode();
        }
    }
}
