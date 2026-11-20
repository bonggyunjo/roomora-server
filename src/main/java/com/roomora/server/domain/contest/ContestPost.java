package com.roomora.server.domain.contest;

import com.roomora.server.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "contest_post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContestPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    private ContestRound round;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "vote_count", nullable = false)
    private int voteCount;

    @Column(name = "scrap_count", nullable = false)
    private int scrapCount;

    @Column(name = "final_rank")
    private Integer finalRank;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "contestPost", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ContestImage> images = new ArrayList<>();

    @Builder
    public ContestPost(ContestRound round, User user, String title, String content, String thumbnailUrl) {
        this.round = round;
        this.user = user;
        this.title = title;
        this.content = content;
        this.thumbnailUrl = thumbnailUrl;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void replaceImages(List<String> imageUrls) {
        this.images.clear();
        if (imageUrls == null) {
            return;
        }
        for (int i = 0; i < imageUrls.size(); i++) {
            this.images.add(new ContestImage(this, imageUrls.get(i), i));
        }
    }

    public void update(String title, String content, String thumbnailUrl) {
        this.title = title;
        this.content = content;
        this.thumbnailUrl = thumbnailUrl;
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = this.deletedAt;
    }

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }

    public void assignFinalRank(int rank) {
        this.finalRank = rank;
        this.updatedAt = Instant.now();
    }
}
