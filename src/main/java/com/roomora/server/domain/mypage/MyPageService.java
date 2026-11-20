package com.roomora.server.domain.mypage;

import com.roomora.server.domain.contest.ContestCommentRepository;
import com.roomora.server.domain.contest.ContestPost;
import com.roomora.server.domain.contest.ContestPostRepository;
import com.roomora.server.domain.contest.ContestScrapRepository;
import com.roomora.server.domain.mypage.dto.SocialDtos.*;
import com.roomora.server.domain.recipe.RecipeCommentRepository;
import com.roomora.server.domain.recipe.RecipePost;
import com.roomora.server.domain.recipe.RecipePostRepository;
import com.roomora.server.domain.recipe.RecipeLikeRepository;
import com.roomora.server.domain.recipe.RecipeScrapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageService {

    private final RecipePostRepository recipePostRepository;
    private final RecipeLikeRepository recipeLikeRepository;
    private final RecipeScrapRepository recipeScrapRepository;
    private final RecipeCommentRepository recipeCommentRepository;
    private final ContestPostRepository contestPostRepository;
    private final ContestScrapRepository contestScrapRepository;
    private final ContestCommentRepository contestCommentRepository;

    public PagedListResponse<MyRecipeItem> getMyRecipes(Long userId, int page, int size) {
        Page<RecipePost> res = recipePostRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                userId, PageRequest.of(Math.max(page, 0), safeSize(size)));
        return new PagedListResponse<>(
                res.getContent().stream().map(MyRecipeItem::from).toList(),
                res.getNumber(), res.getSize(), res.getTotalElements(), res.hasNext()
        );
    }

    public PagedListResponse<MyContestItem> getMyContests(Long userId, int page, int size) {
        Page<ContestPost> res = contestPostRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                userId, PageRequest.of(Math.max(page, 0), safeSize(size)));
        return new PagedListResponse<>(
                res.getContent().stream().map(MyContestItem::from).toList(),
                res.getNumber(), res.getSize(), res.getTotalElements(), res.hasNext()
        );
    }

    public PagedListResponse<MyCommentItem> getMyComments(Long userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = safeSize(size);
        int fetchLimit = (safePage + 1) * safeSize;

        List<MyCommentItem> combined = new ArrayList<>();
        recipeCommentRepository.findActiveByUserId(userId, PageRequest.of(0, fetchLimit))
                .forEach(c -> combined.add(new MyCommentItem(
                        "RECIPE", c.getId(), c.getPost().getId(), c.getPost().getTitle(),
                        c.getContent(), c.getCreatedAt()
                )));
        contestCommentRepository.findActiveByUserId(userId, PageRequest.of(0, fetchLimit))
                .forEach(c -> combined.add(new MyCommentItem(
                        "CONTEST", c.getId(), c.getContestPost().getId(), c.getContestPost().getTitle(),
                        c.getContent(), c.getCreatedAt()
                )));

        combined.sort(Comparator.comparing(MyCommentItem::createdAt).reversed());
        int from = Math.min(safePage * safeSize, combined.size());
        int to = Math.min(from + safeSize, combined.size());
        return new PagedListResponse<>(
                combined.subList(from, to),
                safePage,
                safeSize,
                combined.size(),
                to < combined.size()
        );
    }

    public PagedListResponse<MyRecipeItem> getMyLikedRecipes(Long userId, int page, int size) {
        var res = recipeLikeRepository.findByUserIdAndActivePost(
                userId, PageRequest.of(Math.max(page, 0), safeSize(size)));
        return new PagedListResponse<>(
                res.getContent().stream().map(l -> MyRecipeItem.from(l.getPost())).toList(),
                res.getNumber(), res.getSize(), res.getTotalElements(), res.hasNext()
        );
    }

    public PagedListResponse<MyScrapItem> getMyScraps(Long userId, ScrapTargetType type, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize(size));
        if (type == ScrapTargetType.CONTEST) {
            var res = contestScrapRepository.findByUserIdAndActivePost(userId, pageable);
            return new PagedListResponse<>(
                    res.getContent().stream().map(s -> new MyScrapItem(
                            ScrapTargetType.CONTEST,
                            s.getContestPost().getId(),
                            s.getContestPost().getTitle(),
                            s.getContestPost().getThumbnailUrl(),
                            s.getContestPost().getUser().getNickname(),
                            s.getCreatedAt()
                    )).toList(),
                    res.getNumber(), res.getSize(), res.getTotalElements(), res.hasNext()
            );
        }

        var res = recipeScrapRepository.findByUserIdAndActivePost(userId, pageable);
        return new PagedListResponse<>(
                res.getContent().stream().map(s -> new MyScrapItem(
                        ScrapTargetType.RECIPE,
                        s.getPost().getId(),
                        s.getPost().getTitle(),
                        s.getPost().getThumbnailUrl(),
                        s.getPost().getUser().getNickname(),
                        s.getCreatedAt()
                )).toList(),
                res.getNumber(), res.getSize(), res.getTotalElements(), res.hasNext()
        );
    }

    private int safeSize(int size) {
        return (size > 0 && size <= 50) ? size : 20;
    }
}
