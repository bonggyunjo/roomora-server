package com.roomora.server.domain.recipe.event;

public record RecipeIndexEvent(Long recipeId, Action action) {

    public enum Action {
        CREATED, UPDATED, DELETED
    }
}
