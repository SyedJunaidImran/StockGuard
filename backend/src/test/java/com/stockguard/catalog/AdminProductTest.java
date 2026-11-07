package com.stockguard.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockguard.catalog.dto.ProductCreateRequest;
import com.stockguard.catalog.dto.ProductStatusUpdateRequest;
import com.stockguard.catalog.dto.ProductUpdateRequest;
import com.stockguard.catalog.model.Category;
import com.stockguard.catalog.model.Product;
import com.stockguard.catalog.repository.CategoryRepository;
import com.stockguard.catalog.repository.ProductRepository;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminProductTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.stockguard.inventory.repository.InventoryRepository inventoryRepository;

    @Autowired
    private com.stockguard.inventory.repository.InventoryMovementRepository movementRepository;

    private String adminToken;
    private String customerToken;
    private Category testCategory;
    private Product existingProduct;

    @BeforeEach
    void setUp() {
        movementRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        // Create Admin user and token
        User adminUser = new User(
                "admin.catalog@example.com",
                passwordEncoder.encode("AdminSecret123!"),
                "Admin",
                "User",
                Role.ADMIN
        );
        User savedAdmin = userRepository.save(adminUser);
        adminToken = jwtTokenProvider.generateTokenFromEmail(
                savedAdmin.getEmail(), savedAdmin.getRole(), savedAdmin.getId()
        );

        // Create Customer user and token
        User customerUser = new User(
                "customer.catalog@example.com",
                passwordEncoder.encode("CustomerSecret123!"),
                "Customer",
                "User",
                Role.CUSTOMER
        );
        User savedCustomer = userRepository.save(customerUser);
        customerToken = jwtTokenProvider.generateTokenFromEmail(
                savedCustomer.getEmail(), savedCustomer.getRole(), savedCustomer.getId()
        );

        // Create test category and existing product
        testCategory = categoryRepository.save(new Category(
                "Storage", "storage", "Fast storage drives"
        ));

        existingProduct = productRepository.save(new Product(
                testCategory,
                "SSD-SAMSUNG-990PRO-2TB",
                "Samsung 990 PRO 2TB NVMe SSD",
                "Flagship PCIe 4.0 NVMe M.2 internal SSD",
                new BigDecimal("179.99"),
                "https://images.unsplash.com/ssd-samsung",
                true
        ));
    }

    @Test
    @DisplayName("POST /api/v1/admin/products as ADMIN should successfully create product (201 Created)")
    void testAdminCreateProductSuccess() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "Corsair Dominator Titanium 32GB DDR5",
                "High performance 6000MHz CL30 memory kit",
                "RAM-CORSAIR-32GB-DDR5",
                new BigDecimal("169.99"),
                testCategory.getId(),
                "https://images.unsplash.com/ram-corsair",
                true
        );

        mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.sku", is("RAM-CORSAIR-32GB-DDR5")))
                .andExpect(jsonPath("$.data.name", is("Corsair Dominator Titanium 32GB DDR5")))
                .andExpect(jsonPath("$.data.price", is(169.99)))
                .andExpect(jsonPath("$.data.category.id", is(testCategory.getId().intValue())));
    }

    @Test
    @DisplayName("POST /api/v1/admin/products with duplicate SKU should return 409 Conflict")
    void testAdminCreateProductDuplicateSkuReturns409() throws Exception {
        ProductCreateRequest duplicateRequest = new ProductCreateRequest(
                "Another Samsung 990 Pro",
                "Duplicate SKU attempt",
                "SSD-SAMSUNG-990PRO-2TB", // already exists
                new BigDecimal("159.99"),
                testCategory.getId(),
                null,
                true
        );

        mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    @DisplayName("POST /api/v1/admin/products with invalid data should return 400 Bad Request")
    void testAdminCreateProductValidationFailure() throws Exception {
        ProductCreateRequest invalidRequest = new ProductCreateRequest();
        invalidRequest.setName(""); // blank
        invalidRequest.setSku("X"); // under 3 chars
        invalidRequest.setPrice(new BigDecimal("-10.00")); // negative price
        invalidRequest.setCategoryId(null); // missing category

        mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.validationErrors.name", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.sku", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.price", notNullValue()))
                .andExpect(jsonPath("$.validationErrors.categoryId", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/admin/products as anonymous should return 401 Unauthorized")
    void testAdminCreateProductAnonymousReturns401() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "Valid Name", "Description", "SKU-TEST-123",
                new BigDecimal("50.00"), testCategory.getId(), null, true
        );

        mockMvc.perform(post("/api/v1/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    @DisplayName("POST /api/v1/admin/products as CUSTOMER should return 403 Forbidden")
    void testAdminCreateProductCustomerReturns403() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "Valid Name", "Description", "SKU-TEST-123",
                new BigDecimal("50.00"), testCategory.getId(), null, true
        );

        mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.error", is("Forbidden")));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/products/{id} as ADMIN should successfully update product")
    void testAdminUpdateProductSuccess() throws Exception {
        ProductUpdateRequest updateRequest = new ProductUpdateRequest(
                "Samsung 990 PRO 2TB (Updated Edition)",
                "Upgraded heatsink version",
                "SSD-SAMSUNG-990PRO-2TB",
                new BigDecimal("189.99"),
                testCategory.getId(),
                "https://images.unsplash.com/new-image",
                true
        );

        mockMvc.perform(put("/api/v1/admin/products/" + existingProduct.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("Samsung 990 PRO 2TB (Updated Edition)")))
                .andExpect(jsonPath("$.data.price", is(189.99)));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/products/{id} for non-existent product should return 404")
    void testAdminUpdateNonExistentProductReturns404() throws Exception {
        ProductUpdateRequest updateRequest = new ProductUpdateRequest(
                "Valid Name", "Description", "SKU-UNIQUE-NEW",
                new BigDecimal("50.00"), testCategory.getId(), null, true
        );

        mockMvc.perform(put("/api/v1/admin/products/999999")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/products/{id} as CUSTOMER should return 403 Forbidden")
    void testAdminUpdateProductCustomerReturns403() throws Exception {
        ProductUpdateRequest updateRequest = new ProductUpdateRequest(
                "Valid Name", "Description", "SKU-UNIQUE-NEW",
                new BigDecimal("50.00"), testCategory.getId(), null, true
        );

        mockMvc.perform(put("/api/v1/admin/products/" + existingProduct.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/products/{id}/status as ADMIN should toggle active status")
    void testAdminToggleProductStatusSuccess() throws Exception {
        ProductStatusUpdateRequest statusRequest = new ProductStatusUpdateRequest(false);

        mockMvc.perform(patch("/api/v1/admin/products/" + existingProduct.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.active", is(false)));

        // Re-activate
        statusRequest.setActive(true);
        mockMvc.perform(patch("/api/v1/admin/products/" + existingProduct.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active", is(true)));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/products/{id}/status as CUSTOMER should return 403 Forbidden")
    void testAdminToggleProductStatusCustomerReturns403() throws Exception {
        ProductStatusUpdateRequest statusRequest = new ProductStatusUpdateRequest(false);

        mockMvc.perform(patch("/api/v1/admin/products/" + existingProduct.getId() + "/status")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }
}
