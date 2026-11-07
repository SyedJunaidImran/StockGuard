package com.stockguard.catalog.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for toggling product active status.
 */
public class ProductStatusUpdateRequest {

    @NotNull(message = "Active status is required")
    private Boolean active;

    public ProductStatusUpdateRequest() {
    }

    public ProductStatusUpdateRequest(Boolean active) {
        this.active = active;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
