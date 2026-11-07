package com.stockguard.inventory.model;

import com.stockguard.catalog.model.Product;
import com.stockguard.inventory.exception.InsufficientStockException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;

/**
 * Inventory entity mapping to the Flyway {@code inventory} table.
 * Isolates stock quantities from product catalog metadata to eliminate contention.
 */
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Product product;

    @Column(name = "available_stock", nullable = false)
    private int availableStock = 0;

    @Column(name = "reserved_stock", nullable = false)
    private int reservedStock = 0;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold = 10;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Inventory() {
    }

    public Inventory(Product product) {
        this.product = product;
        this.availableStock = 0;
        this.reservedStock = 0;
        this.lowStockThreshold = 10;
        this.version = 0L;
    }

    public Inventory(Product product, int availableStock, int lowStockThreshold) {
        if (availableStock < 0) {
            throw new IllegalArgumentException("Available stock cannot be negative");
        }
        if (lowStockThreshold < 0) {
            throw new IllegalArgumentException("Low stock threshold cannot be negative");
        }
        this.product = product;
        this.availableStock = availableStock;
        this.reservedStock = 0;
        this.lowStockThreshold = lowStockThreshold;
        this.version = 0L;
    }

    @PrePersist
    protected void onCreate() {
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
        if (this.version == null) {
            this.version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Increases available stock by positive quantity.
     */
    public void addStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to add must be greater than zero");
        }
        this.availableStock += quantity;
        this.updatedAt = Instant.now();
    }

    /**
     * Decreases available stock by positive quantity, verifying sufficient stock.
     */
    public void removeStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to remove must be greater than zero");
        }
        if (this.availableStock < quantity) {
            throw new InsufficientStockException(
                    String.format("Insufficient stock for product '%s' (SKU: %s): requested %d, but only %d available",
                            product != null ? product.getName() : "Unknown",
                            product != null ? product.getSku() : "N/A",
                            quantity,
                            this.availableStock)
            );
        }
        this.availableStock -= quantity;
        this.updatedAt = Instant.now();
    }

    public boolean isLowStock() {
        return this.availableStock <= this.lowStockThreshold;
    }

    public boolean isInStock() {
        return this.availableStock > 0;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public int getAvailableStock() {
        return availableStock;
    }

    public void setAvailableStock(int availableStock) {
        if (availableStock < 0) {
            throw new IllegalArgumentException("Available stock cannot be negative");
        }
        this.availableStock = availableStock;
    }

    public int getReservedStock() {
        return reservedStock;
    }

    public void setReservedStock(int reservedStock) {
        if (reservedStock < 0) {
            throw new IllegalArgumentException("Reserved stock cannot be negative");
        }
        this.reservedStock = reservedStock;
    }

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(int lowStockThreshold) {
        if (lowStockThreshold < 0) {
            throw new IllegalArgumentException("Low stock threshold cannot be negative");
        }
        this.lowStockThreshold = lowStockThreshold;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Inventory inventory = (Inventory) o;
        return Objects.equals(id, inventory.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Inventory{" +
                "id=" + id +
                ", productId=" + (product != null ? product.getId() : null) +
                ", availableStock=" + availableStock +
                ", reservedStock=" + reservedStock +
                ", lowStockThreshold=" + lowStockThreshold +
                ", version=" + version +
                '}';
    }
}
