package com.roomora.server.domain.contest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContestCommentRepository extends JpaRepository<ContestComment, Long> {

    @Query("SELECT c FROM ContestComment c JOIN FETCH c.user WHERE c.contestPost.id = :postId AND c.deletedAt IS NULL ORDER BY c.createdAt ASC")
    List<ContestComment> findActiveByPostIdWithUser(@Param("postId") Long postId);

    Optional<ContestComment> findByIdAndDeletedAtIsNull(Long id);
}
