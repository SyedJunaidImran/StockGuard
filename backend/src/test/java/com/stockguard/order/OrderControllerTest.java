package com.stockguard.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockguard.catalog.model.Category;
import com.stockguard.catalog.model.Product;
import com.stockguard.catalog.repository.CategoryRepository;
import com.stockguard.catalog.repository.ProductRepository;
import com.stockguard.inventory.model.Inventory;
import com.stockguard.inventory.repository.InventoryMovementRepository;
import com.stockguard.inventory.repository.InventoryRepository;
import com.stockguard.order.dto.OrderCreateRequest;
import com.stockguard.order.dto.OrderItemRequest;
import com.stockguard.order.model.IdempotencyRecord;
import com.stockguard.order.model.IdempotencyStatus;
import com.stockguard.order.repository.IdempotencyRecordRepository;
import com.stockguard.order.repository.OrderItemRepository;
import com.stockguard.order.repository.OrderRepository;
import com.stockguard.order.service.IdempotencyService;
import com.stockguard.security.jwt.JwtTokenProvider;
import com.stockguard.user.model.Role;
import com.stockguard.user.model.User;
import com.stockguard.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryMovementRepository movementRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private String customerToken;
    private String customerToken2;
    private String adminToken;
    private User customer1;
    private User customer2;
    private Product testProduct;

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

        // 1. Create Customer 1
        customer1 = userRepository.save(new User(
                "customer1.api@stockguard.io",
                passwordEncoder.encode("CustPass123!"),
                "Cust1", "Api", Role.CUSTOMER
        ));
        customerToken = jwtTokenProvider.generateTokenFromEmail(customer1.getEmail(), customer1.getRole(), customer1.getId());

        // 2. Create Customer 2
        customer2 = userRepository.save(new User(
                "customer2.api@stockguard.io",
                passwordEncoder.encode("CustPass123!"),
                "Cust2", "Api", Role.CUSTOMER
        ));
        customerToken2 = jwtTokenProvider.generateTokenFromEmail(customer2.getEmail(), customer2.getRole(), customer2.getId());

        // 3. Create Admin
        User admin = userRepository.save(new User(
                "admin.api@stockguard.io",
                passwordEncoder.encode("AdminPass123!"),
                "Admin", "Api", Role.ADMIN
        ));
        adminToken = jwtTokenProvider.generateTokenFromEmail(admin.getEmail(), admin.getRole(), admin.getId());

        // 4. Seed Category & Product
        Category category = categoryRepository.save(new Category("Graphics Cards", "gpus", "Dedicated GPUs"));
        testProduct = productRepository.save(new Product(
                category,
                "GPU-RTX-4080",
                "NVIDIA GeForce RTX 4080 16GB",
                "Ada Lovelace Architecture",
                new BigDecimal("1199.99"),
                "https://images.unsplash.com/gpu.jpg",
                true
        ));

        // 5. Initialize stock to 5 units
        inventoryRepository.save(new Inventory(testProduct, 5, 2));
    }

    @Test
    @DisplayName("CUSTOMER places order successfully; returns 201 Created with accurate totals and snapshot")
    void testCreateOrder_Success() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(testProduct.getId(), 2)),
                "Fragile, please handle with care"
        );

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.orderNumber", startsWith("ORD-")))
                .andExpect(jsonPath("$.data.customerId", is(customer1.getId().intValue())))
                .andExpect(jsonPath("$.data.customerEmail", is(customer1.getEmail())))
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")))
                .andExpect(jsonPath("$.data.totalAmount", is(2399.98)))
                .andExpect(jsonPath("$.data.currency", is("USD")))
                .andExpect(jsonPath("$.data.notes", is("Fragile, please handle with care")))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].productId", is(testProduct.getId().intValue())))
                .andExpect(jsonPath("$.data.items[0].productSku", is("GPU-RTX-4080")))
                .andExpect(jsonPath("$.data.items[0].quantity", is(2)))
                .andExpect(jsonPath("$.data.items[0].unitPrice", is(1199.99)))
                .andExpect(jsonPath("$.data.items[0].subtotal", is(2399.98)));

        // Inventory should be 5 - 2 = 3
        Inventory inv = inventoryRepository.findByProductId(testProduct.getId()).orElseThrow();
        assertEquals(3, inv.getAvailableStock());
    }

    @Test
    @DisplayName("Anonymous order placement request is rejected with 401 Unauthorized")
    void testCreateOrder_AnonymousRejected() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(testProduct.getId(), 1)),
                null
        );

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Admin order placement request is rejected with 403 Forbidden (guarded by ROLE_CUSTOMER)")
    void testCreateOrder_AdminRejected() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(testProduct.getId(), 1)),
                null
        );

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Rejects negative or zero quantity with 400 Bad Request")
    void testCreateOrder_InvalidQuantity() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(testProduct.getId(), 0)),
                null
        );

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Rejects empty items list with 400 Bad Request")
    void testCreateOrder_EmptyItems() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest(List.of(), null);

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Rejects order when quantity exceeds available stock with 400 Bad Request")
    void testCreateOrder_InsufficientStock() throws Exception {
        // Stock is 5, customer asks for 10
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(testProduct.getId(), 10)),
                null
        );

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Insufficient stock")));

        assertEquals(0, orderRepository.count());
    }

    @Test
    @DisplayName("Customer can fetch own order by ID; cannot access other customer's order (404 Not Found)")
    void testGetOrderById_CustomerOwnership() throws Exception {
        // 1. Customer 1 places order
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(testProduct.getId(), 1)),
                "Customer 1 order"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        long orderId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // 2. Customer 1 accesses their own order -> 200 OK
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is((int) orderId)))
                .andExpect(jsonPath("$.data.customerId", is(customer1.getId().intValue())));

        // 3. Customer 2 tries to access Customer 1's order -> 404 Not Found (ownership isolation)
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", "Bearer " + customerToken2))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CUSTOMER fetches own paginated order history; isolated per customer")
    void testCustomerOrderHistory_ReturnsOnlyCustomerOrders() throws Exception {
        // Customer 1 places 2 orders
        OrderCreateRequest req1 = new OrderCreateRequest(
                List.of(new OrderItemRequest(testProduct.getId(), 1)),
                "Order 1 from Customer 1"
        );
        OrderCreateRequest req2 = new OrderCreateRequest(
                List.of(new OrderItemRequest(testProduct.getId(), 1)),
                "Order 2 from Customer 1"
        );

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated());

        // Customer 2 places 1 order
        OrderCreateRequest reqCust2 = new OrderCreateRequest(
                List.of(new OrderItemRequest(testProduct.getId(), 1)),
                "Order from Customer 2"
        );
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqCust2)))
                .andExpect(status().isCreated());

        // Customer 1 queries order history: sees exactly 2 orders, none from Customer 2
        mockMvc.perform(get("/api/v1/orders?page=0&size=10")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalElements", is(2)))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].customerId", is(customer1.getId().intValue())))
                .andExpect(jsonPath("$.data.content[1].customerId", is(customer1.getId().intValue())));

        // Customer 2 queries order history: sees exactly 1 order, none from Customer 1
        mockMvc.perform(get("/api/v1/orders?page=0&size=10")
                        .header("Authorization", "Bearer " + customerToken2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalElements", is(1)))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].customerId", is(customer2.getId().intValue())))
                .andExpect(jsonPath("$.data.content[0].notes", is("Order from Customer 2")));
    }

    @Test
    @DisplayName("Anonymous query to order history is rejected with 401 Unauthorized")
    void testCustomerOrderHistory_AnonymousRejected() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Admin query to customer order history is rejected with 403 Forbidden")
    void testCustomerOrderHistory_AdminRejected() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Concurrent order attempts cannot oversell inventory: exactly 5 succeed, remainder fail")
    void testConcurrentOrders_CannotOversellStock() throws Exception {
        // testProduct currently has 5 units in stock
        int threadCount = 10;
        int successCount = 0;
        int failureCount = 0;

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Callable<Integer>> tasks = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                tasks.add(() -> {
                    OrderCreateRequest req = new OrderCreateRequest(
                            List.of(new OrderItemRequest(testProduct.getId(), 1)),
                            "Concurrent buy attempt"
                    );

                    MvcResult res = mockMvc.perform(post("/api/v1/orders")
                                    .header("Authorization", "Bearer " + customerToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andReturn();

                    return res.getResponse().getStatus();
                });
            }

            List<Future<Integer>> futures = executor.invokeAll(tasks);

            for (Future<Integer> f : futures) {
                int statusCode = f.get();
                if (statusCode == 201) {
                    successCount++;
                } else if (statusCode == 400) {
                    failureCount++;
                }
            }
        }

        // Exactly 5 orders succeeded, exactly 5 were rejected due to insufficient stock
        assertEquals(5, successCount, "Exactly 5 orders should succeed for 5 available stock items");
        assertEquals(5, failureCount, "Remaining 5 orders should fail with 400 Bad Request (InsufficientStock)");

        // Verify remaining available stock is exactly 0
        Inventory finalInventory = inventoryRepository.findByProductId(testProduct.getId()).orElseThrow();
        assertEquals(0, finalInventory.getAvailableStock(), "Available stock must be 0, never negative (no overselling)");

        // Verify exactly 5 orders in DB
        assertEquals(5, orderRepository.count(), "Exactly 5 orders must be persisted in database");

        // Verify exactly 5 audit movements recorded
        assertEquals(5, movementRepository.count(), "Exactly 5 ledger movements must be persisted");
    }

    @Test
    @DisplayName("Idempotent replay: identical request with same key returns cached order without deducting stock twice")
    void testCreateOrder_IdempotentReplay() throws Exception {
        String idempotencyKey = "key-replay-test-123";
        OrderCreateRequest req = new OrderCreateRequest(
                List.of(new OrderItemRequest(testProduct.getId(), 2)),
                "Original order request"
        );

        // 1. First order placement
        MvcResult firstResult = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalAmount").value(2399.98))
                .andReturn();

        String firstResponse = firstResult.getResponse().getContentAsString();
        String firstOrderNumber = objectMapper.readTree(firstResponse).path("data").path("orderNumber").asText();

        // 2. Replay identical request with same key
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNumber").value(firstOrderNumber))
                .andExpect(jsonPath("$.data.totalAmount").value(2399.98));

        // 3. Database assertions: exactly 1 order in database, exactly 1 audit movement
        assertEquals(1, orderRepository.count(), "Exactly 1 order must be created across replay attempts");
        assertEquals(1, movementRepository.count(), "Audit movement ledger must only record 1 purchase");

        // 4. Stock assertions: stock was 5, deducted 2 -> remains 3 (NOT 1)
        Inventory inv = inventoryRepository.findByProductId(testProduct.getId()).orElseThrow();
        assertEquals(3, inv.getAvailableStock(), "Available stock must be deducted only once");

        // 5. Idempotency record assertions
        IdempotencyRecord record = idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertEquals(IdempotencyStatus.COMPLETED, record.getStatus());
        assertEquals(firstOrderNumber, record.getOrder().getOrderNumber());
    }

    @Test
    @DisplayName("Idempotency payload mismatch: reusing key with different items returns 400 Bad Request")
    void testCreateOrder_IdempotencyPayloadMismatch() throws Exception {
        String idempotencyKey = "key-mismatch-test-456";
        OrderCreateRequest req1 = new OrderCreateRequest(
                List.of(new OrderItemRequest(testProduct.getId(), 1)),
                "Initial order"
        );

        // First request succeeds
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        // Reusing key with different quantity (2 instead of 1)
        OrderCreateRequest req2 = new OrderCreateRequest(
                List.of(new OrderItemRequest(testProduct.getId(), 2)),
                "Modified quantity order"
        );

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("different order payload")));

        // Still only 1 order and stock deducted once (5 - 1 = 4)
        assertEquals(1, orderRepository.count());
        Inventory inv = inventoryRepository.findByProductId(testProduct.getId()).orElseThrow();
        assertEquals(4, inv.getAvailableStock());
    }

    @Test
    @DisplayName("Idempotency cross-customer isolation: Customer 2 attempting to reuse Customer 1's key returns 409 Conflict")
    void testCreateOrder_IdempotencyCrossCustomerConflict() throws Exception {
        String idempotencyKey = "key-cross-customer-789";
        OrderCreateRequest req = new OrderCreateRequest(
                List.of(new OrderItemRequest(testProduct.getId(), 1)),
                "Customer 1 order"
        );

        // Customer 1 places order
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Customer 2 attempts to use same idempotency key
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken2)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already registered to another customer account")));

        // Only Customer 1's order was created
        assertEquals(1, orderRepository.count());
    }

    @Test
    @DisplayName("Idempotency retry after failure: failed order marks record FAILED and allows subsequent retry with same key")
    void testCreateOrder_RetryAfterFailure() throws Exception {
        String idempotencyKey = "key-retry-after-fail";
        // testProduct only has 5 units; requesting 10 will fail
        OrderCreateRequest req = new OrderCreateRequest(
                List.of(new OrderItemRequest(testProduct.getId(), 10)),
                "Large order"
        );

        // 1. Initial attempt fails due to insufficient stock
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Insufficient stock")));

        // Verify status marked FAILED in idempotency table
        IdempotencyRecord failedRecord = idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertEquals(IdempotencyStatus.FAILED, failedRecord.getStatus());
        assertEquals(0, orderRepository.count());

        // 2. Restock product inventory to 20 units
        Inventory inventory = inventoryRepository.findByProductId(testProduct.getId()).orElseThrow();
        inventory.setAvailableStock(20);
        inventoryRepository.save(inventory);

        // 3. Retry with the EXACT same idempotency key and payload
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].quantity").value(10));

        // 4. Assert order now created and idempotency record is COMPLETED
        assertEquals(1, orderRepository.count());
        IdempotencyRecord completedRecord = idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertEquals(IdempotencyStatus.COMPLETED, completedRecord.getStatus());

        // Stock: 20 - 10 = 10
        Inventory finalInv = inventoryRepository.findByProductId(testProduct.getId()).orElseThrow();
        assertEquals(10, finalInv.getAvailableStock());
    }

    @Test
    @DisplayName("Concurrent requests with same key: exactly 1 order created, stock deducted once, duplicates return cached or 409")
    void testCreateOrder_ConcurrentIdempotentRequests() throws Exception {
        String idempotencyKey = "key-concurrent-race-test";
        int threadCount = 5;
        int createdCount = 0;
        int conflictCount = 0;

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Callable<Integer>> tasks = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                tasks.add(() -> {
                    OrderCreateRequest req = new OrderCreateRequest(
                            List.of(new OrderItemRequest(testProduct.getId(), 1)),
                            "Concurrent same-key buy"
                    );

                    MvcResult res = mockMvc.perform(post("/api/v1/orders")
                                    .header("Authorization", "Bearer " + customerToken)
                                    .header("Idempotency-Key", idempotencyKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andReturn();

                    return res.getResponse().getStatus();
                });
            }

            List<Future<Integer>> futures = executor.invokeAll(tasks);

            for (Future<Integer> f : futures) {
                int code = f.get();
                if (code == 201) {
                    createdCount++;
                } else if (code == 409) {
                    conflictCount++;
                }
            }
        }

        // All concurrent requests must either succeed or return 409 Conflict
        assertEquals(threadCount, createdCount + conflictCount, "All concurrent requests must either succeed or return 409 Conflict");
        org.junit.jupiter.api.Assertions.assertTrue(createdCount >= 1, "At least 1 request must succeed with 201 Created");

        // CRITICAL: Exactly 1 order in database
        assertEquals(1, orderRepository.count(), "Exactly 1 order must be created in database across concurrent race");

        // CRITICAL: Stock deducted exactly once: 5 - 1 = 4
        Inventory finalInv = inventoryRepository.findByProductId(testProduct.getId()).orElseThrow();
        assertEquals(4, finalInv.getAvailableStock(), "Stock must be deducted exactly once");

        // CRITICAL: Exactly 1 audit ledger entry
        assertEquals(1, movementRepository.count(), "Exactly 1 audit movement entry must exist");
    }

    @Test
    @DisplayName("Idempotency stalled in-flight recovery: record stuck in PROCESSING beyond timeout is recovered and completed")
    void testCreateOrder_StalledInFlightRecovery() throws Exception {
        String idempotencyKey = "key-stalled-recovery-test";
        OrderCreateRequest req = new OrderCreateRequest(
                List.of(new OrderItemRequest(testProduct.getId(), 1)),
                "Recover from stalled crash"
        );

        String requestHash = idempotencyService.computeRequestHash(req);

        // Simulate a stalled/crashed in-flight record created 5 minutes ago
        IdempotencyRecord stalledRecord = new IdempotencyRecord(
                idempotencyKey,
                customer1,
                requestHash,
                IdempotencyStatus.PROCESSING,
                java.time.Instant.now().plus(24, java.time.temporal.ChronoUnit.HOURS)
        );
        stalledRecord.setCreatedAt(java.time.Instant.now().minus(5, java.time.temporal.ChronoUnit.MINUTES));
        idempotencyRecordRepository.saveAndFlush(stalledRecord);

        // Retry now: should recover the stalled record and successfully place the order
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        // Exactly 1 order created, status COMPLETED
        assertEquals(1, orderRepository.count());
        IdempotencyRecord recovered = idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        assertEquals(IdempotencyStatus.COMPLETED, recovered.getStatus());

        // Stock: 5 - 1 = 4
        Inventory finalInv = inventoryRepository.findByProductId(testProduct.getId()).orElseThrow();
        assertEquals(4, finalInv.getAvailableStock());
    }
}
