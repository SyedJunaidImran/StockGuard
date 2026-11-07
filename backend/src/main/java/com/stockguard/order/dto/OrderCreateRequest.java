package com.stockguard.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request payload for creating a customer order.
 * Accepts only product IDs, quantities, and optional notes.
 * Pricing and totals are strictly computed server-side.
 */
public class OrderCreateRequest {

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;

    public OrderCreateRequest() {
    }

    public OrderCreateRequest(List<OrderItemRequest> items, String notes) {
        this.items = items;
        this.notes = notes;
    }

    public List<OrderItemRequest> getItems() {
        return items;
    }

    public void setItems(List<OrderItemRequest> items) {
        this.items = items;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
