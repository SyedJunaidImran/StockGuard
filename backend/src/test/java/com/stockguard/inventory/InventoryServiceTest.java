package com.stockguard.inventory;

import com.stockguard.catalog.model.Category;
import com.stockguard.catalog.model.Product;
import com.stockguard.catalog.repository.CategoryRepository;
import com.stockguard.catalog.repository.ProductRepository;
import com.stockguard.common.dto.PageResponse;
import com.stockguard.common.exception.ResourceNotFoundException;
import com.stockguard.inventory.dto.InventoryMovementResponse;
import com.stockguard.inventory.dto.InventoryResponse;
import com.stockguard.inventory.dto.StockAdjustmentRequest;
import com.stockguard.inventory.exception.InsufficientStockException;
import com.stockguard.inventory.model.AdjustmentType;
import com.stockguard.inventory.model.Inventory;
import com.stockguard.inventory.model.InventoryMovement;
import com.stockguard.inventory.repository.InventoryMovementRepository;
import com.stockguard.inventory.repository.InventoryRepository;
import com.stockguard.inventory.service.InventoryService;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InventoryServiceTest {

    @Autowired
    private InventoryService inventoryService;

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

    private Product testProduct;
    private User adminUser;
    private UserPrincipal adminPrincipal;

    @BeforeEach
    void setUp() {
        movementRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        Category category = new Category("Electronics", "electronics", "Hardware components");
        Category savedCategory = categoryRepository.save(category);

        testProduct = new Product(
                savedCategory,
                "CPU-TEST-001",
                "Ryzen Test Processor",
                "High performance test CPU",
                new BigDecimal("299.99"),
                null,
                true
        );
        testProduct = productRepository.save(testProduct);

        adminUser = new User("admin.test@stockguard.io", "hashedPass", "Admin", "User", Role.ADMIN);
        adminUser = userRepository.save(adminUser);
        adminPrincipal = UserPrincipal.create(adminUser);
    }

    @Test
    @DisplayName("adjustStock ADD should increase available stock and record positive movement entry")
    void testSuccessfulAddAdjustment() {
        StockAdjustmentRequest request = new StockAdjustmentRequest(
                50,
                AdjustmentType.ADD,
                "Initial warehouse intake",
                "PO-1001"
        );

        InventoryResponse response = inventoryService.adjustStock(testProduct.getId(), request, adminPrincipal);

        assertNotNull(response);
        assertEquals(50, response.getAvailableStock());
        assertEquals(0, response.getReservedStock());
        assertTrue(response.isInStock());

        List<InventoryMovement> movements = movementRepository.findAll();
        assertEquals(1, movements.size());
        InventoryMovement movement = movements.get(0);
        assertEquals(50, movement.getQuantityChange());
        assertEquals(0, movement.getPreviousStock());
        assertEquals(50, movement.getNewStock());
        assertEquals("Initial warehouse intake", movement.getReason());
        assertEquals("PO-1001", movement.getReferenceId());
        assertNotNull(movement.getActor());
        assertEquals(adminUser.getId(), movement.getActor().getId());
    }

    @Test
    @DisplayName("adjustStock REMOVE should decrease available stock when sufficient quantity exists")
    void testSuccessfulRemoveAdjustment() {
        // Step 1: Add 100 units
        inventoryService.adjustStock(
                testProduct.getId(),
                new StockAdjustmentRequest(100, AdjustmentType.ADD, "Stock intake"),
                adminPrincipal
        );

        // Step 2: Remove 30 units
        StockAdjustmentRequest removeReq = new StockAdjustmentRequest(
                30,
                AdjustmentType.REMOVE,
                "Damaged inventory deduction",
                "RMA-552"
        );
        InventoryResponse response = inventoryService.adjustStock(testProduct.getId(), removeReq, adminPrincipal);

        assertEquals(70, response.getAvailableStock());

        List<InventoryMovement> movements = movementRepository.findAll();
        assertEquals(2, movements.size());
        InventoryMovement removeMovement = movements.get(1);
        assertEquals(-30, removeMovement.getQuantityChange());
        assertEquals(100, removeMovement.getPreviousStock());
        assertEquals(70, removeMovement.getNewStock());
        assertEquals("Damaged inventory deduction", removeMovement.getReason());
    }

    @Test
    @DisplayName("adjustStock REMOVE should throw InsufficientStockException and record NO movement if requested exceeds stock")
    void testInsufficientStockRejection() {
        // Product currently has 0 stock
        StockAdjustmentRequest removeReq = new StockAdjustmentRequest(
                5,
                AdjustmentType.REMOVE,
                "Attempted overdraft"
        );

        InsufficientStockException ex = assertThrows(
                InsufficientStockException.class,
                () -> inventoryService.adjustStock(testProduct.getId(), removeReq, adminPrincipal)
        );

        assertTrue(ex.getMessage().contains("Insufficient stock"));

        // Verify stock remains untouched at 0 and no ledger entry is created
        Inventory inv = inventoryRepository.findByProductId(testProduct.getId()).orElse(null);
        if (inv != null) {
            assertEquals(0, inv.getAvailableStock());
        }
        assertEquals(0, movementRepository.count());
    }

    @Test
    @DisplayName("adjustStock should reject non-positive quantities")
    void testNonPositiveQuantityRejection() {
        StockAdjustmentRequest zeroReq = new StockAdjustmentRequest(0, AdjustmentType.ADD, "Zero quantity");
        assertThrows(IllegalArgumentException.class, () ->
                inventoryService.adjustStock(testProduct.getId(), zeroReq, adminPrincipal)
        );

        StockAdjustmentRequest negativeReq = new StockAdjustmentRequest(-10, AdjustmentType.ADD, "Negative quantity");
        assertThrows(IllegalArgumentException.class, () ->
                inventoryService.adjustStock(testProduct.getId(), negativeReq, adminPrincipal)
        );

        assertEquals(0, movementRepository.count());
    }

    @Test
    @DisplayName("adjustStock should reject blank or empty reason")
    void testBlankReasonRejection() {
        StockAdjustmentRequest blankReq = new StockAdjustmentRequest(10, AdjustmentType.ADD, "   ");
        assertThrows(IllegalArgumentException.class, () ->
                inventoryService.adjustStock(testProduct.getId(), blankReq, adminPrincipal)
        );

        StockAdjustmentRequest nullReq = new StockAdjustmentRequest(10, AdjustmentType.ADD, null);
        assertThrows(IllegalArgumentException.class, () ->
                inventoryService.adjustStock(testProduct.getId(), nullReq, adminPrincipal)
        );

        assertEquals(0, movementRepository.count());
    }

    @Test
    @DisplayName("adjustStock should throw ResourceNotFoundException for non-existent product")
    void testMissingProductRejection() {
        StockAdjustmentRequest request = new StockAdjustmentRequest(10, AdjustmentType.ADD, "Ghost product");
        assertThrows(ResourceNotFoundException.class, () ->
                inventoryService.adjustStock(999999L, request, adminPrincipal)
        );
        assertEquals(0, movementRepository.count());
    }

    @Test
    @DisplayName("getMovementsByProductId should return paginated movements in stable descending order")
    void testMovementHistoryPaginationAndOrdering() {
        // Apply 3 successive adjustments
        inventoryService.adjustStock(testProduct.getId(), new StockAdjustmentRequest(10, AdjustmentType.ADD, "Batch 1"), adminPrincipal);
        inventoryService.adjustStock(testProduct.getId(), new StockAdjustmentRequest(20, AdjustmentType.ADD, "Batch 2"), adminPrincipal);
        inventoryService.adjustStock(testProduct.getId(), new StockAdjustmentRequest(5, AdjustmentType.REMOVE, "Correction"), adminPrincipal);

        PageResponse<InventoryMovementResponse> page = inventoryService.getMovementsByProductId(testProduct.getId(), 0, 2);

        assertEquals(2, page.getContent().size());
        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
        assertTrue(page.isFirst());
        assertFalse(page.isLast());

        // First item on page 0 must be the latest movement ("Correction", delta -5)
        assertEquals(-5, page.getContent().get(0).getQuantityChange());
        assertEquals("Correction", page.getContent().get(0).getReason());
        // Second item must be "Batch 2", delta 20
        assertEquals(20, page.getContent().get(1).getQuantityChange());
        assertEquals("Batch 2", page.getContent().get(1).getReason());
    }

    @Test
    @DisplayName("ensureInventoryExistsForAllProducts safely initializes missing inventory rows with 0 stock")
    void testEnsureInventoryExistsForAllProducts_InitializesMissingProductsSafely() {
        Category category = categoryRepository.findAll().get(0);
        Product uninitializedProduct = new Product(
                category,
                "GPU-RTX-5090",
                "NVIDIA GeForce RTX 5090",
                "Next-gen GPU flagship",
                new BigDecimal("1999.99"),
                null,
                true
        );
        final Product savedProduct = productRepository.save(uninitializedProduct);

        // Precondition: no inventory record exists
        assertFalse(inventoryRepository.existsByProductId(savedProduct.getId()));

        // Run initialization
        inventoryService.ensureInventoryExistsForAllProducts();

        // Verification: inventory record now exists with 0 available stock, 0 reserved stock, and threshold 10
        assertTrue(inventoryRepository.existsByProductId(savedProduct.getId()));
        Inventory inv = inventoryRepository.findByProductId(savedProduct.getId()).orElseThrow();
        assertEquals(0, inv.getAvailableStock());
        assertEquals(0, inv.getReservedStock());
        assertEquals(10, inv.getLowStockThreshold());
        assertTrue(inv.isLowStock());
        assertFalse(inv.isInStock());

        // Idempotency verification: calling again should not create duplicates or alter state
        inventoryService.ensureInventoryExistsForAllProducts();
        assertEquals(1, inventoryRepository.findAll().stream()
                .filter(i -> i.getProduct().getId().equals(savedProduct.getId()))
                .count());
    }

    @Test
    @DisplayName("ensureInventoryExistsForAllProducts preserves existing stock and does not overwrite")
    void testEnsureInventoryExistsForAllProducts_PreservesExistingStock() {
        // Set existing product stock to 75
        inventoryService.adjustStock(
                testProduct.getId(),
                new StockAdjustmentRequest(75, AdjustmentType.ADD, "Initial stock"),
                adminPrincipal
        );

        // Run ensureInventoryExistsForAllProducts
        inventoryService.ensureInventoryExistsForAllProducts();

        // Verify stock is preserved at 75
        Inventory inv = inventoryRepository.findByProductId(testProduct.getId()).orElseThrow();
        assertEquals(75, inv.getAvailableStock());
    }

    @Test
    @DisplayName("getAllInventory automatically self-heals products without inventory records")
    void testGetAllInventory_SelfHealsMissingProducts() {
        Category category = categoryRepository.findAll().get(0);
        Product uninitializedProduct = new Product(
                category,
                "MEM-DDR5-32G",
                "Corsair Vengeance 32GB Kit",
                "High speed DDR5 memory",
                new BigDecimal("129.99"),
                null,
                true
        );
        productRepository.save(uninitializedProduct);

        // Execute getAllInventory
        PageResponse<InventoryResponse> result = inventoryService.getAllInventory(0, 10);

        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        boolean foundNewProduct = result.getContent().stream()
                .anyMatch(r -> "MEM-DDR5-32G".equals(r.getProductSku()) && r.getAvailableStock() == 0);
        assertTrue(foundNewProduct, "Uninitialized product should appear in inventory list with 0 stock");
    }
}
