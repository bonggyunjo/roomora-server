package com.roomora.server.domain.contest.dto;

import com.roomora.server.domain.contest.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ContestDtos {

    private ContestDtos() {
    }

    public record RoundResponse(
            Long id,
            int roundNo,
            String title,
            String themeDescription,
            ContestRoundStatus status,
            Instant startAt,
            Instant endAt
    ) {
        public static RoundResponse from(ContestRound r) {
            return new RoundResponse(
                    r.getId(), r.getRoundNo(), r.getTitle(),
                    r.getThemeDescription(), r.getStatus(), r.getStartAt(), r.getEndAt()
            );
        }
    }

    public record ContestPostCreateRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank String content,
            @Size(max = 500) String thumbnailUrl,
            List<@NotBlank @Size(max = 500) String> images
    ) {
    }

    public record ContestPostUpdateRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank String content,
            @Size(max = 500) String thumbnailUrl,
            List<@NotBlank @Size(max = 500) String> images
    ) {
    }

    public record ContestPostDetailResponse(
            Long id,
            Long roundId,
            Long authorId,
            String authorNickname,
            String title,
            String content,
            String thumbnailUrl,
            List<String> images,
            int voteCount,
            int scrapCount,
            Integer finalRank,
            boolean votedByMe,
            boolean scrappedByMe,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ContestRankingItemResponse(
            int rank,
            Long postId,
            String title,
            String thumbnailUrl,
            Long authorId,
            String authorNickname,
            int voteCount
    ) {
    }

    public record VoteResponse(Long postId, int voteCount) {
    }

    public record ScrapToggleResponse(boolean active, int count) {
    }

    public record ContestCommentCreateRequest(
            Long parentId,
            @NotBlank @Size(max = 1000) String content
    ) {
    }

    public record ContestCommentUpdateRequest(
            @NotBlank @Size(max = 1000) String content
    ) {
    }

    public record ContestCommentResponse(
            Long id,
            Long postId,
            Long parentId,
            Long authorId,
            String authorNickname,
            String content,
            Instant createdAt
    ) {
        public static ContestCommentResponse from(ContestComment c) {
            return new ContestCommentResponse(
                    c.getId(),
                    c.getContestPost().getId(),
                    c.getParent() != null ? c.getParent().getId() : null,
                    c.getUser().getId(),
                    c.getUser().getNickname(),
                    c.getContent(),
                    c.getCreatedAt()
            );
        }
    }
}
