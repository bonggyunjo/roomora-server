package com.roomora.server.domain.recipe;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecipeScrapRepository extends JpaRepository<RecipeScrap, Long> {

    Optional<RecipeScrap> findByPostIdAndUserId(Long postId, Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);
}
