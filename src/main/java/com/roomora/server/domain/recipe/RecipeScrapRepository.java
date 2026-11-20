package com.roomora.server.domain.recipe;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RecipeScrapRepository extends JpaRepository<RecipeScrap, Long> {

    Optional<RecipeScrap> findByPostIdAndUserId(Long postId, Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    @Query("SELECT s FROM RecipeScrap s JOIN FETCH s.post p JOIN FETCH p.user " +
           "WHERE s.user.id = :userId AND p.deletedAt IS NULL " +
           "ORDER BY s.createdAt DESC")
    Page<RecipeScrap> findByUserIdAndActivePost(@Param("userId") Long userId, Pageable pageable);
}
