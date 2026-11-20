package com.roomora.server.domain.guide;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "interior_guide")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InteriorGuide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GuideCategory category;

    @Column(name = "style_tag", length = 30)
    private String styleTag;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "color_palette", length = 200)
    private String colorPalette;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public InteriorGuide(String title, GuideCategory category, String styleTag,
                         String content, String colorPalette) {
        this.title = title;
        this.category = category;
        this.styleTag = styleTag;
        this.content = content;
        this.colorPalette = colorPalette;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
}
