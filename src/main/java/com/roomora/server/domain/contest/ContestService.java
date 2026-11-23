package com.roomora.server.domain.contest;

import com.roomora.server.domain.contest.ContestRankingRedisRepository.RankedEntry;
import com.roomora.server.domain.contest.dto.ContestDtos.*;
import com.roomora.server.domain.rag.RagIndexingService;
import com.roomora.server.domain.user.User;
import com.roomora.server.domain.user.UserRepository;
import com.roomora.server.global.exception.BusinessException;
import com.roomora.server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContestService {

    private final ContestRoundRepository roundRepository;
    private final ContestPostRepository postRepository;
    private final ContestVoteRepository voteRepository;
    private final ContestScrapRepository scrapRepository;
    private final ContestRankingRedisRepository rankingRedisRepository;
    private final UserRepository userRepository;
    private final RagIndexingService ragIndexingService;

    public RoundResponse getActiveRound() {
        ContestRound round = roundRepository.findFirstByStatusOrderByRoundNoDesc(ContestRoundStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_ROUND_NOT_FOUND));
        return RoundResponse.from(round);
    }

    public List<ContestRankingItemResponse> getRanking(Long roundId, int limit) {
        if (!roundRepository.existsById(roundId)) {
            throw new BusinessException(ErrorCode.CONTEST_ROUND_NOT_FOUND);
        }
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<RankedEntry> ranked = rankingRedisRepository.getTopN(roundId, safeLimit);
        if (ranked.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = ranked.stream().map(RankedEntry::postId).toList();
        Map<Long, ContestPost> postMap = postRepository.findAllById(postIds).stream()
                .filter(p -> p.getDeletedAt() == null)
                .collect(Collectors.toMap(ContestPost::getId, Function.identity()));

        List<ContestRankingItemResponse> result = new ArrayList<>();
        int rank = 1;
        for (RankedEntry entry : ranked) {
            ContestPost post = postMap.get(entry.postId());
            if (post != null) {
                result.add(new ContestRankingItemResponse(
                        rank++,
                        post.getId(),
                        post.getTitle(),
                        post.getThumbnailUrl(),
                        post.getUser().getId(),
                        post.getUser().getNickname(),
                        entry.score()
                ));
            }
        }
        return result;
    }

    public List<ContestRankingItemResponse> getWinners(Long roundId) {
        if (!roundRepository.existsById(roundId)) {
            throw new BusinessException(ErrorCode.CONTEST_ROUND_NOT_FOUND);
        }
        List<ContestPost> winners = postRepository
                .findByRoundIdAndFinalRankIsNotNullAndDeletedAtIsNullOrderByFinalRankAsc(roundId);
        return winners.stream()
                .map(p -> new ContestRankingItemResponse(
                        p.getFinalRank(),
                        p.getId(),
                        p.getTitle(),
                        p.getThumbnailUrl(),
                        p.getUser().getId(),
                        p.getUser().getNickname(),
                        p.getVoteCount()
                ))
                .toList();
    }

    @Transactional
    public ContestPostDetailResponse submitPost(Long roundId, Long userId, ContestPostCreateRequest request) {
        ContestRound round = roundRepository.findById(roundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_ROUND_NOT_FOUND));
        if (!round.isActive()) {
            throw new BusinessException(ErrorCode.CONTEST_ROUND_NOT_ACTIVE);
        }
        if (postRepository.existsByRoundIdAndUserIdAndDeletedAtIsNull(roundId, userId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_CONTEST_SUBMISSION);
        }
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ContestPost post = ContestPost.builder()
                .round(round)
                .user(user)
                .title(request.title())
                .content(request.content())
                .thumbnailUrl(request.thumbnailUrl())
                .build();
        post.replaceImages(request.images());

        try {
            ContestPost saved = postRepository.saveAndFlush(post);
            registerAfterCommit(() -> {
                rankingRedisRepository.initializeEntry(roundId, saved.getId());
                ragIndexingService.indexContestPost(saved.getId());
            });
            return toDetailResponse(saved, false, false);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_CONTEST_SUBMISSION);
        }
    }

    public ContestPostDetailResponse getPostDetail(Long postId, Long currentUserId) {
        ContestPost post = findActivePost(postId);
        boolean voted = currentUserId != null && voteRepository.existsByRoundIdAndUserId(post.getRound().getId(), currentUserId);
        boolean scrapped = currentUserId != null && scrapRepository.existsByContestPostIdAndUserId(postId, currentUserId);
        return toDetailResponse(post, voted, scrapped);
    }

    @Transactional
    public ContestPostDetailResponse updatePost(Long postId, Long userId, ContestPostUpdateRequest request) {
        ContestPost post = findActivePost(postId);
        if (!post.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!post.getRound().isActive()) {
            throw new BusinessException(ErrorCode.CONTEST_ROUND_NOT_ACTIVE);
        }
        post.update(request.title(), request.content(), request.thumbnailUrl());
        post.replaceImages(request.images());
        registerAfterCommit(() -> ragIndexingService.indexContestPost(post.getId()));
        boolean voted = voteRepository.existsByRoundIdAndUserId(post.getRound().getId(), userId);
        boolean scrapped = scrapRepository.existsByContestPostIdAndUserId(postId, userId);
        return toDetailResponse(post, voted, scrapped);
    }

    @Transactional
    public void deletePost(Long postId, Long userId) {
        ContestPost post = findActivePost(postId);
        if (!post.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        post.softDelete();
        Long roundId = post.getRound().getId();
        registerAfterCommit(() -> {
            rankingRedisRepository.removeEntry(roundId, postId);
            ragIndexingService.deleteContestPost(postId);
        });
    }

    @Transactional
    public VoteResponse vote(Long postId, Long userId) {
        ContestPost post = findActivePost(postId);
        ContestRound round = post.getRound();
        if (!round.isActive()) {
            throw new BusinessException(ErrorCode.CONTEST_ROUND_NOT_ACTIVE);
        }
        if (post.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.SELF_VOTE_NOT_ALLOWED);
        }
        if (voteRepository.existsByRoundIdAndUserId(round.getId(), userId)) {
            throw new BusinessException(ErrorCode.ALREADY_VOTED_IN_ROUND);
        }
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        try {
            voteRepository.saveAndFlush(new ContestVote(round, post, user));
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.ALREADY_VOTED_IN_ROUND);
        }

        postRepository.incrementVoteCount(postId);
        Long roundId = round.getId();
        registerAfterCommit(() -> rankingRedisRepository.incrementScore(roundId, postId));

        int updatedVoteCount = findActivePost(postId).getVoteCount();
        return new VoteResponse(postId, updatedVoteCount);
    }

    @Transactional
    public ScrapToggleResponse toggleScrap(Long postId, Long userId) {
        ContestPost post = findActivePost(postId);
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        var existing = scrapRepository.findByContestPostIdAndUserId(postId, userId);
        boolean active;
        if (existing.isPresent()) {
            scrapRepository.delete(existing.get());
            postRepository.decrementScrapCount(postId);
            active = false;
        } else {
            scrapRepository.save(new ContestScrap(post, user));
            postRepository.incrementScrapCount(postId);
            active = true;
        }
        return new ScrapToggleResponse(active, findActivePost(postId).getScrapCount());
    }

    @Transactional
    public int closeExpiredRounds(Instant now) {
        List<ContestRound> expired = roundRepository.findByStatusAndEndAtBefore(ContestRoundStatus.ACTIVE, now);
        for (ContestRound round : expired) {
            round.close();
            finalizeRoundRanking(round.getId());
        }
        return expired.size();
    }

    private void finalizeRoundRanking(Long roundId) {
        List<RankedEntry> redisRanked = rankingRedisRepository.getAllRanked(roundId);
        if (!redisRanked.isEmpty()) {
            Map<Long, ContestPost> postMap = postRepository
                    .findByRoundIdAndDeletedAtIsNullOrderByVoteCountDescIdAsc(roundId).stream()
                    .collect(Collectors.toMap(ContestPost::getId, Function.identity()));
            int rank = 1;
            for (RankedEntry entry : redisRanked) {
                ContestPost post = postMap.remove(entry.postId());
                if (post != null) {
                    post.assignFinalRank(rank++);
                }
            }
            for (ContestPost remaining : postMap.values()) {
                remaining.assignFinalRank(rank++);
            }
            return;
        }

        List<ContestPost> rdbPosts = postRepository.findByRoundIdAndDeletedAtIsNullOrderByVoteCountDescIdAsc(roundId);
        int rank = 1;
        for (ContestPost post : rdbPosts) {
            post.assignFinalRank(rank++);
        }
    }

    private void registerAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private ContestPost findActivePost(Long postId) {
        return postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_POST_NOT_FOUND));
    }

    private ContestPostDetailResponse toDetailResponse(ContestPost post, boolean voted, boolean scrapped) {
        return new ContestPostDetailResponse(
                post.getId(),
                post.getRound().getId(),
                post.getUser().getId(),
                post.getUser().getNickname(),
                post.getTitle(),
                post.getContent(),
                post.getThumbnailUrl(),
                post.getImages().stream().map(ContestImage::getImageUrl).toList(),
                post.getVoteCount(),
                post.getScrapCount(),
                post.getFinalRank(),
                voted,
                scrapped,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
