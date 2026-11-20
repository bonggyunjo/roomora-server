package com.roomora.server.domain.mypage.dto;

import com.roomora.server.domain.contest.ContestPost;
import com.roomora.server.domain.recipe.RecipePost;
import com.roomora.server.domain.user.AuthProvider;
import com.roomora.server.domain.user.User;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class SocialDtos {

    private SocialDtos() {
    }

    public enum ScrapTargetType {
        RECIPE, CONTEST
    }

    public record ProfileUpdateRequest(
            @Size(min = 2, max = 30) String nickname,
            @Size(max = 500) String profileImageUrl,
            @Size(max = 200) String bio
    ) {
    }

    public record MyProfileResponse(
            Long id,
            String email,
            String nickname,
            String profileImageUrl,
            String bio,
            AuthProvider provider,
            int followerCount,
            int followingCount,
            Instant createdAt
    ) {
        public static MyProfileResponse from(User u) {
            return new MyProfileResponse(
                    u.getId(), u.getEmail(), u.getNickname(), u.getProfileImageUrl(),
                    u.getBio(), u.getProvider(), u.getFollowerCount(), u.getFollowingCount(),
                    u.getCreatedAt()
            );
        }
    }

    public record PublicUserProfileResponse(
            Long id,
            String nickname,
            String profileImageUrl,
            String bio,
            int followerCount,
            int followingCount,
            boolean followedByMe
    ) {
        public static PublicUserProfileResponse from(User u, boolean followedByMe) {
            return new PublicUserProfileResponse(
                    u.getId(), u.getNickname(), u.getProfileImageUrl(), u.getBio(),
                    u.getFollowerCount(), u.getFollowingCount(), followedByMe
            );
        }
    }

    public record FollowToggleResponse(
            boolean following,
            int followerCount,
            int followingCount
    ) {
    }

    public record FollowUserItem(
            Long userId,
            String nickname,
            String profileImageUrl,
            String bio
    ) {
        public static FollowUserItem from(User u) {
            return new FollowUserItem(u.getId(), u.getNickname(), u.getProfileImageUrl(), u.getBio());
        }
    }

    public record MyRecipeItem(
            Long id,
            String title,
            String thumbnailUrl,
            int budget,
            int likeCount,
            int scrapCount,
            Instant createdAt
    ) {
        public static MyRecipeItem from(RecipePost p) {
            return new MyRecipeItem(
                    p.getId(), p.getTitle(), p.getThumbnailUrl(), p.getBudget(),
                    p.getLikeCount(), p.getScrapCount(), p.getCreatedAt()
            );
        }
    }

    public record MyContestItem(
            Long id,
            Long roundId,
            String title,
            String thumbnailUrl,
            int voteCount,
            int scrapCount,
            Integer finalRank,
            Instant createdAt
    ) {
        public static MyContestItem from(ContestPost p) {
            return new MyContestItem(
                    p.getId(), p.getRound().getId(), p.getTitle(), p.getThumbnailUrl(),
                    p.getVoteCount(), p.getScrapCount(), p.getFinalRank(), p.getCreatedAt()
            );
        }
    }

    public record MyCommentItem(
            String targetType,
            Long commentId,
            Long postId,
            String postTitle,
            String content,
            Instant createdAt
    ) {
    }

    public record MyScrapItem(
            ScrapTargetType type,
            Long postId,
            String title,
            String thumbnailUrl,
            String authorNickname,
            Instant scrappedAt
    ) {
    }

    public record PagedListResponse<T>(
            List<T> items,
            int page,
            int size,
            long totalElements,
            boolean hasNext
    ) {
    }
}
