package com.roomora.server.domain.contest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ContestRoundRepository extends JpaRepository<ContestRound, Long> {

    Optional<ContestRound> findFirstByStatusOrderByRoundNoDesc(ContestRoundStatus status);

    List<ContestRound> findByStatusAndEndAtBefore(ContestRoundStatus status, Instant now);
}
