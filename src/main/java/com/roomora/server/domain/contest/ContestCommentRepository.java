package com.roomora.server.domain.contest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContestCommentRepository extends JpaRepository<ContestComment, Long> {

    @Query("SELECT c FROM ContestComment c JOIN FETCH c.user WHERE c.contestPost.id = :postId AND c.deletedAt IS NULL ORDER BY c.createdAt ASC")
    List<ContestComment> findActiveByPostIdWithUser(@Param("postId") Long postId);

    @Query("SELECT c FROM ContestComment c JOIN FETCH c.contestPost p " +
           "WHERE c.user.id = :userId AND c.deletedAt IS NULL AND p.deletedAt IS NULL " +
           "ORDER BY c.createdAt DESC")
    Page<ContestComment> findActiveByUserId(@Param("userId") Long userId, Pageable pageable);

    Optional<ContestComment> findByIdAndDeletedAtIsNull(Long id);
}
