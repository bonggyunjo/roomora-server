package com.roomora.server.domain.contest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContestClosingScheduler {

    private final ContestService contestService;

    @Scheduled(fixedDelay = 60_000)
    public void closeExpiredRounds() {
        int closedCount = contestService.closeExpiredRounds(Instant.now());
        if (closedCount > 0) {
            log.info("Closed {} expired contest round(s) and finalized rankings.", closedCount);
        }
    }
}
