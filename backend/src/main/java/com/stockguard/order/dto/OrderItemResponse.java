package com.stockguard.order.dto;

import com.stockguard.order.model.OrderItem;

import java.math.BigDecimal;

/**
 * Response payload representing an immutable order line item snapshot.
 */
public class OrderItemResponse {

    private Long id;
    private Long productId;
    private String productSku;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;

    public OrderItemResponse() {
    }

    public OrderItemResponse(Long id, Long productId, String productSku, String productName,
                             int quantity, BigDecimal unitPrice, BigDecimal subtotal) {
        this.id = id;
        this.productId = productId;
        this.productSku = productSku;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
    }

    public static OrderItemResponse fromEntity(OrderItem item) {
        if (item == null) {
            return null;
        }

        Long prodId = item.getProduct() != null ? item.getProduct().getId() : null;
        String prodSku = item.getProduct() != null ? item.getProduct().getSku() : null;
        String prodName = item.getProduct() != null ? item.getProduct().getName() : null;

        return new OrderItemResponse(
                item.getId(),
                prodId,
                prodSku,
                prodName,
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal()
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

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }
}
