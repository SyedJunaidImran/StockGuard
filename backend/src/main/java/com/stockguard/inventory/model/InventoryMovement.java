package com.stockguard.inventory.model;

import com.stockguard.catalog.model.Product;
import com.stockguard.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable append-only audit ledger recording every inventory modification.
 * Maps to the Flyway {@code inventory_movements} table.
 */
@Entity
@Table(name = "inventory_movements")
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Inventory inventory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Product product;

    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    @Column(name = "previous_stock", nullable = false)
    private int previousStock;

    @Column(name = "new_stock", nullable = false)
    private int newStock;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 50)
    private MovementType movementType;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public InventoryMovement() {
    }

    public InventoryMovement(Inventory inventory, Product product, int quantityChange,
                             int previousStock, int newStock, MovementType movementType,
                             String reason, String referenceId, User actor) {
        if (quantityChange == 0) {
            throw new IllegalArgumentException("Quantity change cannot be zero");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Audit reason cannot be null or empty");
        }
        this.inventory = inventory;
        this.product = product;
        this.quantityChange = quantityChange;
        this.previousStock = previousStock;
        this.newStock = newStock;
        this.movementType = movementType != null ? movementType : MovementType.MANUAL_ADJUSTMENT;
        this.reason = reason.trim();
        this.referenceId = referenceId != null ? referenceId.trim() : null;
        this.actor = actor;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantityChange() {
        return quantityChange;
    }

    public int getPreviousStock() {
        return previousStock;
    }

    public int getNewStock() {
        return newStock;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public String getReason() {
        return reason;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public User getActor() {
        return actor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InventoryMovement that = (InventoryMovement) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "InventoryMovement{" +
                "id=" + id +
                ", productId=" + (product != null ? product.getId() : null) +
                ", quantityChange=" + quantityChange +
                ", previousStock=" + previousStock +
                ", newStock=" + newStock +
                ", movementType=" + movementType +
                ", reason='" + reason + '\'' +
                ", referenceId='" + referenceId + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
