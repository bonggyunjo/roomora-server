package com.roomora.server.domain.contest;

import com.roomora.server.domain.contest.dto.ContestDtos.*;
import com.roomora.server.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contests")
@RequiredArgsConstructor
public class ContestController {

    private final ContestService contestService;
    private final ContestCommentService commentService;

    @GetMapping("/rounds/active")
    public ResponseEntity<RoundResponse> getActiveRound() {
        return ResponseEntity.ok(contestService.getActiveRound());
    }

    @GetMapping("/rounds/{roundId}/ranking")
    public ResponseEntity<List<ContestRankingItemResponse>> getRanking(
            @PathVariable Long roundId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(contestService.getRanking(roundId, limit));
    }

    @GetMapping("/rounds/{roundId}/winners")
    public ResponseEntity<List<ContestRankingItemResponse>> getWinners(@PathVariable Long roundId) {
        return ResponseEntity.ok(contestService.getWinners(roundId));
    }

    @PostMapping("/rounds/{roundId}/posts")
    public ResponseEntity<ContestPostDetailResponse> submitPost(
            @PathVariable Long roundId,
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ContestPostCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contestService.submitPost(roundId, user.getUserId(), request));
    }

    @GetMapping("/posts/{postId}")
    public ResponseEntity<ContestPostDetailResponse> getPostDetail(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails user) {
        Long userId = user != null ? user.getUserId() : null;
        return ResponseEntity.ok(contestService.getPostDetail(postId, userId));
    }

    @PatchMapping("/posts/{postId}")
    public ResponseEntity<ContestPostDetailResponse> updatePost(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ContestPostUpdateRequest request) {
        return ResponseEntity.ok(contestService.updatePost(postId, user.getUserId(), request));
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails user) {
        contestService.deletePost(postId, user.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts/{postId}/vote")
    public ResponseEntity<VoteResponse> vote(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(contestService.vote(postId, user.getUserId()));
    }

    @PostMapping("/posts/{postId}/scrap")
    public ResponseEntity<ScrapToggleResponse> toggleScrap(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(contestService.toggleScrap(postId, user.getUserId()));
    }

    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<List<ContestCommentResponse>> listComments(@PathVariable Long postId) {
        return ResponseEntity.ok(commentService.list(postId));
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<ContestCommentResponse> createComment(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ContestCommentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.create(postId, user.getUserId(), request));
    }

    @PatchMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<ContestCommentResponse> updateComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ContestCommentUpdateRequest request) {
        return ResponseEntity.ok(commentService.update(commentId, user.getUserId(), request));
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails user) {
        commentService.delete(commentId, user.getUserId());
        return ResponseEntity.noContent().build();
    }
}
