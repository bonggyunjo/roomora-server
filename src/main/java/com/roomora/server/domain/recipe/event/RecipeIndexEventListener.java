package com.roomora.server.domain.recipe.event;

import com.roomora.server.domain.rag.RagIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 레시피 커밋 이후(AFTER_COMMIT) 비동기 RAG 색인을 수행하는 리스너.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecipeIndexEventListener {

    private final RagIndexingService ragIndexingService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRecipeIndexEvent(RecipeIndexEvent event) {
        try {
            if (event.action() == RecipeIndexEvent.Action.DELETED) {
                ragIndexingService.deleteRecipe(event.recipeId());
            } else {
                ragIndexingService.indexRecipe(event.recipeId());
            }
        } catch (Exception ex) {
            log.warn("Async RAG indexing failed for recipeId={}, action={}: {}",
                    event.recipeId(), event.action(), ex.getMessage());
        }
    }
}
