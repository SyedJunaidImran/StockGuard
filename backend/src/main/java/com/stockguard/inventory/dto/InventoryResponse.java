package com.stockguard.inventory.dto;

import com.stockguard.inventory.model.Inventory;

import java.time.Instant;

/**
 * Safe DTO exposing inventory levels without internal persistence secrets.
 */
public class InventoryResponse {

    private Long id;
    private Long productId;
    private String productSku;
    private String productName;
    private int availableStock;
    private int reservedStock;
    private int lowStockThreshold;
    private boolean inStock;
    private boolean lowStock;
    private Instant updatedAt;

    public InventoryResponse() {
    }

    public InventoryResponse(Long id, Long productId, String productSku, String productName,
                             int availableStock, int reservedStock, int lowStockThreshold,
                             boolean inStock, boolean lowStock, Instant updatedAt) {
        this.id = id;
        this.productId = productId;
        this.productSku = productSku;
        this.productName = productName;
        this.availableStock = availableStock;
        this.reservedStock = reservedStock;
        this.lowStockThreshold = lowStockThreshold;
        this.inStock = inStock;
        this.lowStock = lowStock;
        this.updatedAt = updatedAt;
    }

    public static InventoryResponse fromEntity(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        return new InventoryResponse(
                inventory.getId(),
                inventory.getProduct() != null ? inventory.getProduct().getId() : null,
                inventory.getProduct() != null ? inventory.getProduct().getSku() : null,
                inventory.getProduct() != null ? inventory.getProduct().getName() : null,
                inventory.getAvailableStock(),
                inventory.getReservedStock(),
                inventory.getLowStockThreshold(),
                inventory.isInStock(),
                inventory.isLowStock(),
                inventory.getUpdatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductSku() {
        return productSku;
    }

    public void setProductSku(String productSku) {
        this.productSku = productSku;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getAvailableStock() {
        return availableStock;
    }

    public void setAvailableStock(int availableStock) {
        this.availableStock = availableStock;
    }

    public int getReservedStock() {
        return reservedStock;
    }

    public void setReservedStock(int reservedStock) {
        this.reservedStock = reservedStock;
    }

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }

    public boolean isInStock() {
        return inStock;
    }

    public void setInStock(boolean inStock) {
        this.inStock = inStock;
    }

    public boolean isLowStock() {
        return lowStock;
    }

    public void setLowStock(boolean lowStock) {
        this.lowStock = lowStock;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
