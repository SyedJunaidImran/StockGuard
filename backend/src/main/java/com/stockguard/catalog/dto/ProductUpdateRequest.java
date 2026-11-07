package com.stockguard.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request payload for updating an existing product catalog entry.
 */
public class ProductUpdateRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name cannot exceed 255 characters")
    private String name;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    @NotBlank(message = "SKU is required")
    @Size(min = 3, max = 100, message = "SKU must be between 3 and 100 characters")
    private String sku;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "Price cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Price format must have at most 10 integer digits and 2 decimals")
    private BigDecimal price;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @Size(max = 1024, message = "Image URL cannot exceed 1024 characters")
    private String imageUrl;

    private Boolean active;

    public ProductUpdateRequest() {
    }

    public ProductUpdateRequest(String name, String description, String sku,
                                BigDecimal price, Long categoryId, String imageUrl, Boolean active) {
        this.name = name;
        this.description = description;
        this.sku = sku;
        this.price = price;
        this.categoryId = categoryId;
        this.imageUrl = imageUrl;
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
