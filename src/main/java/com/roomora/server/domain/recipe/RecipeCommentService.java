package com.roomora.server.domain.recipe;

import com.roomora.server.domain.recipe.dto.RecipeDtos.*;
import com.roomora.server.domain.user.User;
import com.roomora.server.domain.user.UserRepository;
import com.roomora.server.global.exception.BusinessException;
import com.roomora.server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeCommentService {

    private final RecipeCommentRepository commentRepository;
    private final RecipePostRepository recipePostRepository;
    private final UserRepository userRepository;

    public List<CommentResponse> list(Long postId) {
        if (recipePostRepository.findByIdAndDeletedAtIsNull(postId).isEmpty()) {
            throw new BusinessException(ErrorCode.RECIPE_NOT_FOUND);
        }
        return commentRepository.findActiveByPostIdWithUser(postId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    @Transactional
    public CommentResponse create(Long postId, Long userId, CommentCreateRequest request) {
        RecipePost post = recipePostRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECIPE_NOT_FOUND));
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        RecipeComment parent = null;
        if (request.parentId() != null) {
            parent = commentRepository.findByIdAndDeletedAtIsNull(request.parentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
            if (!parent.getPost().getId().equals(postId) || parent.getParent() != null) {
                throw new BusinessException(ErrorCode.INVALID_COMMENT_DEPTH);
            }
        }

        RecipeComment saved = commentRepository.save(new RecipeComment(post, user, parent, request.content()));
        return CommentResponse.from(saved);
    }

    @Transactional
    public CommentResponse update(Long commentId, Long userId, CommentUpdateRequest request) {
        RecipeComment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        comment.updateContent(request.content());
        return CommentResponse.from(comment);
    }

    @Transactional
    public void delete(Long commentId, Long userId) {
        RecipeComment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        comment.softDelete();
    }
}
