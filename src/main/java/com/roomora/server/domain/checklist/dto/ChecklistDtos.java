package com.roomora.server.domain.checklist.dto;

import com.roomora.server.domain.checklist.AiChecklist;
import com.roomora.server.domain.checklist.AiChecklistItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ChecklistDtos {

    private ChecklistDtos() {
    }

    public record ChecklistItemCreateRequest(
            @Min(1) int stepOrder,
            @NotBlank @Size(max = 30) String category,
            @NotBlank @Size(max = 300) String content,
            @Min(0) Integer estimatedCost,
            @Size(max = 300) String memo
    ) {
    }

    public record ChecklistCreateRequest(
            @NotBlank @Size(max = 150) String title,
            @Size(max = 300) String querySummary,
            @Min(0) Integer targetBudget,
            @Size(max = 30) String housingType,
            Boolean isNoDrill,
            @Valid List<ChecklistItemCreateRequest> items
    ) {
    }

    public record ChecklistItemUpdateRequest(
            Boolean isChecked,
            @Size(max = 300) String memo,
            @Size(max = 300) String content,
            @Min(0) Integer estimatedCost
    ) {
    }

    public record ChecklistItemResponse(
            Long id,
            int stepOrder,
            String category,
            String content,
            int estimatedCost,
            boolean isChecked,
            String memo
    ) {
        public static ChecklistItemResponse from(AiChecklistItem item) {
            return new ChecklistItemResponse(
                    item.getId(),
                    item.getStepOrder(),
                    item.getCategory(),
                    item.getContent(),
                    item.getEstimatedCost(),
                    item.isChecked(),
                    item.getMemo()
            );
        }
    }

    public record ChecklistSummaryResponse(
            Long id,
            String title,
            String querySummary,
            Integer targetBudget,
            String housingType,
            Boolean isNoDrill,
            int completedCount,
            int totalCount,
            int progressPercent,
            Instant createdAt
    ) {
        public static ChecklistSummaryResponse from(AiChecklist checklist) {
            int total = checklist.getItems().size();
            int completed = (int) checklist.getItems().stream().filter(AiChecklistItem::isChecked).count();
            int progress = total == 0 ? 0 : (int) Math.round((completed * 100.0) / total);
            return new ChecklistSummaryResponse(
                    checklist.getId(),
                    checklist.getTitle(),
                    checklist.getQuerySummary(),
                    checklist.getTargetBudget(),
                    checklist.getHousingType(),
                    checklist.getIsNoDrill(),
                    completed,
                    total,
                    progress,
                    checklist.getCreatedAt()
            );
        }
    }

    public record ChecklistDetailResponse(
            Long id,
            String title,
            String querySummary,
            Integer targetBudget,
            String housingType,
            Boolean isNoDrill,
            int completedCount,
            int totalCount,
            int progressPercent,
            List<ChecklistItemResponse> items,
            Instant createdAt
    ) {
        public static ChecklistDetailResponse from(AiChecklist checklist) {
            int total = checklist.getItems().size();
            int completed = (int) checklist.getItems().stream().filter(AiChecklistItem::isChecked).count();
            int progress = total == 0 ? 0 : (int) Math.round((completed * 100.0) / total);
            return new ChecklistDetailResponse(
                    checklist.getId(),
                    checklist.getTitle(),
                    checklist.getQuerySummary(),
                    checklist.getTargetBudget(),
                    checklist.getHousingType(),
                    checklist.getIsNoDrill(),
                    completed,
                    total,
                    progress,
                    checklist.getItems().stream().map(ChecklistItemResponse::from).toList(),
                    checklist.getCreatedAt()
            );
        }
    }
}
