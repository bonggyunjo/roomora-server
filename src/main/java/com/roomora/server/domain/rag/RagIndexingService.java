package com.roomora.server.domain.rag;

import com.roomora.server.domain.guide.InteriorGuide;
import com.roomora.server.domain.guide.InteriorGuideRepository;
import com.roomora.server.domain.recipe.RecipeItem;
import com.roomora.server.domain.recipe.RecipePost;
import com.roomora.server.domain.recipe.RecipePostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagIndexingService {

    private final RecipePostRepository recipePostRepository;
    private final InteriorGuideRepository guideRepository;
    private final RagDocumentChunkRepository chunkRepository;
    private final RagChunker chunker;
    private final RagEmbeddingClient embeddingClient;
    private final TransactionTemplate transactionTemplate;

    public void indexRecipe(Long recipeId) {
        IndexPayload payload = loadRecipePayload(recipeId);
        if (payload == null) {
            deleteSource(RagSourceType.RECIPE, recipeId);
            return;
        }
        indexPayloadOutsideTx(RagSourceType.RECIPE, recipeId, payload);
    }

    public void deleteRecipe(Long recipeId) {
        deleteSource(RagSourceType.RECIPE, recipeId);
    }

    public void indexGuide(Long guideId) {
        IndexPayload payload = loadGuidePayload(guideId);
        if (payload == null) {
            deleteSource(RagSourceType.GUIDE, guideId);
            return;
        }
        indexPayloadOutsideTx(RagSourceType.GUIDE, guideId, payload);
    }

    public int reindexAllGuidesAndRecipes() {
        List<Long> recipeIds = transactionTemplate.execute(status ->
                recipePostRepository.findAll().stream()
                        .filter(r -> r.getDeletedAt() == null)
                        .map(RecipePost::getId)
                        .toList());
        List<Long> guideIds = transactionTemplate.execute(status ->
                guideRepository.findAll().stream()
                        .map(InteriorGuide::getId)
                        .toList());

        int count = 0;
        if (recipeIds != null) {
            for (Long id : recipeIds) {
                indexRecipe(id);
                count++;
            }
        }
        if (guideIds != null) {
            for (Long id : guideIds) {
                indexGuide(id);
                count++;
            }
        }
        return count;
    }

    private IndexPayload loadRecipePayload(Long recipeId) {
        return transactionTemplate.execute(status -> {
            RecipePost post = recipePostRepository.findByIdAndDeletedAtIsNull(recipeId).orElse(null);
            if (post == null) {
                return null;
            }
            String itemsSummary = post.getItems().isEmpty() ? "" : "\n투입 품목: " + post.getItems().stream()
                    .map(i -> i.getItemName() + "(" + i.getCategory() + ", " + i.getPrice() + "원)")
                    .collect(Collectors.joining(", "));
            String prefix = String.format("[%s | %d평 | %s | %s | %d원 | %s]",
                    post.getHousingType().name(),
                    post.getAreaPyeong(),
                    post.getContractType().name(),
                    post.isNoDrill() ? "무타공" : "시공가능",
                    post.getBudget(),
                    post.getStyleTag().name());

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("title", post.getTitle());
            metadata.put("housingType", post.getHousingType().name());
            metadata.put("areaPyeong", post.getAreaPyeong().intValue());
            metadata.put("contractType", post.getContractType().name());
            metadata.put("isNoDrill", post.isNoDrill());
            metadata.put("budget", post.getBudget());
            metadata.put("styleTag", post.getStyleTag().name());
            if (post.getThumbnailUrl() != null) {
                metadata.put("thumbnailUrl", post.getThumbnailUrl());
            }
            return new IndexPayload(prefix, post.getTitle(), post.getContent() + itemsSummary, metadata);
        });
    }

    private IndexPayload loadGuidePayload(Long guideId) {
        return transactionTemplate.execute(status -> {
            InteriorGuide guide = guideRepository.findById(guideId).orElse(null);
            if (guide == null) {
                return null;
            }
            String prefix = String.format("[가이드 | %s | %s]",
                    guide.getCategory().name(),
                    guide.getStyleTag() != null ? guide.getStyleTag() : "공통");
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("title", guide.getTitle());
            metadata.put("category", guide.getCategory().name());
            if (guide.getStyleTag() != null) {
                metadata.put("styleTag", guide.getStyleTag());
            }
            return new IndexPayload(prefix, guide.getTitle(), guide.getContent(), metadata);
        });
    }

    private void indexPayloadOutsideTx(RagSourceType sourceType, Long sourceId, IndexPayload payload) {
        List<String> chunks = chunker.chunkWithPrefix(payload.prefix(), payload.title(), payload.content());
        // External embedding API called OUTSIDE any DB transaction
        List<float[]> vectors = chunks.stream().map(embeddingClient::embed).toList();

        transactionTemplate.executeWithoutResult(status -> {
            for (int i = 0; i < chunks.size(); i++) {
                chunkRepository.upsertChunk(sourceType, sourceId, i, chunks.get(i), payload.metadata(), vectors.get(i));
            }
        });
        log.debug("Indexed {} chunks for {}#{}", chunks.size(), sourceType, sourceId);
    }

    @Transactional
    protected void deleteSource(RagSourceType sourceType, Long sourceId) {
        chunkRepository.deleteBySource(sourceType, sourceId);
    }

    private record IndexPayload(String prefix, String title, String content, Map<String, Object> metadata) {
    }
}
