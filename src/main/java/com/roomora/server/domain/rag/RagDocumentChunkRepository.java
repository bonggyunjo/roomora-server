package com.roomora.server.domain.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class RagDocumentChunkRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public record RetrievedChunk(
            Long id,
            RagSourceType sourceType,
            Long sourceId,
            int chunkIndex,
            String content,
            String metadataJson,
            double score
    ) {
    }

    public record SearchFilter(
            String housingType,
            Boolean isNoDrill,
            Integer maxBudget,
            Integer maxPyeong
    ) {
    }

    public void upsertChunk(RagSourceType sourceType, Long sourceId, int chunkIndex,
                            String content, Map<String, Object> metadata, float[] embedding) {
        String sql = """
                INSERT INTO rag_document_chunk
                    (source_type, source_id, chunk_index, content, metadata, embedding, updated_at)
                VALUES
                    (:sourceType, :sourceId, :chunkIndex, :content, CAST(:metadata AS jsonb), CAST(:embedding AS vector), NOW())
                ON CONFLICT (source_type, source_id, chunk_index)
                DO UPDATE SET
                    content = EXCLUDED.content,
                    metadata = EXCLUDED.metadata,
                    embedding = EXCLUDED.embedding,
                    updated_at = NOW()
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sourceType", sourceType.name())
                .addValue("sourceId", sourceId)
                .addValue("chunkIndex", chunkIndex)
                .addValue("content", content)
                .addValue("metadata", toJson(metadata))
                .addValue("embedding", toVectorLiteral(embedding));

        jdbcTemplate.update(sql, params);
    }

    public void deleteBySource(RagSourceType sourceType, Long sourceId) {
        String sql = "DELETE FROM rag_document_chunk WHERE source_type = :sourceType AND source_id = :sourceId";
        jdbcTemplate.update(sql, Map.of(
                "sourceType", sourceType.name(),
                "sourceId", sourceId
        ));
    }

    /**
     * Single-DB Hybrid Search:
     * 1) RDB/JSONB Metadata Pre-filtering (GUIDE는 범용 지식 베이스로 포함)
     * 2) Vector Cosine Similarity + pg_trgm 키워드 유사도 RRF 결합
     */
    public List<RetrievedChunk> hybridSearch(String keywordQuery, float[] queryVector,
                                             SearchFilter filter, int limit) {
        StringBuilder where = new StringBuilder("WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("vec", toVectorLiteral(queryVector))
                .addValue("kw", keywordQuery != null ? keywordQuery : "")
                .addValue("limit", limit);

        appendFilterConditions(where, params, filter);

        String sql = """
                WITH filtered AS (
                    SELECT id, source_type, source_id, chunk_index, content, metadata::text AS metadata_json,
                           (1.0 - (embedding <=> CAST(:vec AS vector))) AS vec_score,
                           similarity(content, :kw) AS trgm_score
                    FROM rag_document_chunk
                """ + where + """
                ),
                ranked AS (
                    SELECT *,
                           ROW_NUMBER() OVER (ORDER BY vec_score DESC) AS vec_rank,
                           ROW_NUMBER() OVER (ORDER BY trgm_score DESC) AS kw_rank
                    FROM filtered
                )
                SELECT id, source_type, source_id, chunk_index, content, metadata_json,
                       (1.0 / (60 + vec_rank)) + (1.0 / (60 + kw_rank)) AS rrf_score
                FROM ranked
                ORDER BY rrf_score DESC
                LIMIT :limit
                """;

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new RetrievedChunk(
                rs.getLong("id"),
                RagSourceType.valueOf(rs.getString("source_type")),
                rs.getLong("source_id"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getString("metadata_json"),
                rs.getDouble("rrf_score")
        ));
    }

    /**
     * AI 장애/Circuit Breaker Open 시 호출되는 순수 RDB/pg_trgm 키워드 Fallback 검색.
     */
    public List<RetrievedChunk> keywordFallbackSearch(String keywordQuery, SearchFilter filter, int limit) {
        StringBuilder where = new StringBuilder("WHERE 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("kw", keywordQuery != null ? keywordQuery : "")
                .addValue("likeKw", "%" + (keywordQuery != null ? keywordQuery : "") + "%")
                .addValue("limit", limit);

        appendFilterConditions(where, params, filter);

        String sql = """
                SELECT id, source_type, source_id, chunk_index, content, metadata::text AS metadata_json,
                       similarity(content, :kw) AS score
                FROM rag_document_chunk
                """ + where + """
                AND (:kw = '' OR content ILIKE :likeKw OR content % :kw)
                ORDER BY score DESC, updated_at DESC
                LIMIT :limit
                """;

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new RetrievedChunk(
                rs.getLong("id"),
                RagSourceType.valueOf(rs.getString("source_type")),
                rs.getLong("source_id"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getString("metadata_json"),
                rs.getDouble("score")
        ));
    }

    private void appendFilterConditions(StringBuilder where, MapSqlParameterSource params, SearchFilter filter) {
        if (filter == null) {
            return;
        }
        if (filter.housingType() != null) {
            where.append(" AND (source_type = 'GUIDE' OR metadata->>'housingType' = :housingType) ");
            params.addValue("housingType", filter.housingType());
        }
        if (filter.isNoDrill() != null) {
            where.append(" AND (source_type = 'GUIDE' OR (metadata->>'isNoDrill')::boolean = :isNoDrill) ");
            params.addValue("isNoDrill", filter.isNoDrill());
        }
        if (filter.maxBudget() != null) {
            where.append(" AND (source_type = 'GUIDE' OR (metadata->>'budget')::int <= :maxBudget) ");
            params.addValue("maxBudget", filter.maxBudget());
        }
        if (filter.maxPyeong() != null) {
            where.append(" AND (source_type = 'GUIDE' OR (metadata->>'areaPyeong')::int <= :maxPyeong) ");
            params.addValue("maxPyeong", filter.maxPyeong());
        }
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata != null ? metadata : Map.of());
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
