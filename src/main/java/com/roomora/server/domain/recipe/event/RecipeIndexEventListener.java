package com.roomora.server.domain.recipe.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 레시피 커밋 이후(AFTER_COMMIT) 비동기 RAG 색인을 수행하는 리스너.
 * 마일스톤 5에서 RagIndexingService와 연결된다.
 */
@Slf4j
@Component
public class RecipeIndexEventListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRecipeIndexEvent(RecipeIndexEvent event) {
        log.info("Queued async RAG index event: recipeId={}, action={}", event.recipeId(), event.action());
    }
}
