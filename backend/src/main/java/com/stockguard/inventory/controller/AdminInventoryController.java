package com.stockguard.inventory.controller;

import com.stockguard.common.dto.ApiResponse;
import com.stockguard.common.dto.PageResponse;
import com.stockguard.inventory.dto.InventoryMovementResponse;
import com.stockguard.inventory.dto.InventoryResponse;
import com.stockguard.inventory.dto.StockAdjustmentRequest;
import com.stockguard.inventory.service.InventoryService;
import com.stockguard.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative controller for inventory adjustments, stock monitoring,
 * and immutable movement audit ledger inspection.
 * Strictly restricted to users with the ADMIN authority.
 */
@RestController
@RequestMapping("/api/v1/admin/inventory")
@PreAuthorize("hasRole('ADMIN')")
public class AdminInventoryController {

    private final InventoryService inventoryService;

    public AdminInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * Executes an administrative stock adjustment under transactional write lock.
     * Appends an audit ledger record atomically attributing the authenticated admin actor.
     */
    @PostMapping("/{productId}/adjustments")
    public ResponseEntity<ApiResponse<InventoryResponse>> adjustStock(
            @PathVariable Long productId,
            @Valid @RequestBody StockAdjustmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        InventoryResponse updated = inventoryService.adjustStock(productId, request, principal);
        return ResponseEntity.ok(ApiResponse.success("Stock adjustment successfully applied", updated));
    }

    /**
     * Retrieves inventory levels for a specific product.
     */
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<InventoryResponse>> getInventoryByProductId(
            @PathVariable Long productId
    ) {
        InventoryResponse response = inventoryService.getInventoryByProductId(productId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Retrieves a paginated list of inventory levels across all catalog products.
     */
    @GetMapping({"", "/"})
    public ResponseEntity<ApiResponse<PageResponse<InventoryResponse>>> getAllInventory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<InventoryResponse> response = inventoryService.getAllInventory(page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Retrieves paginated audit ledger movement history for a specific product.
     * Ordered stably by timestamp DESC, id DESC.
     */
    @GetMapping("/{productId}/movements")
    public ResponseEntity<ApiResponse<PageResponse<InventoryMovementResponse>>> getMovementsByProductId(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<InventoryMovementResponse> response = inventoryService.getMovementsByProductId(productId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Retrieves paginated global audit ledger movements across all products.
     * Ordered stably by timestamp DESC, id DESC.
     */
    @GetMapping({"/movements", "/movements/"})
    public ResponseEntity<ApiResponse<PageResponse<InventoryMovementResponse>>> getAllMovements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<InventoryMovementResponse> response = inventoryService.getAllMovements(page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
