package com.roomora.server.domain.rag.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class RagDtos {

    private RagDtos() {
    }

    public record RagFilterRequest(
            String housingType,
            @Min(0) Integer maxBudget,
            Boolean isNoDrill,
            @Min(1) Integer maxPyeong
    ) {
    }

    public record RagSearchRequest(
            @NotBlank @Size(max = 300) String query,
            RagFilterRequest filters
    ) {
    }

    public record BudgetAllocation(String category, int amount) {
    }

    public record CitationItem(
            String sourceType,
            Long sourceId,
            String title,
            String thumbnailUrl,
            Integer budget
    ) {
    }

    public record ChecklistDraftItem(
            int stepOrder,
            String category,
            String content,
            int estimatedCost
    ) {
    }

    public record RagSearchResponse(
            boolean fallback,
            boolean relaxed,
            String summary,
            List<String> warnings,
            List<BudgetAllocation> budgetBreakdown,
            List<CitationItem> citations,
            List<ChecklistDraftItem> checklistDraft
    ) {
    }

    public record ReindexResponse(int indexedCount) {
    }
}
