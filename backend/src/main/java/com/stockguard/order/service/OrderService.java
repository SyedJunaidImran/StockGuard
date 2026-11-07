package com.stockguard.order.service;

import com.stockguard.catalog.model.Product;
import com.stockguard.catalog.repository.ProductRepository;
import com.stockguard.common.dto.PageResponse;
import com.stockguard.common.exception.AccountDisabledException;
import com.stockguard.common.exception.ResourceNotFoundException;
import com.stockguard.inventory.exception.InsufficientStockException;
import com.stockguard.inventory.model.Inventory;
import com.stockguard.inventory.model.InventoryMovement;
import com.stockguard.inventory.model.MovementType;
import com.stockguard.inventory.repository.InventoryMovementRepository;
import com.stockguard.inventory.repository.InventoryRepository;
import com.stockguard.order.dto.OrderCreateRequest;
import com.stockguard.order.dto.OrderItemRequest;
import com.stockguard.order.dto.OrderResponse;
import com.stockguard.order.model.Order;
import com.stockguard.order.model.OrderItem;
import com.stockguard.order.model.OrderStatus;
import com.stockguard.order.repository.OrderRepository;
import com.stockguard.security.UserPrincipal;
import com.stockguard.user.model.Role;
import com.stockguard.user.model.User;
import com.stockguard.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service orchestrating customer order creation, concurrency-safe inventory deduction,
 * pricing snapshot calculations, and immutable audit ledger tracking.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final UserRepository userRepository;
    private final IdempotencyService idempotencyService;
    private final TransactionTemplate transactionTemplate;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        InventoryRepository inventoryRepository,
                        InventoryMovementRepository movementRepository,
                        UserRepository userRepository,
                        IdempotencyService idempotencyService,
                        PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.userRepository = userRepository;
        this.idempotencyService = idempotencyService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Creates a customer order atomically without an idempotency key.
     */
    public OrderResponse createOrder(OrderCreateRequest request, UserPrincipal customerPrincipal) {
        return createOrder(request, customerPrincipal, null);
    }

    /**
     * Creates a customer order atomically with optional idempotency-key deduplication:
     * 1. Validates authenticated customer account.
     * 2. Checks/reserves idempotency key under a dedicated short-lived transaction.
     * 3. Sorts items by product ID to prevent database deadlocks across concurrent multi-item transactions.
     * 4. Locks inventory rows using pessimistic write locks (SELECT ... FOR UPDATE).
     * 5. Validates product existence, active status, and stock sufficiency.
     * 6. Atomically deducts inventory and appends immutable audit ledger movements.
     * 7. Snapshots unit prices and calculates subtotals server-side (Half-Up rounding, 2 decimal places).
     * 8. Persists order and items. Marks idempotency record COMPLETED.
     *    If any item fails, rolls back all changes completely and marks idempotency record FAILED.
     *
     * @param request           Validated order request with items and optional notes
     * @param customerPrincipal Authenticated security principal
     * @param idempotencyKey    Optional client-supplied idempotency key
     * @return OrderResponse with full snapshot details
     */
    public OrderResponse createOrder(OrderCreateRequest request, UserPrincipal customerPrincipal, String idempotencyKey) {
        if (customerPrincipal == null || customerPrincipal.getId() == null) {
            throw new IllegalArgumentException("Authenticated customer is required to place an order");
        }

        User customer = userRepository.findById(customerPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer account not found with id: " + customerPrincipal.getId()));

        if (!customer.isActive()) {
            throw new AccountDisabledException("Your account is deactivated. Please contact support.");
        }

        // Idempotency deduplication check: runs outside the order placement transaction
        // to avoid nested transactions and Hikari connection pool exhaustion.
        if (idempotencyKey != null) {
            idempotencyService.validateKey(idempotencyKey);
            String requestHash = idempotencyService.computeRequestHash(request);
            Optional<OrderResponse> cached = idempotencyService.reserveKey(idempotencyKey, customer, requestHash);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        try {
            return transactionTemplate.execute(status -> executeOrderPlacement(request, customer, idempotencyKey));
        } catch (Exception ex) {
            if (idempotencyKey != null) {
                idempotencyService.markFailed(idempotencyKey);
            }
            throw ex;
        }
    }

    public OrderResponse executeOrderPlacement(OrderCreateRequest request, User customer) {
        return executeOrderPlacement(request, customer, null);
    }

    public OrderResponse executeOrderPlacement(OrderCreateRequest request, User customer, String idempotencyKey) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        // Lock idempotency record under this active transaction so no concurrent retry or
        // timeout-based stalled recovery can take over while this order is actively being placed.
        // If a concurrent request or recovery attempt already completed the order, return the cached snapshot.
        if (idempotencyKey != null) {
            Optional<OrderResponse> existing = idempotencyService.lockActiveRecord(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Order for idempotency key [{}] was already completed by another transaction. Returning existing order #{}",
                        idempotencyKey, existing.get().getOrderNumber());
                return existing.get();
            }
        }

        // Validate uniqueness of product IDs in request to prevent duplicate processing or ambiguous locks
        Set<Long> seenProductIds = new HashSet<>();
        for (OrderItemRequest itemReq : request.getItems()) {
            if (itemReq.getProductId() == null) {
                throw new IllegalArgumentException("Product ID cannot be null");
            }
            if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than zero for product id: " + itemReq.getProductId());
            }
            if (!seenProductIds.add(itemReq.getProductId())) {
                throw new IllegalArgumentException("Duplicate product ID in order request: " + itemReq.getProductId()
                        + ". Consolidate item quantities before submitting.");
            }
        }

        // Generate unique order number
        String orderNumber = generateUniqueOrderNumber();

        // Sort items by product ID ascending to ensure a consistent lock acquisition order across transactions,
        // completely preventing database deadlocks under concurrent multi-item orders.
        List<OrderItemRequest> sortedItems = request.getItems().stream()
                .sorted(Comparator.comparing(OrderItemRequest::getProductId))
                .toList();

        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setCustomer(customer);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setCurrency("USD");
        order.setNotes(request.getNotes() != null ? request.getNotes().trim() : null);

        BigDecimal totalAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (OrderItemRequest itemReq : sortedItems) {
            Long productId = itemReq.getProductId();
            int requestedQuantity = itemReq.getQuantity();

            // 1. Validate product existence and active status
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

            if (!product.isActive()) {
                throw new IllegalArgumentException("Product '" + product.getName() + "' (SKU: " + product.getSku()
                        + ") is not active and cannot be ordered");
            }

            // 2. Concurrency-safe stock locking: acquire pessimistic write lock (SELECT ... FOR UPDATE)
            Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                    .orElseThrow(() -> new InsufficientStockException("Inventory record not found for product: " + product.getSku()));

            int previousStock = inventory.getAvailableStock();

            // 3. Deduct stock (throws InsufficientStockException if requestedQuantity > availableStock)
            inventory.removeStock(requestedQuantity);
            int newStock = inventory.getAvailableStock();
            inventoryRepository.save(inventory);

            // 4. Record immutable inventory audit ledger movement
            InventoryMovement movement = new InventoryMovement(
                    inventory,
                    product,
                    -requestedQuantity,
                    previousStock,
                    newStock,
                    MovementType.ORDER_PURCHASE,
                    "Customer order purchase",
                    orderNumber,
                    customer
            );
            movementRepository.save(movement);

            // 5. Calculate snapshot price and subtotal strictly on the server
            BigDecimal unitPrice = product.getPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(requestedQuantity)).setScale(2, RoundingMode.HALF_UP);
            totalAmount = totalAmount.add(subtotal);

            // 6. Create line item snapshot
            OrderItem orderItem = new OrderItem(order, product, requestedQuantity, unitPrice, subtotal);
            order.addItem(orderItem);

            log.info("Order [{}] - Allocated {} units of product #{} ('{}') at ${}. Stock: {} -> {}",
                    orderNumber, requestedQuantity, productId, product.getName(), unitPrice, previousStock, newStock);
        }

        order.setTotalAmount(totalAmount);
        Order savedOrder = orderRepository.save(order);

        OrderResponse response = OrderResponse.fromEntity(savedOrder);
        if (idempotencyKey != null) {
            idempotencyService.markCompleted(idempotencyKey, savedOrder, response);
        }

        log.info("Order successfully placed: [{}] for customer '{}' with {} items. Total: ${}",
                orderNumber, customer.getEmail(), savedOrder.getItems().size(), savedOrder.getTotalAmount());

        return response;
    }

    /**
     * Retrieves an order by ID ensuring customer ownership or administrative privileges.
     *
     * @param orderId   Target order ID
     * @param principal Authenticated principal
     * @return OrderResponse
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId, UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }

        Order order;
        if (principal.getRole() == Role.ADMIN) {
            order = orderRepository.findWithDetailsById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        } else {
            order = orderRepository.findByIdAndCustomerId(orderId, principal.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        }

        return OrderResponse.fromEntity(order);
    }

    /**
     * Retrieves paginated order history strictly for the authenticated customer.
     * Orders are sorted by creation date descending.
     *
     * @param page      0-based page index
     * @param size      Page size (bounded between 1 and 100)
     * @param principal Authenticated customer security principal
     * @return Paginated order responses
     */
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getCustomerOrders(int page, int size, UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new IllegalArgumentException("Authenticated customer is required to query orders");
        }

        int validatedPage = Math.max(0, page);
        int validatedSize = (size <= 0 || size > 100) ? 10 : size;
        Pageable pageable = PageRequest.of(validatedPage, validatedSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Order> ordersPage = orderRepository.findByCustomerId(principal.getId(), pageable);
        return PageResponse.from(ordersPage, OrderResponse::fromEntity);
    }

    private String generateUniqueOrderNumber() {
        String orderNumber;
        do {
            orderNumber = "ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        } while (orderRepository.existsByOrderNumber(orderNumber));
        return orderNumber;
    }
}
