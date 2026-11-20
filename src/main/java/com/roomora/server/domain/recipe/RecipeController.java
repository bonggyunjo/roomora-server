package com.roomora.server.domain.recipe;

import com.roomora.server.domain.recipe.dto.RecipeDtos.*;
import com.roomora.server.global.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;
    private final RecipeCommentService commentService;

    @PostMapping
    public ResponseEntity<RecipeDetailResponse> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody RecipeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recipeService.create(user.getUserId(), request));
    }

    @GetMapping
    public ResponseEntity<RecipePageResponse> list(
            @RequestParam(required = false) HousingType housingType,
            @RequestParam(required = false) Boolean isNoDrill,
            @RequestParam(required = false) Integer maxBudget,
            @RequestParam(required = false) Short minPyeong,
            @RequestParam(required = false) Short maxPyeong,
            @RequestParam(required = false) StyleTag styleTag,
            @RequestParam(required = false) RecipeSortType sort,
            @RequestParam(required = false) Long lastId,
            @RequestParam(required = false) Integer size) {
        RecipeSearchCondition condition = new RecipeSearchCondition(
                housingType, isNoDrill, maxBudget, minPyeong, maxPyeong, styleTag, sort, lastId, size
        );
        return ResponseEntity.ok(recipeService.list(condition));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecipeDetailResponse> getDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user,
            HttpServletRequest request) {
        Long userId = user != null ? user.getUserId() : null;
        String viewerKey = userId != null ? "u:" + userId : "ip:" + request.getRemoteAddr();
        return ResponseEntity.ok(recipeService.getDetail(id, userId, viewerKey));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<RecipeDetailResponse> update(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody RecipeUpdateRequest request) {
        return ResponseEntity.ok(recipeService.update(id, user.getUserId(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        recipeService.delete(id, user.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<ToggleResponse> toggleLike(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(recipeService.toggleLike(id, user.getUserId()));
    }

    @PostMapping("/{id}/scrap")
    public ResponseEntity<ToggleResponse> toggleScrap(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(recipeService.toggleScrap(id, user.getUserId()));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<List<CommentResponse>> listComments(@PathVariable Long id) {
        return ResponseEntity.ok(commentService.list(id));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CommentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.create(id, user.getUserId(), request));
    }

    @PatchMapping("/{id}/comments/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            @PathVariable Long id,
            @PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CommentUpdateRequest request) {
        return ResponseEntity.ok(commentService.update(commentId, user.getUserId(), request));
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long id,
            @PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails user) {
        commentService.delete(commentId, user.getUserId());
        return ResponseEntity.noContent().build();
    }
}
