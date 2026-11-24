package com.roomora.server.domain.contest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContestPostRepository extends JpaRepository<ContestPost, Long> {

    Optional<ContestPost> findByIdAndDeletedAtIsNull(Long id);

    Page<ContestPost> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId, Pageable pageable);

    boolean existsByRoundIdAndUserIdAndDeletedAtIsNull(Long roundId, Long userId);

    List<ContestPost> findByRoundIdAndDeletedAtIsNullOrderByVoteCountDescIdAsc(Long roundId);

    List<ContestPost> findByRoundIdAndFinalRankIsNotNullAndDeletedAtIsNullOrderByFinalRankAsc(Long roundId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ContestPost c SET c.voteCount = c.voteCount + 1 WHERE c.id = :id")
    void incrementVoteCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ContestPost c SET c.scrapCount = c.scrapCount + 1 WHERE c.id = :id")
    void incrementScrapCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ContestPost c SET c.scrapCount = CASE WHEN c.scrapCount > 0 THEN c.scrapCount - 1 ELSE 0 END WHERE c.id = :id")
    void decrementScrapCount(@Param("id") Long id);
}
