package com.stockguard.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockguard.catalog.model.Category;
import com.stockguard.catalog.model.Product;
import com.stockguard.catalog.repository.CategoryRepository;
import com.stockguard.catalog.repository.ProductRepository;
import com.stockguard.inventory.dto.StockAdjustmentRequest;
import com.stockguard.inventory.model.AdjustmentType;
import com.stockguard.inventory.model.Inventory;
import com.stockguard.inventory.repository.InventoryMovementRepository;
import com.stockguard.inventory.repository.InventoryRepository;
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
class AdminInventoryControllerTest {

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
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String customerToken;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        movementRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Create Admin user & token
        User admin = new User("admin.inventory@stockguard.io", passwordEncoder.encode("AdminPass123!"), "Admin", "User", Role.ADMIN);
        User savedAdmin = userRepository.save(admin);
        adminToken = jwtTokenProvider.generateTokenFromEmail(savedAdmin.getEmail(), savedAdmin.getRole(), savedAdmin.getId());

        // 2. Create Customer user & token
        User customer = new User("customer.inventory@stockguard.io", passwordEncoder.encode("CustomerPass123!"), "Customer", "User", Role.CUSTOMER);
        User savedCustomer = userRepository.save(customer);
        customerToken = jwtTokenProvider.generateTokenFromEmail(savedCustomer.getEmail(), savedCustomer.getRole(), savedCustomer.getId());

        // 3. Seed Category & Product
        Category category = new Category("Storage", "storage-devices", "Enterprise NVMe SSDs");
        Category savedCategory = categoryRepository.save(category);

        Product product = new Product(
                savedCategory,
                "SSD-NVME-2TB",
                "2TB Gen4 Enterprise NVMe SSD",
                "High performance NVMe drive",
                new BigDecimal("189.99"),
                "https://images.unsplash.com/ssd.jpg",
                true
        );
        testProduct = productRepository.save(product);

        // Initialize inventory with 0 stock
        inventoryRepository.save(new Inventory(testProduct, 0, 10));
    }

    @Test
    @DisplayName("Anonymous access to stock adjustments should be rejected with 401 Unauthorized")
    void testAnonymousAccessRejected() throws Exception {
        StockAdjustmentRequest request = new StockAdjustmentRequest(20, AdjustmentType.ADD, "Restock batch");

        mockMvc.perform(post("/api/v1/admin/inventory/" + testProduct.getId() + "/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CUSTOMER role access to stock adjustments should be rejected with 403 Forbidden")
    void testCustomerRoleAccessForbidden() throws Exception {
        StockAdjustmentRequest request = new StockAdjustmentRequest(20, AdjustmentType.ADD, "Restock batch");

        mockMvc.perform(post("/api/v1/admin/inventory/" + testProduct.getId() + "/adjustments")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN role should successfully ADD stock, return 200, and create audit record")
    void testAdminAddStockSuccess() throws Exception {
        StockAdjustmentRequest request = new StockAdjustmentRequest(50, AdjustmentType.ADD, "Inbound supplier shipment #8812", "PO-8812");

        mockMvc.perform(post("/api/v1/admin/inventory/" + testProduct.getId() + "/adjustments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.productId", is(testProduct.getId().intValue())))
                .andExpect(jsonPath("$.data.productSku", is("SSD-NVME-2TB")))
                .andExpect(jsonPath("$.data.availableStock", is(50)))
                .andExpect(jsonPath("$.data.inStock", is(true)));

        assertEquals(1, movementRepository.count());
    }

    @Test
    @DisplayName("ADMIN role should successfully REMOVE stock when sufficient available stock exists")
    void testAdminRemoveStockSuccess() throws Exception {
        // Step 1: Add 40 units
        mockMvc.perform(post("/api/v1/admin/inventory/" + testProduct.getId() + "/adjustments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StockAdjustmentRequest(40, AdjustmentType.ADD, "Initial load"))))
                .andExpect(status().isOk());

        // Step 2: Remove 15 units
        StockAdjustmentRequest removeReq = new StockAdjustmentRequest(15, AdjustmentType.REMOVE, "Lab test sample write-off");

        mockMvc.perform(post("/api/v1/admin/inventory/" + testProduct.getId() + "/adjustments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(removeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableStock", is(25)));

        assertEquals(2, movementRepository.count());
    }

    @Test
    @DisplayName("Attempting to REMOVE more stock than available should be rejected with 400 Bad Request")
    void testInsufficientStockReturns400() throws Exception {
        // Available stock is 0
        StockAdjustmentRequest removeReq = new StockAdjustmentRequest(10, AdjustmentType.REMOVE, "Overdraft attempt");

        mockMvc.perform(post("/api/v1/admin/inventory/" + testProduct.getId() + "/adjustments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(removeReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Insufficient stock")));

        // Verify zero movements recorded and stock remains 0
        assertEquals(0, movementRepository.count());
        Inventory inv = inventoryRepository.findByProductId(testProduct.getId()).orElseThrow();
        assertEquals(0, inv.getAvailableStock());
    }

    @Test
    @DisplayName("Validation failure on quantity <= 0 or blank reason should return 400 Bad Request")
    void testValidationErrorsReturn400() throws Exception {
        // Quantity <= 0
        StockAdjustmentRequest invalidQty = new StockAdjustmentRequest(0, AdjustmentType.ADD, "Valid reason");
        mockMvc.perform(post("/api/v1/admin/inventory/" + testProduct.getId() + "/adjustments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidQty)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.quantity", notNullValue()));

        // Blank reason
        StockAdjustmentRequest blankReason = new StockAdjustmentRequest(10, AdjustmentType.ADD, "   ");
        mockMvc.perform(post("/api/v1/admin/inventory/" + testProduct.getId() + "/adjustments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankReason)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.reason", notNullValue()));
    }

    @Test
    @DisplayName("Adjusting non-existent product ID should return 404 Not Found")
    void testAdjustingNonExistentProductReturns404() throws Exception {
        StockAdjustmentRequest req = new StockAdjustmentRequest(10, AdjustmentType.ADD, "Valid reason");

        mockMvc.perform(post("/api/v1/admin/inventory/999999/adjustments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/admin/inventory/{productId} returns inventory details for product")
    void testGetInventoryByProductId() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inventory/" + testProduct.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.productId", is(testProduct.getId().intValue())))
                .andExpect(jsonPath("$.data.availableStock", is(0)));
    }

    @Test
    @DisplayName("GET /api/v1/admin/inventory returns paginated product inventory list")
    void testGetAllInventory() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inventory?page=0&size=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));

        // Also test trailing slash variant to ensure Spring 6 trailing-slash resiliency
        mockMvc.perform(get("/api/v1/admin/inventory/?page=0&size=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("GET /api/v1/admin/inventory auto-initializes catalog products that lack inventory rows")
    void testGetAllInventoryAutoInitializesUninitializedCatalogProducts() throws Exception {
        Category category = categoryRepository.findAll().get(0);
        Product uninitializedProduct = new Product(
                category,
                "GPU-RTX-4080S",
                "GeForce RTX 4080 Super",
                "High-end gaming graphics card",
                new BigDecimal("999.99"),
                null,
                true
        );
        productRepository.save(uninitializedProduct);

        mockMvc.perform(get("/api/v1/admin/inventory?page=0&size=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalElements", is(2)))
                .andExpect(jsonPath("$.data.content[*].productSku", hasItem("GPU-RTX-4080S")))
                .andExpect(jsonPath("$.data.content[*].availableStock", hasItem(0)));
    }

    @Test
    @DisplayName("GET /api/v1/admin/inventory/movements returns paginated audit history ordered stably")
    void testGetMovementsAuditHistory() throws Exception {
        // Create 2 adjustments
        mockMvc.perform(post("/api/v1/admin/inventory/" + testProduct.getId() + "/adjustments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StockAdjustmentRequest(20, AdjustmentType.ADD, "Intake A"))));

        mockMvc.perform(post("/api/v1/admin/inventory/" + testProduct.getId() + "/adjustments")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StockAdjustmentRequest(5, AdjustmentType.REMOVE, "Defect B"))));

        mockMvc.perform(get("/api/v1/admin/inventory/movements?page=0&size=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].reason", is("Defect B")))
                .andExpect(jsonPath("$.data.content[0].quantityChange", is(-5)))
                .andExpect(jsonPath("$.data.content[0].actorEmail", is("admin.inventory@stockguard.io")))
                .andExpect(jsonPath("$.data.content[1].reason", is("Intake A")))
                .andExpect(jsonPath("$.data.content[1].quantityChange", is(20)));
    }

    @Test
    @DisplayName("Concurrent stock adjustments on the same product do not lose updates")
    void testConcurrentAdjustmentsSerializeCorrectly() throws Exception {
        int threadCount = 10;
        int addPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            tasks.add(() -> {
                StockAdjustmentRequest req = new StockAdjustmentRequest(
                        addPerThread,
                        AdjustmentType.ADD,
                        "Concurrent batch #" + index
                );
                mockMvc.perform(post("/api/v1/admin/inventory/" + testProduct.getId() + "/adjustments")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isOk());
                return addPerThread;
            });
        }

        List<Future<Integer>> futures = executor.invokeAll(tasks);
        for (Future<Integer> f : futures) {
            f.get();
        }
        executor.shutdown();

        // 10 threads adding 5 each = 50 total stock
        Inventory finalInventory = inventoryRepository.findByProductId(testProduct.getId()).orElseThrow();
        assertEquals(threadCount * addPerThread, finalInventory.getAvailableStock());
        assertEquals(threadCount, movementRepository.count());
    }
}
