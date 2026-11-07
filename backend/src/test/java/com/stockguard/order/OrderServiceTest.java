package com.stockguard.order;

import com.stockguard.catalog.model.Category;
import com.stockguard.catalog.model.Product;
import com.stockguard.catalog.repository.CategoryRepository;
import com.stockguard.catalog.repository.ProductRepository;
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
import com.stockguard.order.exception.IdempotencyPayloadMismatchException;
import com.stockguard.order.model.IdempotencyRecord;
import com.stockguard.order.model.IdempotencyStatus;
import com.stockguard.order.model.Order;
import com.stockguard.order.model.OrderStatus;
import com.stockguard.order.repository.IdempotencyRecordRepository;
import com.stockguard.order.repository.OrderItemRepository;
import com.stockguard.order.repository.OrderRepository;
import com.stockguard.order.service.IdempotencyService;
import com.stockguard.order.service.OrderService;
import com.stockguard.security.UserPrincipal;
import com.stockguard.user.model.Role;
import com.stockguard.user.model.User;
import com.stockguard.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryMovementRepository movementRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Product productA;
    private Product productB;
    private User customerUser;
    private User customerUser2;
    private User adminUser;
    private UserPrincipal customerPrincipal;
    private UserPrincipal customerPrincipal2;
    private UserPrincipal adminPrincipal;

    @BeforeEach
    void setUp() {
        idempotencyRecordRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        movementRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        Category category = categoryRepository.save(new Category("Components", "components", "PC components"));

        productA = productRepository.save(new Product(
                category,
                "CPU-INTEL-14700",
                "Intel Core i7-14700",
                "High performance CPU",
                new BigDecimal("399.99"),
                "https://example.com/cpu.jpg",
                true
        ));

        productB = productRepository.save(new Product(
                category,
                "RAM-DDR5-32GB",
                "Corsair Vengeance 32GB",
                "DDR5 6000MHz RAM Kit",
                new BigDecimal("129.50"),
                "https://example.com/ram.jpg",
                true
        ));

        // Initial inventory: Product A has 10 units, Product B has 5 units
        inventoryRepository.save(new Inventory(productA, 10, 2));
        inventoryRepository.save(new Inventory(productB, 5, 2));

        customerUser = userRepository.save(new User("customer.order@stockguard.io", "hashedPass1", "Alice", "Customer", Role.CUSTOMER));
        customerUser2 = userRepository.save(new User("customer2.order@stockguard.io", "hashedPass2", "Bob", "Customer", Role.CUSTOMER));
        adminUser = userRepository.save(new User("admin.order@stockguard.io", "hashedPass3", "Admin", "User", Role.ADMIN));

        customerPrincipal = UserPrincipal.create(customerUser);
        customerPrincipal2 = UserPrincipal.create(customerUser2);
        adminPrincipal = UserPrincipal.create(adminUser);
    }

    @Test
    @DisplayName("Successfully creates customer order with accurate server-side price calculation and stock deduction")
    void testCreateOrder_Success_SingleItem() {
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(productA.getId(), 2)),
                "Please deliver in original packaging"
        );

        OrderResponse response = orderService.createOrder(request, customerPrincipal);

        assertNotNull(response);
        assertNotNull(response.getId());
        assertNotNull(response.getOrderNumber());
        assertTrue(response.getOrderNumber().startsWith("ORD-"));
        assertEquals(customerUser.getId(), response.getCustomerId());
        assertEquals(customerUser.getEmail(), response.getCustomerEmail());
        assertEquals(OrderStatus.CONFIRMED, response.getStatus());
        assertEquals("USD", response.getCurrency());
        assertEquals("Please deliver in original packaging", response.getNotes());

        // Price snapshot calculation: 2 * 399.99 = 799.98
        assertEquals(new BigDecimal("799.98"), response.getTotalAmount());
        assertEquals(1, response.getItems().size());
        assertEquals(productA.getId(), response.getItems().get(0).getProductId());
        assertEquals(2, response.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("399.99"), response.getItems().get(0).getUnitPrice());
        assertEquals(new BigDecimal("799.98"), response.getItems().get(0).getSubtotal());

        // Verify inventory deducted from 10 to 8
        Inventory updatedInventory = inventoryRepository.findByProductId(productA.getId()).orElseThrow();
        assertEquals(8, updatedInventory.getAvailableStock());

        // Verify audit movement ledger
        List<InventoryMovement> movements = movementRepository.findAll();
        assertEquals(1, movements.size());
        InventoryMovement movement = movements.get(0);
        assertEquals(productA.getId(), movement.getProduct().getId());
        assertEquals(-2, movement.getQuantityChange());
        assertEquals(10, movement.getPreviousStock());
        assertEquals(8, movement.getNewStock());
        assertEquals(MovementType.ORDER_PURCHASE, movement.getMovementType());
        assertEquals(response.getOrderNumber(), movement.getReferenceId());
        assertEquals(customerUser.getId(), movement.getActor().getId());
    }

    @Test
    @DisplayName("Historical price snapshot remains immutable even if product catalog price is subsequently modified")
    void testPriceSnapshotImmutability() {
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(productA.getId(), 1)),
                null
        );

        OrderResponse response = orderService.createOrder(request, customerPrincipal);
        assertEquals(new BigDecimal("399.99"), response.getTotalAmount());
        assertEquals(new BigDecimal("399.99"), response.getItems().get(0).getUnitPrice());

        // Later catalog price increase to $599.99
        productA.setPrice(new BigDecimal("599.99"));
        productRepository.save(productA);

        // Verify existing order and item snapshots remain unchanged
        Order persistedOrder = orderRepository.findWithDetailsById(response.getId()).orElseThrow();
        assertEquals(new BigDecimal("399.99"), persistedOrder.getTotalAmount());
        assertEquals(new BigDecimal("399.99"), persistedOrder.getItems().get(0).getUnitPrice());
        assertEquals(new BigDecimal("399.99"), persistedOrder.getItems().get(0).getSubtotal());
    }

    @Test
    @DisplayName("Successfully creates multi-item order with cumulative server-side total calculation")
    void testCreateOrder_Success_MultipleItems() {
        // Product A: 2 * 399.99 = 799.98
        // Product B: 3 * 129.50 = 388.50
        // Expected total = 1188.48
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(
                        new OrderItemRequest(productA.getId(), 2),
                        new OrderItemRequest(productB.getId(), 3)
                ),
                "Combined items order"
        );

        OrderResponse response = orderService.createOrder(request, customerPrincipal);

        assertEquals(new BigDecimal("1188.48"), response.getTotalAmount());
        assertEquals(2, response.getItems().size());

        // Verify stock deductions
        Inventory invA = inventoryRepository.findByProductId(productA.getId()).orElseThrow();
        Inventory invB = inventoryRepository.findByProductId(productB.getId()).orElseThrow();
        assertEquals(8, invA.getAvailableStock()); // 10 - 2
        assertEquals(2, invB.getAvailableStock()); // 5 - 3

        // Verify 2 ledger movements created
        List<InventoryMovement> movements = movementRepository.findAll();
        assertEquals(2, movements.size());
    }

    @Test
    @DisplayName("Atomic rollback: if any item fails stock deduction, entire order and all stock/ledger changes are rolled back")
    void testCreateOrder_RollbackOnInsufficientStock() {
        // Product A has 10 units (request 2 -> valid)
        // Product B has 5 units (request 10 -> INSUFFICIENT)
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(
                        new OrderItemRequest(productA.getId(), 2),
                        new OrderItemRequest(productB.getId(), 10)
                ),
                null
        );

        assertThrows(InsufficientStockException.class, () -> orderService.createOrder(request, customerPrincipal));

        // Verify entire order was rolled back
        assertEquals(0, orderRepository.count());
        assertEquals(0, orderItemRepository.count());

        // Verify Product A stock was NOT deducted (still 10)
        Inventory invA = inventoryRepository.findByProductId(productA.getId()).orElseThrow();
        assertEquals(10, invA.getAvailableStock());

        // Verify Product B stock remains 5
        Inventory invB = inventoryRepository.findByProductId(productB.getId()).orElseThrow();
        assertEquals(5, invB.getAvailableStock());

        // Verify zero audit movements persisted
        assertEquals(0, movementRepository.count());
    }

    @Test
    @DisplayName("Order creation rejected when product is marked inactive")
    void testCreateOrder_RejectsInactiveProduct() {
        productB.setActive(false);
        productRepository.save(productB);

        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(productB.getId(), 1)),
                null
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.createOrder(request, customerPrincipal));
        assertTrue(ex.getMessage().contains("not active"));

        assertEquals(0, orderRepository.count());
        assertEquals(5, inventoryRepository.findByProductId(productB.getId()).orElseThrow().getAvailableStock());
    }

    @Test
    @DisplayName("Order creation rejected when product does not exist")
    void testCreateOrder_RejectsNonExistentProduct() {
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(99999L, 1)),
                null
        );

        assertThrows(ResourceNotFoundException.class, () -> orderService.createOrder(request, customerPrincipal));
        assertEquals(0, orderRepository.count());
    }

    @Test
    @DisplayName("Order creation rejected when duplicate product IDs are provided in items list")
    void testCreateOrder_RejectsDuplicateProductIds() {
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(
                        new OrderItemRequest(productA.getId(), 1),
                        new OrderItemRequest(productA.getId(), 2)
                ),
                null
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.createOrder(request, customerPrincipal));
        assertTrue(ex.getMessage().contains("Duplicate product ID"));

        assertEquals(0, orderRepository.count());
    }

    @Test
    @DisplayName("Order creation rejected if customer account is deactivated")
    void testCreateOrder_RejectsInactiveCustomer() {
        customerUser.setActive(false);
        userRepository.save(customerUser);

        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(productA.getId(), 1)),
                null
        );

        assertThrows(AccountDisabledException.class, () -> orderService.createOrder(request, customerPrincipal));
        assertEquals(0, orderRepository.count());
    }

    @Test
    @DisplayName("Customer cannot access another customer's order, but can access their own; admin can access any order")
    void testGetOrderById_OwnershipEnforcement() {
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(productA.getId(), 1)),
                "Alice's secret order"
        );

        OrderResponse createdOrder = orderService.createOrder(request, customerPrincipal);

        // 1. Owner (Alice) can access her order
        OrderResponse aliceView = orderService.getOrderById(createdOrder.getId(), customerPrincipal);
        assertEquals(createdOrder.getId(), aliceView.getId());

        // 2. Bob cannot access Alice's order (throws ResourceNotFoundException)
        assertThrows(ResourceNotFoundException.class,
                () -> orderService.getOrderById(createdOrder.getId(), customerPrincipal2));

        // 3. Admin can access Alice's order
        OrderResponse adminView = orderService.getOrderById(createdOrder.getId(), adminPrincipal);
        assertEquals(createdOrder.getId(), adminView.getId());
    }

    @Test
    @DisplayName("getCustomerOrders strictly scopes orders to the authenticated customer with pagination")
    void testGetCustomerOrders_OwnershipAndPagination() {
        OrderCreateRequest req1 = new OrderCreateRequest(
                List.of(new OrderItemRequest(productA.getId(), 1)),
                "Alice Order 1"
        );
        OrderCreateRequest req2 = new OrderCreateRequest(
                List.of(new OrderItemRequest(productA.getId(), 1)),
                "Alice Order 2"
        );
        OrderCreateRequest reqBob = new OrderCreateRequest(
                List.of(new OrderItemRequest(productB.getId(), 1)),
                "Bob Order 1"
        );

        OrderResponse aliceOrd1 = orderService.createOrder(req1, customerPrincipal);
        OrderResponse aliceOrd2 = orderService.createOrder(req2, customerPrincipal);
        OrderResponse bobOrd = orderService.createOrder(reqBob, customerPrincipal2);

        // Alice orders
        var alicePage = orderService.getCustomerOrders(0, 10, customerPrincipal);
        assertEquals(2, alicePage.getTotalElements());
        assertEquals(2, alicePage.getContent().size());
        assertTrue(alicePage.getContent().stream().allMatch(o -> o.getCustomerId().equals(customerUser.getId())));
        // Sorted desc by createdAt: second order should be first in content
        assertEquals(aliceOrd2.getId(), alicePage.getContent().get(0).getId());
        assertEquals(aliceOrd1.getId(), alicePage.getContent().get(1).getId());

        // Bob orders
        var bobPage = orderService.getCustomerOrders(0, 10, customerPrincipal2);
        assertEquals(1, bobPage.getTotalElements());
        assertEquals(bobOrd.getId(), bobPage.getContent().get(0).getId());

        // Null principal rejection
        assertThrows(IllegalArgumentException.class, () -> orderService.getCustomerOrders(0, 10, null));
    }

    @Test
    @DisplayName("IdempotencyService computeRequestHash is deterministic regardless of item insertion order")
    void testIdempotencyService_ComputeRequestHash_Deterministic() {
        OrderCreateRequest req1 = new OrderCreateRequest(
                List.of(new OrderItemRequest(1L, 2), new OrderItemRequest(2L, 5)),
                "Expedited shipping"
        );
        OrderCreateRequest req2 = new OrderCreateRequest(
                List.of(new OrderItemRequest(2L, 5), new OrderItemRequest(1L, 2)),
                "Expedited shipping"
        );

        String hash1 = idempotencyService.computeRequestHash(req1);
        String hash2 = idempotencyService.computeRequestHash(req2);

        assertNotNull(hash1);
        assertEquals(hash1, hash2, "Hashing sorted items must produce identical fingerprint regardless of list ordering");

        // Different notes should produce different hash
        OrderCreateRequest reqDifferentNotes = new OrderCreateRequest(
                List.of(new OrderItemRequest(1L, 2), new OrderItemRequest(2L, 5)),
                "Standard shipping"
        );
        assertNotEquals(hash1, idempotencyService.computeRequestHash(reqDifferentNotes));
    }

    @Test
    @DisplayName("IdempotencyService validateKey validates non-blank and length constraints")
    void testIdempotencyService_ValidateKey_Constraints() {
        // Valid keys
        assertDoesNotThrow(() -> idempotencyService.validateKey("uuid-key-12345"));
        assertDoesNotThrow(() -> idempotencyService.validateKey(null)); // Optional header

        // Blank key
        assertThrows(IllegalArgumentException.class, () -> idempotencyService.validateKey("   "));

        // Excessively long key (> 255 chars)
        String over255 = "a".repeat(256);
        assertThrows(IllegalArgumentException.class, () -> idempotencyService.validateKey(over255));
    }

    @Test
    @DisplayName("OrderService with idempotency key: replaying returns cached response without new order or stock deduction")
    void testCreateOrder_WithIdempotencyKey_RepeatedCallReturnsCached() {
        String key = "test-order-service-key-100";
        OrderCreateRequest req = new OrderCreateRequest(
                List.of(new OrderItemRequest(productA.getId(), 2)),
                "Idempotent service test"
        );

        // 1. Initial creation
        OrderResponse initialResp = orderService.createOrder(req, customerPrincipal, key);
        assertNotNull(initialResp);
        assertEquals(1, orderRepository.count());
        assertEquals(1, movementRepository.count());

        Inventory invAfter1 = inventoryRepository.findByProductId(productA.getId()).orElseThrow();
        assertEquals(8, invAfter1.getAvailableStock()); // 10 - 2 = 8

        // 2. Replay with identical key
        OrderResponse replayResp = orderService.createOrder(req, customerPrincipal, key);
        assertNotNull(replayResp);
        assertEquals(initialResp.getId(), replayResp.getId());
        assertEquals(initialResp.getOrderNumber(), replayResp.getOrderNumber());
        assertEquals(initialResp.getTotalAmount(), replayResp.getTotalAmount());

        // Still exactly 1 order, 1 movement, stock remains 8
        assertEquals(1, orderRepository.count());
        assertEquals(1, movementRepository.count());
        Inventory invAfter2 = inventoryRepository.findByProductId(productA.getId()).orElseThrow();
        assertEquals(8, invAfter2.getAvailableStock());
    }

    @Test
    @DisplayName("OrderService with idempotency key: reusing key with different payload throws IdempotencyPayloadMismatchException")
    void testCreateOrder_WithIdempotencyKey_PayloadMismatchThrows() {
        String key = "test-order-service-key-mismatch";
        OrderCreateRequest req1 = new OrderCreateRequest(
                List.of(new OrderItemRequest(productA.getId(), 1)),
                "Initial note"
        );
        OrderCreateRequest req2 = new OrderCreateRequest(
                List.of(new OrderItemRequest(productA.getId(), 2)), // Different quantity
                "Initial note"
        );

        orderService.createOrder(req1, customerPrincipal, key);

        assertThrows(IdempotencyPayloadMismatchException.class, () ->
                orderService.createOrder(req2, customerPrincipal, key)
        );

        // Order count stays 1
        assertEquals(1, orderRepository.count());
    }

    @Test
    @DisplayName("Regression: Delayed original request in gap between reserveKey and executeOrderPlacement returns completed order without duplicate deduction")
    void testCreateOrder_StalledGapRecovery_LateRequestReturnsExistingOrderWithoutDuplicateDeduction() {
        String key = "test-gap-race-key-100";
        OrderCreateRequest req = new OrderCreateRequest(
                List.of(new OrderItemRequest(productA.getId(), 2)),
                "Gap race condition test"
        );
        String requestHash = idempotencyService.computeRequestHash(req);

        // 1. Thread 1 calls reserveKey at T0
        var initialReserve = idempotencyService.reserveKey(key, customerUser, requestHash);
        assertTrue(initialReserve.isEmpty(), "First reservation must succeed with empty cache");

        // 2. Simulate Thread 1 pausing in the gap while > 2 minutes pass (stalled timeout reached)
        IdempotencyRecord record = idempotencyRecordRepository.findByIdempotencyKey(key).orElseThrow();
        record.setCreatedAt(Instant.now().minus(3, ChronoUnit.MINUTES));
        idempotencyRecordRepository.saveAndFlush(record);

        // 3. Thread 2 (customer retry) arrives while Thread 1 is still alive in the gap:
        //    Thread 2 recovers the stalled PROCESSING record, places Order 2, and marks COMPLETED.
        OrderResponse resp2 = orderService.createOrder(req, customerPrincipal, key);
        assertNotNull(resp2);
        assertEquals(1, orderRepository.count(), "Thread 2 must create exactly 1 order");
        assertEquals(1, movementRepository.count(), "Thread 2 must record 1 audit movement");
        Inventory invAfterThread2 = inventoryRepository.findByProductId(productA.getId()).orElseThrow();
        assertEquals(8, invAfterThread2.getAvailableStock(), "Stock should be 10 - 2 = 8");

        // 4. Thread 1 finally enters executeOrderPlacement after the delay:
        //    lockActiveRecord must detect the record is already COMPLETED and return the existing OrderResponse.
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        OrderResponse resp1 = txTemplate.execute(status ->
                orderService.executeOrderPlacement(req, customerUser, key)
        );

        assertNotNull(resp1);
        assertEquals(resp2.getId(), resp1.getId(), "Thread 1 must receive the same order ID created by Thread 2");
        assertEquals(resp2.getOrderNumber(), resp1.getOrderNumber(), "Thread 1 must receive the same order number");
        assertEquals(resp2.getTotalAmount(), resp1.getTotalAmount(), "Totals must match");

        // 5. Assert NO duplicate order and NO double inventory deduction occurred
        assertEquals(1, orderRepository.count(), "Database must STILL contain exactly 1 order");
        assertEquals(1, movementRepository.count(), "Ledger must STILL contain exactly 1 movement");
        Inventory invFinal = inventoryRepository.findByProductId(productA.getId()).orElseThrow();
        assertEquals(8, invFinal.getAvailableStock(), "Stock must remain 8 (NOT deducted twice to 6)");
    }

    @Test
    @DisplayName("Regression: Calling markFailed on an already COMPLETED record does not corrupt it to FAILED")
    void testMarkFailed_DoesNotCorruptCompletedRecord() {
        String key = "test-mark-failed-completed-key";
        OrderCreateRequest req = new OrderCreateRequest(
                List.of(new OrderItemRequest(productA.getId(), 1)),
                "Mark failed safety test"
        );

        OrderResponse resp = orderService.createOrder(req, customerPrincipal, key);
        assertNotNull(resp);

        IdempotencyRecord record = idempotencyRecordRepository.findByIdempotencyKey(key).orElseThrow();
        assertEquals(IdempotencyStatus.COMPLETED, record.getStatus());

        // Calling markFailed (e.g. from an aborted concurrent transaction) must be a no-op
        idempotencyService.markFailed(key);

        IdempotencyRecord afterRecord = idempotencyRecordRepository.findByIdempotencyKey(key).orElseThrow();
        assertEquals(IdempotencyStatus.COMPLETED, afterRecord.getStatus(), "Completed status must never be downgraded to FAILED");
    }
}
