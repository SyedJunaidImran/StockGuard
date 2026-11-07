package com.stockguard.inventory.service;

import com.stockguard.catalog.model.Product;
import com.stockguard.catalog.repository.ProductRepository;
import com.stockguard.common.dto.PageResponse;
import com.stockguard.common.exception.ResourceNotFoundException;
import com.stockguard.inventory.dto.InventoryMovementResponse;
import com.stockguard.inventory.dto.InventoryResponse;
import com.stockguard.inventory.dto.StockAdjustmentRequest;
import com.stockguard.inventory.model.AdjustmentType;
import com.stockguard.inventory.model.Inventory;
import com.stockguard.inventory.model.InventoryMovement;
import com.stockguard.inventory.model.MovementType;
import com.stockguard.inventory.repository.InventoryMovementRepository;
import com.stockguard.inventory.repository.InventoryRepository;
import com.stockguard.security.UserPrincipal;
import com.stockguard.user.model.User;
import com.stockguard.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service orchestrating inventory adjustments, concurrency protection,
 * and immutable audit ledger persistence.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public InventoryService(InventoryRepository inventoryRepository,
                            InventoryMovementRepository movementRepository,
                            ProductRepository productRepository,
                            UserRepository userRepository) {
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    /**
     * Executes an administrative stock adjustment under a pessimistic write lock.
     * Records both the stock modification and audit ledger entry atomically.
     *
     * @param productId      Target product ID
     * @param request        Validated adjustment request (positive quantity, direction, reason)
     * @param actorPrincipal Authenticated security principal acting as audit attribution
     * @return Updated InventoryResponse
     */
    @Transactional
    public InventoryResponse adjustStock(Long productId, StockAdjustmentRequest request, UserPrincipal actorPrincipal) {
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Adjustment quantity must be greater than zero");
        }
        if (request.getType() == null) {
            throw new IllegalArgumentException("Adjustment type is required (ADD or REMOVE)");
        }
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Adjustment reason cannot be blank");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        User actor = null;
        if (actorPrincipal != null && actorPrincipal.getId() != null) {
            actor = userRepository.findById(actorPrincipal.getId()).orElse(null);
        }

        // 1. Acquire pessimistic write lock (SELECT ... FOR UPDATE) to serialize concurrent adjustments
        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseGet(() -> {
                    log.info("Initializing inventory for product #{}: SKU '{}'", product.getId(), product.getSku());
                    Inventory initial = new Inventory(product, 0, 10);
                    return inventoryRepository.saveAndFlush(initial);
                });

        int previousStock = inventory.getAvailableStock();
        int quantity = request.getQuantity();
        int delta;
        MovementType movementType;

        if (request.getType() == AdjustmentType.ADD) {
            inventory.addStock(quantity);
            delta = quantity;
            movementType = MovementType.RESTOCK;
        } else {
            // Checks for sufficient stock and throws InsufficientStockException if insufficient
            inventory.removeStock(quantity);
            delta = -quantity;
            movementType = MovementType.MANUAL_ADJUSTMENT;
        }

        int newStock = inventory.getAvailableStock();

        // 2. Persist inventory state update
        Inventory updatedInventory = inventoryRepository.save(inventory);

        // 3. Write immutable audit ledger entry in the same transaction
        InventoryMovement movement = new InventoryMovement(
                updatedInventory,
                product,
                delta,
                previousStock,
                newStock,
                movementType,
                request.getReason().trim(),
                request.getReferenceId() != null ? request.getReferenceId().trim() : null,
                actor
        );
        movementRepository.save(movement);

        log.info("Stock adjustment completed for product #{} ({}): {} {} units ({} -> {}). Actor: {}",
                product.getId(), product.getSku(), request.getType(), quantity, previousStock, newStock,
                actor != null ? actor.getEmail() : "SYSTEM");

        return InventoryResponse.fromEntity(updatedInventory);
    }

    /**
     * Retrieves inventory details for a specific product.
     * Initializes a zero-stock inventory row if none exists yet.
     */
    @Transactional
    public InventoryResponse getInventoryByProductId(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseGet(() -> {
                    log.info("Auto-initializing inventory record for product #{}", product.getId());
                    Inventory initial = new Inventory(product, 0, 10);
                    return inventoryRepository.save(initial);
                });

        return InventoryResponse.fromEntity(inventory);
    }

    /**
     * Safe, idempotent initialization of inventory records for all catalog products
     * that currently do not possess an inventory record.
     * Preserves existing stock quantities and never creates duplicates.
     */
    @Transactional
    public void ensureInventoryExistsForAllProducts() {
        List<Product> missingProducts = inventoryRepository.findProductsWithoutInventory();
        if (missingProducts.isEmpty()) {
            return;
        }

        log.info("Found {} catalog product(s) missing inventory records. Initializing baseline zero-stock records...", missingProducts.size());
        for (Product product : missingProducts) {
            if (!inventoryRepository.existsByProductId(product.getId())) {
                Inventory initial = new Inventory(product, 0, 10);
                inventoryRepository.save(initial);
                log.info("Initialized inventory record for product #{} (SKU: '{}', Name: '{}')",
                        product.getId(), product.getSku(), product.getName());
            }
        }
    }

    /**
     * Retrieves paginated inventory records across products.
     * Self-heals any catalog products missing inventory rows prior to pagination.
     */
    @Transactional
    public PageResponse<InventoryResponse> getAllInventory(int page, int size) {
        ensureInventoryExistsForAllProducts();
        int validatedSize = clampPageSize(size);
        Pageable pageable = PageRequest.of(page, validatedSize, Sort.by(Sort.Direction.ASC, "product.name"));
        Page<Inventory> inventoryPage = inventoryRepository.findAllWithProduct(pageable);
        return PageResponse.from(inventoryPage, InventoryResponse::fromEntity);
    }

    /**
     * Retrieves paginated movement audit ledger history for a specific product.
     * Guaranteed stable ordering by createdAt DESC, id DESC.
     */
    @Transactional(readOnly = true)
    public PageResponse<InventoryMovementResponse> getMovementsByProductId(Long productId, int page, int size) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        }

        int validatedSize = clampPageSize(size);
        Pageable pageable = PageRequest.of(
                page,
                validatedSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );

        Page<InventoryMovement> movementsPage = movementRepository.findByProductId(productId, pageable);
        return PageResponse.from(movementsPage, InventoryMovementResponse::fromEntity);
    }

    /**
     * Retrieves paginated global movement audit ledger across all products.
     * Guaranteed stable ordering by createdAt DESC, id DESC.
     */
    @Transactional(readOnly = true)
    public PageResponse<InventoryMovementResponse> getAllMovements(int page, int size) {
        int validatedSize = clampPageSize(size);
        Pageable pageable = PageRequest.of(
                page,
                validatedSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );

        Page<InventoryMovement> movementsPage = movementRepository.findAllWithDetails(pageable);
        return PageResponse.from(movementsPage, InventoryMovementResponse::fromEntity);
    }

    /**
     * Initializes an inventory row for a newly created product.
     */
    @Transactional
    public Inventory initializeInventoryForProduct(Product product) {
        if (inventoryRepository.existsByProductId(product.getId())) {
            return inventoryRepository.findByProductId(product.getId()).orElseThrow();
        }

        Inventory inventory = new Inventory(product, 0, 10);
        Inventory saved = inventoryRepository.save(inventory);
        log.info("Initialized inventory record for product #{} (SKU: {}) with 0 stock", product.getId(), product.getSku());
        return saved;
    }

    private int clampPageSize(int size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
