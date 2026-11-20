package com.roomora.server.domain.contest;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class ContestRankingRedisRepository {

    private static final String KEY_PREFIX = "contest:ranking:";

    private final StringRedisTemplate redisTemplate;

    public void initializeEntry(Long roundId, Long postId) {
        redisTemplate.opsForZSet().addIfAbsent(key(roundId), String.valueOf(postId), 0.0);
    }

    public void incrementScore(Long roundId, Long postId) {
        redisTemplate.opsForZSet().incrementScore(key(roundId), String.valueOf(postId), 1.0);
    }

    public void removeEntry(Long roundId, Long postId) {
        redisTemplate.opsForZSet().remove(key(roundId), String.valueOf(postId));
    }

    public List<RankedEntry> getTopN(Long roundId, int limit) {
        int end = Math.max(limit - 1, 0);
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key(roundId), 0, end);
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }
        return tuples.stream()
                .filter(t -> t.getValue() != null)
                .map(t -> new RankedEntry(
                        Long.parseLong(t.getValue()),
                        t.getScore() != null ? t.getScore().intValue() : 0
                ))
                .toList();
    }

    public List<RankedEntry> getAllRanked(Long roundId) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key(roundId), 0, -1);
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }
        return tuples.stream()
                .filter(t -> t.getValue() != null)
                .map(t -> new RankedEntry(
                        Long.parseLong(t.getValue()),
                        t.getScore() != null ? t.getScore().intValue() : 0
                ))
                .toList();
    }

    private String key(Long roundId) {
        return KEY_PREFIX + roundId;
    }

    public record RankedEntry(Long postId, int score) {
    }
}
