package com.roomora.server.domain.checklist;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_checklist_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_id", nullable = false)
    private AiChecklist checklist;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false, length = 300)
    private String content;

    @Column(name = "estimated_cost", nullable = false)
    private int estimatedCost;

    @Column(name = "is_checked", nullable = false)
    private boolean checked;

    @Column(length = 300)
    private String memo;

    @Builder
    public AiChecklistItem(AiChecklist checklist, int stepOrder, String category,
                           String content, int estimatedCost, String memo) {
        this.checklist = checklist;
        this.stepOrder = stepOrder;
        this.category = category;
        this.content = content;
        this.estimatedCost = Math.max(estimatedCost, 0);
        this.checked = false;
        this.memo = memo;
    }

    public void update(Boolean checked, String memo, String content, Integer estimatedCost) {
        if (checked != null) {
            this.checked = checked;
        }
        if (memo != null) {
            this.memo = memo;
        }
        if (content != null && !content.isBlank()) {
            this.content = content;
        }
        if (estimatedCost != null && estimatedCost >= 0) {
            this.estimatedCost = estimatedCost;
        }
    }
}
