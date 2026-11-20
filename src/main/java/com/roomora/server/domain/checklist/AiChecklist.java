package com.roomora.server.domain.checklist;

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
@Table(name = "ai_checklist")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiChecklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(name = "query_summary", length = 300)
    private String querySummary;

    @Column(name = "target_budget")
    private Integer targetBudget;

    @Column(name = "housing_type", length = 30)
    private String housingType;

    @Column(name = "is_no_drill")
    private Boolean isNoDrill;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "checklist", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    private List<AiChecklistItem> items = new ArrayList<>();

    @Builder
    public AiChecklist(User user, String title, String querySummary, Integer targetBudget,
                       String housingType, Boolean isNoDrill) {
        this.user = user;
        this.title = title;
        this.querySummary = querySummary;
        this.targetBudget = targetBudget;
        this.housingType = housingType;
        this.isNoDrill = isNoDrill;
        this.createdAt = Instant.now();
    }

    public void addItem(AiChecklistItem item) {
        this.items.add(item);
    }

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }
}
