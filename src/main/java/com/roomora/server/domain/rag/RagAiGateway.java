package com.roomora.server.domain.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomora.server.domain.rag.RagDocumentChunkRepository.RetrievedChunk;
import com.roomora.server.domain.rag.RagDocumentChunkRepository.SearchFilter;
import com.roomora.server.domain.rag.RagLlmClient.GeneratedPlan;
import com.roomora.server.domain.rag.dto.RagDtos.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagAiGateway {

    private static final int TOP_K = 6;

    private final RagEmbeddingClient embeddingClient;
    private final RagLlmClient llmClient;
    private final RagDocumentChunkRepository chunkRepository;
    private final ObjectMapper objectMapper;

    @CircuitBreaker(name = "ragAi", fallbackMethod = "fallbackSearch")
    public RagSearchResponse executeRagSearch(String query, SearchFilter strictFilter) {
        float[] queryVector = embeddingClient.embed(query);

        SearchFilter appliedFilter = strictFilter;
        List<RetrievedChunk> chunks = chunkRepository.hybridSearch(query, queryVector, appliedFilter, TOP_K);
        boolean relaxed = false;

        // Stepwise filter relaxation when strict filter returns no RECIPE results
        if (!hasRecipeResult(chunks) && hasAnyFilter(strictFilter)) {
            relaxed = true;
            // 1) Drop maxPyeong
            appliedFilter = new SearchFilter(strictFilter.housingType(), strictFilter.isNoDrill(), strictFilter.maxBudget(), null);
            chunks = chunkRepository.hybridSearch(query, queryVector, appliedFilter, TOP_K);

            // 2) Widen maxBudget by 30% or drop if still empty
            if (!hasRecipeResult(chunks) && strictFilter.maxBudget() != null) {
                appliedFilter = new SearchFilter(strictFilter.housingType(), strictFilter.isNoDrill(), null, null);
                chunks = chunkRepository.hybridSearch(query, queryVector, appliedFilter, TOP_K);
            }

            // 3) Drop all recipe filters as last resort
            if (!hasRecipeResult(chunks)) {
                appliedFilter = new SearchFilter(null, null, null, null);
                chunks = chunkRepository.hybridSearch(query, queryVector, appliedFilter, TOP_K);
            }
        }

        GeneratedPlan plan = llmClient.generate(query, appliedFilter, chunks);
        List<String> warnings = new ArrayList<>(plan.warnings() != null ? plan.warnings() : List.of());
        if (relaxed) {
            warnings.add(0, "정확히 일치하는 조건의 레시피가 부족하여 일부 조건(평수·예산 등)을 완화해 유사 사례를 함께 검색했습니다.");
        }

        return new RagSearchResponse(
                false,
                relaxed,
                plan.summary(),
                warnings,
                plan.budgetBreakdown(),
                toCitations(chunks),
                plan.checklistDraft()
        );
    }

    public RagSearchResponse fallbackSearch(String query, SearchFilter strictFilter, Throwable ex) {
        log.warn("RAG AI CircuitBreaker fallback triggered for query='{}': {}", query, ex.getMessage());
        List<RetrievedChunk> fallbackChunks = chunkRepository.keywordFallbackSearch(query, strictFilter, TOP_K);
        if (fallbackChunks.isEmpty() && hasAnyFilter(strictFilter)) {
            fallbackChunks = chunkRepository.keywordFallbackSearch(query, new SearchFilter(null, null, null, null), TOP_K);
        }

        return new RagSearchResponse(
                true,
                false,
                "AI 요약 서비스가 일시적으로 지연되어 조건·키워드 기반 검색 결과를 대신 제공합니다.",
                List.of("현재 AI 요약 대신 DB 조건·키워드 검색 결과만 표시되고 있습니다."),
                List.of(),
                toCitations(fallbackChunks),
                List.of()
        );
    }

    private boolean hasRecipeResult(List<RetrievedChunk> chunks) {
        return chunks.stream().anyMatch(c -> c.sourceType() == RagSourceType.RECIPE);
    }

    private boolean hasAnyFilter(SearchFilter filter) {
        return filter != null && (filter.housingType() != null || filter.isNoDrill() != null
                || filter.maxBudget() != null || filter.maxPyeong() != null);
    }

    private List<CitationItem> toCitations(List<RetrievedChunk> chunks) {
        Map<String, CitationItem> deduped = new LinkedHashMap<>();
        for (RetrievedChunk chunk : chunks) {
            String key = chunk.sourceType().name() + "#" + chunk.sourceId();
            if (deduped.containsKey(key)) {
                continue;
            }
            String title = key;
            String thumbnailUrl = null;
            Integer budget = null;
            try {
                JsonNode meta = objectMapper.readTree(chunk.metadataJson());
                if (meta.hasNonNull("title")) {
                    title = meta.get("title").asText();
                }
                if (meta.hasNonNull("thumbnailUrl")) {
                    thumbnailUrl = meta.get("thumbnailUrl").asText();
                }
                if (meta.hasNonNull("budget")) {
                    budget = meta.get("budget").asInt();
                }
            } catch (Exception ignored) {
            }
            deduped.put(key, new CitationItem(
                    chunk.sourceType().name(),
                    chunk.sourceId(),
                    title,
                    thumbnailUrl,
                    budget
            ));
        }
        return new ArrayList<>(deduped.values());
    }
}
