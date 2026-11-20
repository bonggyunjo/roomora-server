package com.roomora.server.domain.recipe;

import com.roomora.server.domain.recipe.dto.RecipeDtos.*;
import com.roomora.server.domain.recipe.event.RecipeIndexEvent;
import com.roomora.server.domain.user.User;
import com.roomora.server.domain.user.UserRepository;
import com.roomora.server.global.exception.BusinessException;
import com.roomora.server.global.exception.ErrorCode;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeService {

    private static final String VIEW_KEY_PREFIX = "recipe:view:";
    private static final Duration VIEW_TTL = Duration.ofHours(1);

    private final RecipePostRepository recipePostRepository;
    private final RecipeLikeRepository recipeLikeRepository;
    private final RecipeScrapRepository recipeScrapRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public RecipeDetailResponse create(Long userId, RecipeCreateRequest request) {
        User author = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        RecipePost post = RecipePost.builder()
                .user(author)
                .title(request.title())
                .content(request.content())
                .housingType(request.housingType())
                .areaPyeong(request.areaPyeong())
                .contractType(request.contractType())
                .noDrill(request.isNoDrill())
                .budget(request.budget())
                .styleTag(request.styleTag())
                .thumbnailUrl(request.thumbnailUrl())
                .build();

        post.replaceImages(request.images());
        post.replaceItems(toItemEntities(post, request.items()));

        RecipePost saved = recipePostRepository.save(post);
        eventPublisher.publishEvent(new RecipeIndexEvent(saved.getId(), RecipeIndexEvent.Action.CREATED));
        return toDetailResponse(saved, false, false);
    }

    public RecipePageResponse list(RecipeSearchCondition cond) {
        int pageSize = (cond.size() != null && cond.size() > 0 && cond.size() <= 50) ? cond.size() : 20;
        RecipeSortType sortType = cond.sort() != null ? cond.sort() : RecipeSortType.LATEST;

        Specification<RecipePost> spec = buildFilterSpec(cond, sortType);
        Sort sort = toSort(sortType);
        List<RecipePost> fetched = recipePostRepository.findAll(spec, PageRequest.of(0, pageSize + 1, sort)).getContent();

        boolean hasNext = fetched.size() > pageSize;
        List<RecipePost> pageItems = hasNext ? fetched.subList(0, pageSize) : fetched;
        Long nextCursor = hasNext ? pageItems.get(pageItems.size() - 1).getId() : null;

        List<RecipeSummaryResponse> responses = pageItems.stream()
                .map(RecipeSummaryResponse::from)
                .toList();
        return new RecipePageResponse(responses, nextCursor, hasNext);
    }

    @Transactional
    public RecipeDetailResponse getDetail(Long postId, Long currentUserId, String viewerKey) {
        RecipePost post = findActivePost(postId);

        String redisKey = VIEW_KEY_PREFIX + postId + ":" + viewerKey;
        Boolean firstView = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", VIEW_TTL);
        if (Boolean.TRUE.equals(firstView)) {
            recipePostRepository.incrementViewCount(postId);
            post = findActivePost(postId);
        }

        boolean liked = currentUserId != null && recipeLikeRepository.existsByPostIdAndUserId(postId, currentUserId);
        boolean scrapped = currentUserId != null && recipeScrapRepository.existsByPostIdAndUserId(postId, currentUserId);
        return toDetailResponse(post, liked, scrapped);
    }

    @Transactional
    public RecipeDetailResponse update(Long postId, Long userId, RecipeUpdateRequest request) {
        RecipePost post = findActivePost(postId);
        if (!post.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        post.update(
                request.title(),
                request.content(),
                request.housingType(),
                request.areaPyeong(),
                request.contractType(),
                request.isNoDrill(),
                request.budget(),
                request.styleTag(),
                request.thumbnailUrl()
        );
        post.replaceImages(request.images());
        post.replaceItems(toItemEntities(post, request.items()));

        eventPublisher.publishEvent(new RecipeIndexEvent(post.getId(), RecipeIndexEvent.Action.UPDATED));
        boolean liked = recipeLikeRepository.existsByPostIdAndUserId(postId, userId);
        boolean scrapped = recipeScrapRepository.existsByPostIdAndUserId(postId, userId);
        return toDetailResponse(post, liked, scrapped);
    }

    @Transactional
    public void delete(Long postId, Long userId) {
        RecipePost post = findActivePost(postId);
        if (!post.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        post.softDelete();
        eventPublisher.publishEvent(new RecipeIndexEvent(post.getId(), RecipeIndexEvent.Action.DELETED));
    }

    @Transactional
    public ToggleResponse toggleLike(Long postId, Long userId) {
        RecipePost post = findActivePost(postId);
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        var existing = recipeLikeRepository.findByPostIdAndUserId(postId, userId);
        boolean active;
        if (existing.isPresent()) {
            recipeLikeRepository.delete(existing.get());
            recipePostRepository.decrementLikeCount(postId);
            active = false;
        } else {
            recipeLikeRepository.save(new RecipeLike(post, user));
            recipePostRepository.incrementLikeCount(postId);
            active = true;
        }
        int updatedCount = findActivePost(postId).getLikeCount();
        return new ToggleResponse(active, updatedCount);
    }

    @Transactional
    public ToggleResponse toggleScrap(Long postId, Long userId) {
        RecipePost post = findActivePost(postId);
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        var existing = recipeScrapRepository.findByPostIdAndUserId(postId, userId);
        boolean active;
        if (existing.isPresent()) {
            recipeScrapRepository.delete(existing.get());
            recipePostRepository.decrementScrapCount(postId);
            active = false;
        } else {
            recipeScrapRepository.save(new RecipeScrap(post, user));
            recipePostRepository.incrementScrapCount(postId);
            active = true;
        }
        int updatedCount = findActivePost(postId).getScrapCount();
        return new ToggleResponse(active, updatedCount);
    }

    private RecipePost findActivePost(Long postId) {
        return recipePostRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECIPE_NOT_FOUND));
    }

    private List<RecipeItem> toItemEntities(RecipePost post, List<RecipeItemRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .map(r -> new RecipeItem(post, r.itemName(), r.category(), r.price(), r.purchaseNote()))
                .toList();
    }

    private Specification<RecipePost> buildFilterSpec(RecipeSearchCondition cond, RecipeSortType sortType) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (cond.housingType() != null) {
                predicates.add(cb.equal(root.get("housingType"), cond.housingType()));
            }
            if (cond.isNoDrill() != null) {
                predicates.add(cb.equal(root.get("noDrill"), cond.isNoDrill()));
            }
            if (cond.maxBudget() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("budget"), cond.maxBudget()));
            }
            if (cond.minPyeong() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("areaPyeong"), cond.minPyeong()));
            }
            if (cond.maxPyeong() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("areaPyeong"), cond.maxPyeong()));
            }
            if (cond.styleTag() != null) {
                predicates.add(cb.equal(root.get("styleTag"), cond.styleTag()));
            }
            if (cond.lastId() != null && sortType == RecipeSortType.LATEST) {
                predicates.add(cb.lessThan(root.get("id"), cond.lastId()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Sort toSort(RecipeSortType sortType) {
        return switch (sortType) {
            case LIKES -> Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("id"));
            case SCRAPS -> Sort.by(Sort.Order.desc("scrapCount"), Sort.Order.desc("id"));
            case BUDGET_ASC -> Sort.by(Sort.Order.asc("budget"), Sort.Order.desc("id"));
            case LATEST -> Sort.by(Sort.Order.desc("id"));
        };
    }

    private RecipeDetailResponse toDetailResponse(RecipePost post, boolean liked, boolean scrapped) {
        return new RecipeDetailResponse(
                post.getId(),
                post.getUser().getId(),
                post.getUser().getNickname(),
                post.getTitle(),
                post.getContent(),
                post.getHousingType(),
                post.getAreaPyeong(),
                post.getContractType(),
                post.isNoDrill(),
                post.getBudget(),
                post.getStyleTag(),
                post.getThumbnailUrl(),
                post.getImages().stream().map(RecipeImage::getImageUrl).toList(),
                post.getItems().stream().map(RecipeItemResponse::from).toList(),
                post.getLikeCount(),
                post.getScrapCount(),
                post.getViewCount(),
                liked,
                scrapped,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
