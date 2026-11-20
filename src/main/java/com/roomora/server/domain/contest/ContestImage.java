package com.roomora.server.domain.contest;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "contest_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContestImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_post_id", nullable = false)
    private ContestPost contestPost;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public ContestImage(ContestPost contestPost, String imageUrl, int sortOrder) {
        this.contestPost = contestPost;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
    }
}
