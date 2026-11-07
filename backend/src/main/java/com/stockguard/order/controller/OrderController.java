package com.stockguard.order.controller;

import com.stockguard.common.dto.ApiResponse;
import com.stockguard.common.dto.PageResponse;
import com.stockguard.order.dto.OrderCreateRequest;
import com.stockguard.order.dto.OrderResponse;
import com.stockguard.order.service.OrderService;
import com.stockguard.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for placing orders and querying order history.
 * Order creation and customer order history are strictly guarded by ROLE_CUSTOMER.
 * Individual order inspection is accessible by the owning customer or administrators.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Creates a customer order atomically with concurrency-safe stock deduction
     * and optional idempotency-key deduplication.
     *
     * @param request        Order items and optional notes
     * @param idempotencyKey Optional unique client key to prevent duplicate order placement
     * @param principal      Authenticated CUSTOMER principal
     * @return 201 Created with order snapshot details
     */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody OrderCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        OrderResponse response = orderService.createOrder(request, principal, idempotencyKey);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.CREATED);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            builder.header("Idempotency-Key", idempotencyKey.trim());
        }
        return builder.body(ApiResponse.success("Order placed successfully", response));
    }

    /**
     * Retrieves paginated order history strictly for the authenticated customer.
     *
     * @param page      Page index (0-based, default 0)
     * @param size      Page size (default 10)
     * @param principal Authenticated CUSTOMER principal
     * @return 200 OK with paginated order history
     */
    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PageResponse<OrderResponse> response = orderService.getCustomerOrders(page, size, principal);
        return ResponseEntity.ok(ApiResponse.success("Order history retrieved successfully", response));
    }

    /**
     * Retrieves an order by ID. Accessible to the customer who placed the order or an administrator.
     *
     * @param id        Order ID
     * @param principal Authenticated CUSTOMER or ADMIN principal
     * @return 200 OK with order details
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        OrderResponse response = orderService.getOrderById(id, principal);
        return ResponseEntity.ok(ApiResponse.success("Order retrieved successfully", response));
    }
}
