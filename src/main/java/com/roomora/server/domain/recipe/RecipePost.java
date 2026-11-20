package com.roomora.server.domain.recipe;

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
@Table(name = "recipe_post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipePost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "housing_type", nullable = false, length = 30)
    private HousingType housingType;

    @Column(name = "area_pyeong", nullable = false)
    private Short areaPyeong;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 20)
    private ContractType contractType;

    @Column(name = "is_no_drill", nullable = false)
    private boolean noDrill;

    @Column(nullable = false)
    private int budget;

    @Enumerated(EnumType.STRING)
    @Column(name = "style_tag", nullable = false, length = 30)
    private StyleTag styleTag;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "scrap_count", nullable = false)
    private int scrapCount;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<RecipeImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecipeItem> items = new ArrayList<>();

    @Builder
    public RecipePost(User user, String title, String content, HousingType housingType,
                      Short areaPyeong, ContractType contractType, boolean noDrill,
                      int budget, StyleTag styleTag, String thumbnailUrl) {
        this.user = user;
        this.title = title;
        this.content = content;
        this.housingType = housingType;
        this.areaPyeong = areaPyeong;
        this.contractType = contractType;
        this.noDrill = noDrill;
        this.budget = budget;
        this.styleTag = styleTag;
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
            this.images.add(new RecipeImage(this, imageUrls.get(i), i));
        }
    }

    public void replaceItems(List<RecipeItem> newItems) {
        this.items.clear();
        if (newItems != null) {
            this.items.addAll(newItems);
        }
    }

    public void update(String title, String content, HousingType housingType, Short areaPyeong,
                       ContractType contractType, boolean noDrill, int budget,
                       StyleTag styleTag, String thumbnailUrl) {
        this.title = title;
        this.content = content;
        this.housingType = housingType;
        this.areaPyeong = areaPyeong;
        this.contractType = contractType;
        this.noDrill = noDrill;
        this.budget = budget;
        this.styleTag = styleTag;
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
}
