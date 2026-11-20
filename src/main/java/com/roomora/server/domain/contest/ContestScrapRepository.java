package com.roomora.server.domain.contest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ContestScrapRepository extends JpaRepository<ContestScrap, Long> {

    Optional<ContestScrap> findByContestPostIdAndUserId(Long contestPostId, Long userId);

    boolean existsByContestPostIdAndUserId(Long contestPostId, Long userId);

    @Query("SELECT s FROM ContestScrap s JOIN FETCH s.contestPost p JOIN FETCH p.user " +
           "WHERE s.user.id = :userId AND p.deletedAt IS NULL " +
           "ORDER BY s.createdAt DESC")
    Page<ContestScrap> findByUserIdAndActivePost(@Param("userId") Long userId, Pageable pageable);
}
