package com.roomora.server.domain.contest;

import com.roomora.server.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "contest_vote")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContestVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    private ContestRound round;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_post_id", nullable = false)
    private ContestPost contestPost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ContestVote(ContestRound round, ContestPost contestPost, User user) {
        this.round = round;
        this.contestPost = contestPost;
        this.user = user;
        this.createdAt = Instant.now();
    }
}
