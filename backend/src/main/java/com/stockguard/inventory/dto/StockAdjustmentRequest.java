package com.stockguard.inventory.dto;

import com.stockguard.inventory.model.AdjustmentType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Validated request payload for administrative stock adjustments.
 */
public class StockAdjustmentRequest {

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Adjustment quantity must be a positive integer greater than zero")
    private Integer quantity;

    @NotNull(message = "Adjustment type is required (ADD or REMOVE)")
    private AdjustmentType type;

    @NotBlank(message = "Adjustment reason is required")
    @Size(max = 255, message = "Reason cannot exceed 255 characters")
    private String reason;

    @Size(max = 100, message = "Reference ID cannot exceed 100 characters")
    private String referenceId;

    public StockAdjustmentRequest() {
    }

    public StockAdjustmentRequest(Integer quantity, AdjustmentType type, String reason) {
        this.quantity = quantity;
        this.type = type;
        this.reason = reason;
    }

    public StockAdjustmentRequest(Integer quantity, AdjustmentType type, String reason, String referenceId) {
        this.quantity = quantity;
        this.type = type;
        this.reason = reason;
        this.referenceId = referenceId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public AdjustmentType getType() {
        return type;
    }

    public void setType(AdjustmentType type) {
        this.type = type;
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
}
