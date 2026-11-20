package com.roomora.server.domain.recipe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecipeCommentRepository extends JpaRepository<RecipeComment, Long> {

    @Query("SELECT c FROM RecipeComment c JOIN FETCH c.user WHERE c.post.id = :postId AND c.deletedAt IS NULL ORDER BY c.createdAt ASC")
    List<RecipeComment> findActiveByPostIdWithUser(@Param("postId") Long postId);

    Optional<RecipeComment> findByIdAndDeletedAtIsNull(Long id);
}
