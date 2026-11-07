package com.stockguard.inventory.dto;

import com.stockguard.inventory.model.InventoryMovement;
import com.stockguard.inventory.model.MovementType;

import java.time.Instant;

/**
 * Safe DTO exposing an audit movement ledger record.
 * Never leaks user password hashes or sensitive security information.
 */
public class InventoryMovementResponse {

    private Long id;
    private Long inventoryId;
    private Long productId;
    private String productSku;
    private String productName;
    private int quantityChange;
    private int previousStock;
    private int newStock;
    private MovementType movementType;
    private String reason;
    private String referenceId;
    private Long actorId;
    private String actorEmail;
    private String actorName;
    private Instant createdAt;

    public InventoryMovementResponse() {
    }

    public InventoryMovementResponse(Long id, Long inventoryId, Long productId, String productSku,
                                   String productName, int quantityChange, int previousStock,
                                   int newStock, MovementType movementType, String reason,
                                   String referenceId, Long actorId, String actorEmail,
                                   String actorName, Instant createdAt) {
        this.id = id;
        this.inventoryId = inventoryId;
        this.productId = productId;
        this.productSku = productSku;
        this.productName = productName;
        this.quantityChange = quantityChange;
        this.previousStock = previousStock;
        this.newStock = newStock;
        this.movementType = movementType;
        this.reason = reason;
        this.referenceId = referenceId;
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.actorName = actorName;
        this.createdAt = createdAt;
    }

    public static InventoryMovementResponse fromEntity(InventoryMovement movement) {
        if (movement == null) {
            return null;
        }

        Long actorId = null;
        String actorEmail = null;
        String actorName = null;

        if (movement.getActor() != null) {
            actorId = movement.getActor().getId();
            actorEmail = movement.getActor().getEmail();
            actorName = movement.getActor().getFirstName() + " " + movement.getActor().getLastName();
        }

        return new InventoryMovementResponse(
                movement.getId(),
                movement.getInventory() != null ? movement.getInventory().getId() : null,
                movement.getProduct() != null ? movement.getProduct().getId() : null,
                movement.getProduct() != null ? movement.getProduct().getSku() : null,
                movement.getProduct() != null ? movement.getProduct().getName() : null,
                movement.getQuantityChange(),
                movement.getPreviousStock(),
                movement.getNewStock(),
                movement.getMovementType(),
                movement.getReason(),
                movement.getReferenceId(),
                actorId,
                actorEmail,
                actorName,
                movement.getCreatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getInventoryId() {
        return inventoryId;
    }

    public void setInventoryId(Long inventoryId) {
        this.inventoryId = inventoryId;
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

    public int getQuantityChange() {
        return quantityChange;
    }

    public void setQuantityChange(int quantityChange) {
        this.quantityChange = quantityChange;
    }

    public int getPreviousStock() {
        return previousStock;
    }

    public void setPreviousStock(int previousStock) {
        this.previousStock = previousStock;
    }

    public int getNewStock() {
        return newStock;
    }

    public void setNewStock(int newStock) {
        this.newStock = newStock;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(MovementType movementType) {
        this.movementType = movementType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public void setActorEmail(String actorEmail) {
        this.actorEmail = actorEmail;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
