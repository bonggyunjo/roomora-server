package com.roomora.server.domain.contest;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContestVoteRepository extends JpaRepository<ContestVote, Long> {

    boolean existsByRoundIdAndUserId(Long roundId, Long userId);
}
