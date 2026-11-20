package com.roomora.server.domain.recipe.dto;

import com.roomora.server.domain.recipe.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public final class RecipeDtos {

    private RecipeDtos() {
    }

    public record RecipeItemRequest(
            @NotBlank @Size(max = 100) String itemName,
            @NotBlank @Size(max = 30) String category,
            @Min(0) int price,
            @Size(max = 300) String purchaseNote
    ) {
    }

    public record RecipeCreateRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank String content,
            @NotNull HousingType housingType,
            @NotNull @Min(1) @Max(200) Short areaPyeong,
            @NotNull ContractType contractType,
            boolean isNoDrill,
            @Min(0) int budget,
            @NotNull StyleTag styleTag,
            @Size(max = 500) String thumbnailUrl,
            List<@NotBlank @Size(max = 500) String> images,
            @Valid List<RecipeItemRequest> items
    ) {
    }

    public record RecipeUpdateRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank String content,
            @NotNull HousingType housingType,
            @NotNull @Min(1) @Max(200) Short areaPyeong,
            @NotNull ContractType contractType,
            boolean isNoDrill,
            @Min(0) int budget,
            @NotNull StyleTag styleTag,
            @Size(max = 500) String thumbnailUrl,
            List<@NotBlank @Size(max = 500) String> images,
            @Valid List<RecipeItemRequest> items
    ) {
    }

    public record RecipeSearchCondition(
            HousingType housingType,
            Boolean isNoDrill,
            Integer maxBudget,
            Short minPyeong,
            Short maxPyeong,
            StyleTag styleTag,
            RecipeSortType sort,
            Long lastId,
            Integer size
    ) {
    }

    public record RecipeSummaryResponse(
            Long id,
            Long authorId,
            String authorNickname,
            String title,
            HousingType housingType,
            Short areaPyeong,
            ContractType contractType,
            boolean isNoDrill,
            int budget,
            StyleTag styleTag,
            String thumbnailUrl,
            int likeCount,
            int scrapCount,
            int viewCount,
            Instant createdAt
    ) {
        public static RecipeSummaryResponse from(RecipePost post) {
            return new RecipeSummaryResponse(
                    post.getId(),
                    post.getUser().getId(),
                    post.getUser().getNickname(),
                    post.getTitle(),
                    post.getHousingType(),
                    post.getAreaPyeong(),
                    post.getContractType(),
                    post.isNoDrill(),
                    post.getBudget(),
                    post.getStyleTag(),
                    post.getThumbnailUrl(),
                    post.getLikeCount(),
                    post.getScrapCount(),
                    post.getViewCount(),
                    post.getCreatedAt()
            );
        }
    }

    public record RecipePageResponse(
            List<RecipeSummaryResponse> items,
            Long nextCursor,
            boolean hasNext
    ) {
    }

    public record RecipeItemResponse(
            Long id,
            String itemName,
            String category,
            int price,
            String purchaseNote
    ) {
        public static RecipeItemResponse from(RecipeItem item) {
            return new RecipeItemResponse(
                    item.getId(),
                    item.getItemName(),
                    item.getCategory(),
                    item.getPrice(),
                    item.getPurchaseNote()
            );
        }
    }

    public record RecipeDetailResponse(
            Long id,
            Long authorId,
            String authorNickname,
            String title,
            String content,
            HousingType housingType,
            Short areaPyeong,
            ContractType contractType,
            boolean isNoDrill,
            int budget,
            StyleTag styleTag,
            String thumbnailUrl,
            List<String> images,
            List<RecipeItemResponse> items,
            int likeCount,
            int scrapCount,
            int viewCount,
            boolean likedByMe,
            boolean scrappedByMe,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ToggleResponse(boolean active, int count) {
    }

    public record CommentCreateRequest(
            Long parentId,
            @NotBlank @Size(max = 1000) String content
    ) {
    }

    public record CommentUpdateRequest(
            @NotBlank @Size(max = 1000) String content
    ) {
    }

    public record CommentResponse(
            Long id,
            Long postId,
            Long parentId,
            Long authorId,
            String authorNickname,
            String content,
            Instant createdAt
    ) {
        public static CommentResponse from(RecipeComment c) {
            return new CommentResponse(
                    c.getId(),
                    c.getPost().getId(),
                    c.getParent() != null ? c.getParent().getId() : null,
                    c.getUser().getId(),
                    c.getUser().getNickname(),
                    c.getContent(),
                    c.getCreatedAt()
            );
        }
    }
}
