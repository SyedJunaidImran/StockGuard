package com.stockguard.catalog;

import com.stockguard.catalog.model.Category;
import com.stockguard.catalog.model.Product;
import com.stockguard.catalog.repository.CategoryRepository;
import com.stockguard.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductCatalogTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private com.stockguard.inventory.repository.InventoryRepository inventoryRepository;

    @Autowired
    private com.stockguard.inventory.repository.InventoryMovementRepository movementRepository;

    private Category hardwareCategory;
    private Category networkingCategory;
    private Product product1;
    private Product product2;
    private Product inactiveProduct;

    @BeforeEach
    void setUp() {
        movementRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        hardwareCategory = categoryRepository.save(new Category(
                "Hardware", "hardware", "PC hardware components"
        ));
        networkingCategory = categoryRepository.save(new Category(
                "Networking", "networking", "Network gear"
        ));

        product1 = productRepository.save(new Product(
                hardwareCategory,
                "CPU-RYZEN-7800X3D",
                "AMD Ryzen 7 7800X3D Processor",
                "High performance gaming CPU with 3D V-Cache technology",
                new BigDecimal("449.99"),
                "https://images.unsplash.com/photo-cpu",
                true
        ));

        product2 = productRepository.save(new Product(
                hardwareCategory,
                "GPU-RTX-4080-SUPER",
                "NVIDIA GeForce RTX 4080 Super",
                "Ultra-fast graphics card with ray tracing and DLSS 3.5",
                new BigDecimal("999.99"),
                "https://images.unsplash.com/photo-gpu",
                true
        ));

        inactiveProduct = productRepository.save(new Product(
                networkingCategory,
                "NET-SWITCH-24P-POE",
                "Enterprise 24-Port Gigabit PoE Switch",
                "Managed Layer 2 rackmount PoE switch for enterprise networks",
                new BigDecimal("329.50"),
                "https://images.unsplash.com/photo-switch",
                false
        ));
    }

    @Test
    @DisplayName("GET /api/v1/products should return paginated list of active products by default")
    void testGetProductsPagination() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(2))) // Only 2 active products
                .andExpect(jsonPath("$.data.pageNumber", is(0)))
                .andExpect(jsonPath("$.data.pageSize", is(10)))
                .andExpect(jsonPath("$.data.totalElements", is(2)))
                .andExpect(jsonPath("$.data.first", is(true)))
                .andExpect(jsonPath("$.data.last", is(true)))
                .andExpect(jsonPath("$.data.content[0].sku", notNullValue()))
                .andExpect(jsonPath("$.data.content[0].category.name", is("Hardware")));
    }

    @Test
    @DisplayName("GET /api/v1/products with search query should filter by product name")
    void testSearchProductsByName() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("search", "Ryzen")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].sku", is("CPU-RYZEN-7800X3D")))
                .andExpect(jsonPath("$.data.content[0].name", containsString("Ryzen")));
    }

    @Test
    @DisplayName("GET /api/v1/products with search query should filter by product description")
    void testSearchProductsByDescription() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("search", "ray tracing")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].sku", is("GPU-RTX-4080-SUPER")));
    }

    @Test
    @DisplayName("GET /api/v1/products with categoryId filter should return products in that category")
    void testFilterProductsByCategory() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("categoryId", hardwareCategory.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)));

        mockMvc.perform(get("/api/v1/products")
                        .param("categoryId", networkingCategory.getId().toString())
                        .param("active", "false")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].sku", is("NET-SWITCH-24P-POE")));
    }

    @Test
    @DisplayName("GET /api/v1/products sorted by price ASC should return cheapest product first")
    void testSortProductsByPriceAsc() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("sortBy", "price")
                        .param("sortDir", "ASC")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].sku", is("CPU-RYZEN-7800X3D")))
                .andExpect(jsonPath("$.data.content[1].sku", is("GPU-RTX-4080-SUPER")));
    }

    @Test
    @DisplayName("GET /api/v1/products sorted by price DESC should return most expensive product first")
    void testSortProductsByPriceDesc() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("sortBy", "price")
                        .param("sortDir", "DESC")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].sku", is("GPU-RTX-4080-SUPER")))
                .andExpect(jsonPath("$.data.content[1].sku", is("CPU-RYZEN-7800X3D")));
    }

    @Test
    @DisplayName("GET /api/v1/products with disallowed sort field should return 400 Bad Request")
    void testDisallowedSortFieldReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("sortBy", "password")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Invalid sort field")));
    }

    @Test
    @DisplayName("GET /api/v1/products with negative page index should return 400 Bad Request")
    void testNegativePageIndexReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("page", "-1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("less than zero")));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} should return single product details DTO")
    void testGetProductById() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + product1.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(product1.getId().intValue())))
                .andExpect(jsonPath("$.data.sku", is("CPU-RYZEN-7800X3D")))
                .andExpect(jsonPath("$.data.name", is("AMD Ryzen 7 7800X3D Processor")))
                .andExpect(jsonPath("$.data.category.name", is("Hardware")));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} for non-existent product should return 404 Not Found")
    void testGetNonExistentProductReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/products/999999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")));
    }

    @Test
    @DisplayName("GET /api/v1/categories should return available categories list")
    void testGetCategories() throws Exception {
        mockMvc.perform(get("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @DisplayName("Anonymous GET /api/v1/products must work without JWT token")
    void testAnonymousGetProductsWorksWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", notNullValue()));
    }

    @Test
    @DisplayName("Anonymous GET /api/v1/products/{id} must work without JWT token")
    void testAnonymousGetProductByIdWorksWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + product1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(product1.getId().intValue())));
    }

    @Test
    @DisplayName("Anonymous GET /api/v1/categories must work without JWT token")
    void testAnonymousGetCategoriesWorksWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", notNullValue()));
    }

    @Test
    @DisplayName("Public GET /api/v1/products with invalid/expired JWT must still succeed (200 OK)")
    void testPublicGetProductsWithInvalidTokenSucceeds() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer invalid.stale.expired.jwt.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @DisplayName("Public GET /api/v1/products/{id} with invalid/expired JWT must still succeed (200 OK)")
    void testPublicGetProductByIdWithInvalidTokenSucceeds() throws Exception {
        mockMvc.perform(get("/api/v1/products/" + product1.getId())
                        .header("Authorization", "Bearer invalid.stale.expired.jwt.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @DisplayName("Public GET /api/v1/categories with invalid/expired JWT must still succeed (200 OK)")
    void testPublicGetCategoriesWithInvalidTokenSucceeds() throws Exception {
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer invalid.stale.expired.jwt.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }
}
