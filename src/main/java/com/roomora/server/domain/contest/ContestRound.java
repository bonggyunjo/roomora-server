package com.roomora.server.domain.contest;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "contest_round")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContestRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "round_no", nullable = false, unique = true)
    private int roundNo;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "theme_description", nullable = false, columnDefinition = "TEXT")
    private String themeDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContestRoundStatus status;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public ContestRound(int roundNo, String title, String themeDescription,
                        ContestRoundStatus status, Instant startAt, Instant endAt) {
        this.roundNo = roundNo;
        this.title = title;
        this.themeDescription = themeDescription;
        this.status = status != null ? status : ContestRoundStatus.UPCOMING;
        this.startAt = startAt;
        this.endAt = endAt;
        this.createdAt = Instant.now();
    }

    public boolean isActive() {
        return this.status == ContestRoundStatus.ACTIVE;
    }

    public void close() {
        this.status = ContestRoundStatus.CLOSED;
    }
}
