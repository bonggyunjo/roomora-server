package com.roomora.server.domain.contest;

import com.roomora.server.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "contest_scrap")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContestScrap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_post_id", nullable = false)
    private ContestPost contestPost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ContestScrap(ContestPost contestPost, User user) {
        this.contestPost = contestPost;
        this.user = user;
        this.createdAt = Instant.now();
    }
}
