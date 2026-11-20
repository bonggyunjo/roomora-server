package com.roomora.server.domain.recipe;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recipe_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecipeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private RecipePost post;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false)
    private int price;

    @Column(name = "purchase_note", length = 300)
    private String purchaseNote;

    public RecipeItem(RecipePost post, String itemName, String category, int price, String purchaseNote) {
        this.post = post;
        this.itemName = itemName;
        this.category = category;
        this.price = price;
        this.purchaseNote = purchaseNote;
    }
}
