package com.roomora.server.domain.contest;

import com.roomora.server.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "contest_comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContestComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_post_id", nullable = false)
    private ContestPost contestPost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ContestComment parent;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ContestComment(ContestPost contestPost, User user, ContestComment parent, String content) {
        this.contestPost = contestPost;
        this.user = user;
        this.parent = parent;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }
}
