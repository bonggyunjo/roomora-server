package com.roomora.server.domain.contest;

import com.roomora.server.domain.contest.dto.ContestDtos.*;
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
public class ContestCommentService {

    private final ContestCommentRepository commentRepository;
    private final ContestPostRepository postRepository;
    private final UserRepository userRepository;

    public List<ContestCommentResponse> list(Long postId) {
        if (postRepository.findByIdAndDeletedAtIsNull(postId).isEmpty()) {
            throw new BusinessException(ErrorCode.CONTEST_POST_NOT_FOUND);
        }
        return commentRepository.findActiveByPostIdWithUser(postId).stream()
                .map(ContestCommentResponse::from)
                .toList();
    }

    @Transactional
    public ContestCommentResponse create(Long postId, Long userId, ContestCommentCreateRequest request) {
        ContestPost post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEST_POST_NOT_FOUND));
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ContestComment parent = null;
        if (request.parentId() != null) {
            parent = commentRepository.findByIdAndDeletedAtIsNull(request.parentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
            if (!parent.getContestPost().getId().equals(postId) || parent.getParent() != null) {
                throw new BusinessException(ErrorCode.INVALID_COMMENT_DEPTH);
            }
        }

        ContestComment saved = commentRepository.save(new ContestComment(post, user, parent, request.content()));
        return ContestCommentResponse.from(saved);
    }

    @Transactional
    public ContestCommentResponse update(Long commentId, Long userId, ContestCommentUpdateRequest request) {
        ContestComment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        comment.updateContent(request.content());
        return ContestCommentResponse.from(comment);
    }

    @Transactional
    public void delete(Long commentId, Long userId) {
        ContestComment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        comment.softDelete();
    }
}
