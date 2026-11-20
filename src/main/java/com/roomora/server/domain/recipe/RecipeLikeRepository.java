package com.roomora.server.domain.recipe;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RecipeLikeRepository extends JpaRepository<RecipeLike, Long> {

    Optional<RecipeLike> findByPostIdAndUserId(Long postId, Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    @Query("SELECT l FROM RecipeLike l JOIN FETCH l.post p JOIN FETCH p.user " +
           "WHERE l.user.id = :userId AND p.deletedAt IS NULL " +
           "ORDER BY l.createdAt DESC")
    Page<RecipeLike> findByUserIdAndActivePost(@Param("userId") Long userId, Pageable pageable);
}
