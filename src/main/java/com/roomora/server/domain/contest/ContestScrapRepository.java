package com.roomora.server.domain.contest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContestScrapRepository extends JpaRepository<ContestScrap, Long> {

    Optional<ContestScrap> findByContestPostIdAndUserId(Long contestPostId, Long userId);

    boolean existsByContestPostIdAndUserId(Long contestPostId, Long userId);
}
