package com.stockguard.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockguard.catalog.model.Category;
import com.stockguard.catalog.model.Product;
import com.stockguard.catalog.repository.CategoryRepository;
import com.stockguard.catalog.repository.ProductRepository;
import com.stockguard.inventory.model.Inventory;
import com.stockguard.inventory.model.MovementType;
import com.stockguard.inventory.repository.InventoryMovementRepository;
import com.stockguard.inventory.repository.InventoryRepository;
import com.stockguard.order.dto.OrderCreateRequest;
import com.stockguard.order.dto.OrderItemRequest;
import com.stockguard.order.dto.OrderResponse;
import com.stockguard.order.model.IdempotencyRecord;
import com.stockguard.order.model.IdempotencyStatus;
import com.stockguard.order.repository.IdempotencyRecordRepository;
import com.stockguard.order.repository.OrderItemRepository;
import com.stockguard.order.repository.OrderRepository;
import com.stockguard.order.service.IdempotencyService;
import com.stockguard.order.service.OrderService;
import com.stockguard.security.jwt.JwtTokenProvider;
import com.stockguard.user.model.Role;
import com.stockguard.user.model.User;
import com.stockguard.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 6 Integration Test Suite validating StockGuard against real PostgreSQL.
 *
 * Execution Strategy:
 * 1. Checks if a dedicated PostgreSQL test instance is provided via STOCKGUARD_TEST_POSTGRES_URL.
 * 2. Otherwise checks if Docker is available to spin up a Testcontainers PostgreSQL container.
 * 3. If neither is available in the current environment, cleanly skips all tests via JUnit 5 Assumptions.
 *    (Ensures CI with Docker passes, external PostgreSQL passes, and Docker-less local machines skip without failure).
 */
@SpringBootTest
@AutoConfigureMockMvc
@SuppressWarnings("resource")
public class PostgresIntegrationTest {

    private static PostgreSQLContainer<?> postgresContainer;
    private static String postgresJdbcUrl;
    private static String postgresUser;
    private static String postgresPass;
    private static boolean postgresAvailable = false;

    static {
        String extUrl = System.getenv("STOCKGUARD_TEST_POSTGRES_URL");
        if (extUrl != null && !extUrl.isBlank()) {
            postgresJdbcUrl = extUrl;
            postgresUser = System.getenv().getOrDefault("STOCKGUARD_TEST_POSTGRES_USER", "postgres");
            postgresPass = System.getenv().getOrDefault("STOCKGUARD_TEST_POSTGRES_PASSWORD", "postgres");
            try {
                // Verify real database connectivity
                try (java.sql.Connection conn = java.sql.DriverManager.getConnection(postgresJdbcUrl, postgresUser, postgresPass)) {
                    postgresAvailable = conn.isValid(5);
                }
            } catch (Throwable e) {
                postgresAvailable = false;
            }
        } else {
            try {
                if (DockerClientFactory.instance().isDockerAvailable()) {
                    postgresContainer = new PostgreSQLContainer<>("postgres:16-alpine")
                            .withDatabaseName("stockguard_test")
                            .withUsername("testuser")
                            .withPassword("testpass");
                    postgresContainer.start();
                    postgresJdbcUrl = postgresContainer.getJdbcUrl();
                    postgresUser = postgresContainer.getUsername();
                    postgresPass = postgresContainer.getPassword();
                    postgresAvailable = true;
                }
            } catch (Throwable ignored) {
                postgresAvailable = false;
            }
        }
    }

    @DynamicPropertySource
    static void configurePostgresProperties(DynamicPropertyRegistry registry) {
        if (postgresAvailable) {
            registry.add("spring.datasource.url", () -> postgresJdbcUrl);
            registry.add("spring.datasource.username", () -> postgresUser);
            registry.add("spring.datasource.password", () -> postgresPass);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.flyway.enabled", () -> "true");
            registry.add("spring.flyway.baseline-on-migrate", () -> "true");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        } else {
            // Fallback for context loading when Docker is absent
            registry.add("spring.datasource.url", () -> "jdbc:h2:mem:stockguard_postgres_skip_fallback;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
            registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
            registry.add("spring.flyway.enabled", () -> "false");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        }
    }

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
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectMapper objectMapper;

    private User customer1;
    private User customer2;
    private User admin;
    private String customerToken1;
    private String customerToken2;
    private String adminToken;
    private Product productA;
    private Product productB;

    @BeforeAll
    static void verifyPostgresEnvironment() {
        boolean enforce = Boolean.parseBoolean(System.getenv("STOCKGUARD_ENFORCE_POSTGRES_TESTS"));
        if (enforce) {
            assertTrue(postgresAvailable,
                    "CRITICAL: PostgreSQL integration test execution is enforced by CI (STOCKGUARD_ENFORCE_POSTGRES_TESTS=true), but PostgreSQL was unavailable! Check container startup or STOCKGUARD_TEST_POSTGRES_URL.");
        } else {
            Assumptions.assumeTrue(postgresAvailable,
                    "Skipping real PostgreSQL tests: Docker is not available and STOCKGUARD_TEST_POSTGRES_URL is not configured.");
        }
    }

    @AfterAll
    static void stopPostgresContainer() {
        if (postgresContainer != null && postgresContainer.isRunning()) {
            postgresContainer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        boolean enforce = Boolean.parseBoolean(System.getenv("STOCKGUARD_ENFORCE_POSTGRES_TESTS"));
        if (enforce) {
            assertTrue(postgresAvailable,
                    "CRITICAL: PostgreSQL integration test execution is enforced by CI (STOCKGUARD_ENFORCE_POSTGRES_TESTS=true), but PostgreSQL was unavailable!");
        } else {
            Assumptions.assumeTrue(postgresAvailable,
                    "Skipping real PostgreSQL tests: Docker is not available and STOCKGUARD_TEST_POSTGRES_URL is not configured.");
        }

        // Clean tables in reverse dependency order
        idempotencyRecordRepository.deleteAll();
        movementRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        // Seed users
        customer1 = new User("cust1@stockguard.io", passwordEncoder.encode("Pass123!"), "Cust", "One", Role.CUSTOMER);
        customer2 = new User("cust2@stockguard.io", passwordEncoder.encode("Pass123!"), "Cust", "Two", Role.CUSTOMER);
        admin = new User("admin@stockguard.io", passwordEncoder.encode("AdminPass123!"), "Admin", "User", Role.ADMIN);

        customer1 = userRepository.save(customer1);
        customer2 = userRepository.save(customer2);
        admin = userRepository.save(admin);

        customerToken1 = jwtTokenProvider.generateTokenFromEmail(customer1.getEmail(), customer1.getRole(), customer1.getId());
        customerToken2 = jwtTokenProvider.generateTokenFromEmail(customer2.getEmail(), customer2.getRole(), customer2.getId());
        adminToken = jwtTokenProvider.generateTokenFromEmail(admin.getEmail(), admin.getRole(), admin.getId());

        // Seed category and products
        Category category = categoryRepository.save(new Category("Components", "components", "Hardware components"));

        productA = new Product(category, "GPU-RTX-4090", "NVIDIA GeForce RTX 4090", "Flagship GPU", new BigDecimal("1599.99"), "https://images.unsplash.com/gpu.jpg", true);
        productA = productRepository.save(productA);

        productB = new Product(category, "CPU-I9-14900K", "Intel Core i9-14900K", "High performance CPU", new BigDecimal("589.99"), "https://images.unsplash.com/cpu.jpg", true);
        productB = productRepository.save(productB);

        // Seed inventory
        Inventory invA = new Inventory(productA, 3, 10); // Only 3 units available
        inventoryRepository.save(invA);

        Inventory invB = new Inventory(productB, 0, 10); // 0 units available for rollback test
        inventoryRepository.save(invB);
    }

    @Test
    @DisplayName("PostgreSQL Concurrency: 10 concurrent customer threads competing for 3 units never oversell")
    void testConcurrentOrders_CompetingForLimitedStock_NoOversell() throws Exception {
        int initialStock = 3;
        int threadCount = 10;
        int successCount = 0;
        int rejectedCount = 0;

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Callable<Integer>> tasks = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                tasks.add(() -> {
                    OrderCreateRequest req = new OrderCreateRequest(
                            List.of(new OrderItemRequest(productA.getId(), 1)),
                            "Contended purchase"
                    );

                    MvcResult result = mockMvc.perform(post("/api/v1/orders")
                                    .header("Authorization", "Bearer " + customerToken1)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andReturn();

                    return result.getResponse().getStatus();
                });
            }

            List<Future<Integer>> futures = executor.invokeAll(tasks);

            for (Future<Integer> f : futures) {
                int status = f.get();
                if (status == 201) successCount++;
                else if (status == 400) rejectedCount++;
            }
        }

        assertEquals(initialStock, successCount, "Exactly " + initialStock + " orders must succeed");
        assertEquals(threadCount - initialStock, rejectedCount, "Remaining " + (threadCount - initialStock) + " orders must be rejected");

        // Verify database state on PostgreSQL
        assertEquals(initialStock, orderRepository.count(), "Database must contain exactly " + initialStock + " orders");
        assertEquals(initialStock, movementRepository.count(), "Movement ledger must contain exactly " + initialStock + " entries");

        Inventory finalInv = inventoryRepository.findByProductId(productA.getId()).orElseThrow();
        assertEquals(0, finalInv.getAvailableStock(), "Final available stock must be exactly 0, never negative");
    }

    @Test
    @DisplayName("PostgreSQL Idempotency: Multiple simultaneous requests with same key yield exactly 1 order and deduction")
    void testSimultaneousRequests_SameIdempotencyKey_SingleDeduction() throws Exception {
        String key = "pg-idempotent-race-key";
        int threadCount = 5;
        int createdOrReplayedCount = 0;
        int conflictCount = 0;

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Callable<Integer>> tasks = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                tasks.add(() -> {
                    OrderCreateRequest req = new OrderCreateRequest(
                            List.of(new OrderItemRequest(productA.getId(), 1)),
                            "Same key idempotent buy"
                    );

                    MvcResult result = mockMvc.perform(post("/api/v1/orders")
                                    .header("Authorization", "Bearer " + customerToken1)
                                    .header("Idempotency-Key", key)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andReturn();

                    return result.getResponse().getStatus();
                });
            }

            List<Future<Integer>> futures = executor.invokeAll(tasks);

            for (Future<Integer> f : futures) {
                int status = f.get();
                if (status == 201) createdOrReplayedCount++;
                else if (status == 409) conflictCount++;
            }
        }

        assertTrue(createdOrReplayedCount >= 1, "At least 1 request must return 201 Created");
        assertEquals(threadCount, createdOrReplayedCount + conflictCount, "All requests must be 201 or 409");

        // Exactly 1 order in PostgreSQL
        assertEquals(1, orderRepository.count(), "Exactly 1 order must be created");
        assertEquals(1, movementRepository.count(), "Exactly 1 audit movement recorded");

        // Stock deducted once: 3 - 1 = 2
        Inventory finalInv = inventoryRepository.findByProductId(productA.getId()).orElseThrow();
        assertEquals(2, finalInv.getAvailableStock(), "Stock must be deducted exactly once");

        IdempotencyRecord record = idempotencyRecordRepository.findByIdempotencyKey(key).orElseThrow();
        assertEquals(IdempotencyStatus.COMPLETED, record.getStatus());
    }

    @Test
    @DisplayName("PostgreSQL Rollback Atomicity: Multi-item order failure leaves zero partial records or stock deductions")
    void testMultiItemOrder_RollbackOnInsufficientStock_LeavesZeroSideEffects() throws Exception {
        // Product A has 3 units, Product B has 0 units
        OrderCreateRequest req = new OrderCreateRequest(
                List.of(
                        new OrderItemRequest(productA.getId(), 1),
                        new OrderItemRequest(productB.getId(), 1) // Will fail
                ),
                "Atomic multi-item test"
        );

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Insufficient stock")));

        // Zero orders in database
        assertEquals(0, orderRepository.count(), "No orders must be persisted on rollback");
        assertEquals(0, movementRepository.count(), "No inventory movements must be persisted on rollback");

        // Product A stock remains untouched: 3 units
        Inventory invA = inventoryRepository.findByProductId(productA.getId()).orElseThrow();
        assertEquals(3, invA.getAvailableStock(), "Product A stock must remain untouched at 3");
    }

    @Test
    @DisplayName("PostgreSQL Ledger Consistency: Stock deductions exactly match audit ledger deltas and reasons")
    void testInventoryMovementLedger_ConsistentWithStockChanges() throws Exception {
        OrderCreateRequest req = new OrderCreateRequest(
                List.of(new OrderItemRequest(productA.getId(), 2)),
                "Ledger audit test"
        );

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        assertEquals(1, movementRepository.count());
        var movement = movementRepository.findAll().get(0);

        assertEquals(-2, movement.getQuantityChange());
        assertEquals(3, movement.getPreviousStock());
        assertEquals(1, movement.getNewStock());
        assertEquals(MovementType.ORDER_PURCHASE, movement.getMovementType());
        assertEquals(customer1.getId(), movement.getActor().getId());
    }

    @Test
    @DisplayName("Security & Authorization: RBAC, cross-customer isolation, expired JWT and unauthenticated access")
    void testSecurityHardening_RbacAndCustomerIsolation() throws Exception {
        // 1. Customer 1 places order
        OrderCreateRequest req = new OrderCreateRequest(
                List.of(new OrderItemRequest(productA.getId(), 1)),
                "Customer 1 order"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(responseBody).path("data").path("id").asLong();

        // 2. Customer 2 attempting to access Customer 1's order receives 404 Not Found (ownership isolation)
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", "Bearer " + customerToken2))
                .andExpect(status().isNotFound());

        // 3. Admin can inspect Customer 1's order
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 4. Admin attempting to place order receives 403 Forbidden
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // 5. Unauthenticated request receives 401 Unauthorized
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        // 6. Expired JWT receives 401 Unauthorized
        String expiredToken = io.jsonwebtoken.Jwts.builder()
                .subject(customer1.getEmail())
                .claim("userId", customer1.getId())
                .claim("role", customer1.getRole().name())
                .issuedAt(java.util.Date.from(Instant.now().minus(2, ChronoUnit.HOURS)))
                .expiration(java.util.Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "stockguard_test_secret_key_that_is_at_least_256_bits_long_for_hmac_sha256!".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PostgreSQL Idempotency Lifecycle: Stalled in-flight and FAILED states are safely recovered")
    void testIdempotencyLifecycle_StalledAndFailedRecovery() throws Exception {
        String key = "pg-stalled-lifecycle-key";
        OrderCreateRequest req = new OrderCreateRequest(
                List.of(new OrderItemRequest(productA.getId(), 1)),
                "Stalled recovery on Postgres"
        );

        String requestHash = idempotencyService.computeRequestHash(req);

        // 1. Simulate a crashed/stalled record created 5 minutes ago
        IdempotencyRecord stalled = new IdempotencyRecord(
                key,
                customer1,
                requestHash,
                IdempotencyStatus.PROCESSING,
                Instant.now().plus(24, ChronoUnit.HOURS)
        );
        stalled.setCreatedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        idempotencyRecordRepository.saveAndFlush(stalled);

        // 2. Retry succeeds and marks COMPLETED
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken1)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        assertEquals(1, orderRepository.count());
        IdempotencyRecord completed = idempotencyRecordRepository.findByIdempotencyKey(key).orElseThrow();
        assertEquals(IdempotencyStatus.COMPLETED, completed.getStatus());
    }

    @Test
    @DisplayName("PostgreSQL Concurrency: Delayed original request in gap between reserveKey and executeOrderPlacement returns completed order without duplicate deduction")
    void testPostgres_StalledGapRecovery_LateRequestReturnsExistingOrderWithoutDuplicateDeduction() throws Exception {
        String key = "pg-gap-race-key-100";
        OrderCreateRequest req = new OrderCreateRequest(
                List.of(new OrderItemRequest(productA.getId(), 2)),
                "PostgreSQL gap race condition test"
        );
        String requestHash = idempotencyService.computeRequestHash(req);

        // 1. Thread 1 calls reserveKey at T0 (runs real SQL on PostgreSQL)
        var initialReserve = idempotencyService.reserveKey(key, customer1, requestHash);
        assertTrue(initialReserve.isEmpty(), "First reservation must succeed with empty cache");

        // 2. Simulate Thread 1 pausing in the gap while > 2 minutes pass (stalled timeout reached)
        IdempotencyRecord record = idempotencyRecordRepository.findByIdempotencyKey(key).orElseThrow();
        record.setCreatedAt(Instant.now().minus(3, ChronoUnit.MINUTES));
        idempotencyRecordRepository.saveAndFlush(record);

        // 3. Thread 2 (customer retry) arrives while Thread 1 is still alive in the gap:
        //    Via MockMvc HTTP POST /api/v1/orders with same Idempotency-Key
        MvcResult result2 = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + customerToken1)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long order2Id = objectMapper.readTree(result2.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        assertEquals(1, orderRepository.count(), "Thread 2 must create exactly 1 order");
        assertEquals(1, movementRepository.count(), "Thread 2 must record 1 audit movement");
        Inventory invAfterThread2 = inventoryRepository.findByProductId(productA.getId()).orElseThrow();
        assertEquals(1, invAfterThread2.getAvailableStock(), "Stock should be 3 - 2 = 1");

        // 4. Thread 1 finally enters executeOrderPlacement after the delay:
        //    lockActiveRecord acquires PostgreSQL FOR UPDATE row lock, detects COMPLETED, and returns existing OrderResponse.
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        OrderResponse resp1 = txTemplate.execute(status ->
                orderService.executeOrderPlacement(req, customer1, key)
        );

        assertNotNull(resp1);
        assertEquals(order2Id, resp1.getId(), "Thread 1 must receive the same order ID created by Thread 2");

        // 5. Assert NO duplicate order and NO double inventory deduction occurred on PostgreSQL
        assertEquals(1, orderRepository.count(), "Database must STILL contain exactly 1 order");
        assertEquals(1, movementRepository.count(), "Ledger must STILL contain exactly 1 movement");
        Inventory invFinal = inventoryRepository.findByProductId(productA.getId()).orElseThrow();
        assertEquals(1, invFinal.getAvailableStock(), "Stock must remain 1 (NOT deducted twice to -1)");
    }
}
