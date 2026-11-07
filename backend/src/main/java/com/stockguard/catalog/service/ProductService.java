package com.stockguard.catalog.service;

import com.stockguard.catalog.dto.ProductCreateRequest;
import com.stockguard.catalog.dto.ProductResponse;
import com.stockguard.catalog.dto.ProductUpdateRequest;
import com.stockguard.catalog.model.Category;
import com.stockguard.catalog.model.Product;
import com.stockguard.catalog.repository.ProductRepository;
import com.stockguard.catalog.repository.ProductSpecification;
import com.stockguard.common.dto.PageResponse;
import com.stockguard.common.exception.ResourceNotFoundException;
import com.stockguard.common.exception.SkuAlreadyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Service encapsulating public catalog browsing and administrative product lifecycle operations.
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "name",
            "price",
            "createdAt",
            "sku"
    );

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final com.stockguard.inventory.repository.InventoryRepository inventoryRepository;

    public ProductService(ProductRepository productRepository,
                          CategoryService categoryService,
                          com.stockguard.inventory.repository.InventoryRepository inventoryRepository) {
        this.productRepository = productRepository;
        this.categoryService = categoryService;
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Retrieves paginated products with dynamic search, category filtering, and sorting.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getProducts(
            String search,
            Long categoryId,
            Boolean active,
            int page,
            int size,
            String sortBy,
            String sortDir
    ) {
        validatePaginationAndSorting(page, size, sortBy, sortDir);

        Sort.Direction direction = Sort.Direction.fromString(sortDir);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Specification<Product> spec = ProductSpecification.filter(search, categoryId, active);
        Page<Product> productPage = productRepository.findAll(spec, pageable);

        return PageResponse.from(productPage, ProductResponse::fromEntity);
    }

    /**
     * Retrieves a single product by ID.
     */
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        return ProductResponse.fromEntity(product);
    }

    /**
     * Admin-only: Creates a new product catalog item with unique SKU verification.
     * Note: Does not accept or mutate inventory levels.
     */
    @Transactional
    public ProductResponse createProduct(ProductCreateRequest request) {
        String normalizedSku = request.getSku().trim().toUpperCase();

        if (productRepository.existsBySkuIgnoreCase(normalizedSku)) {
            log.warn("Product creation rejected: SKU '{}' already exists", normalizedSku);
            throw new SkuAlreadyExistsException("A product with SKU '" + normalizedSku + "' already exists");
        }

        Category category = categoryService.getCategoryEntity(request.getCategoryId());

        Product product = new Product(
                category,
                normalizedSku,
                request.getName().trim(),
                request.getDescription() != null ? request.getDescription().trim() : null,
                request.getPrice(),
                request.getImageUrl() != null ? request.getImageUrl().trim() : null,
                request.getActive() != null ? request.getActive() : true
        );

        try {
            Product savedProduct = productRepository.saveAndFlush(product);
            inventoryRepository.save(new com.stockguard.inventory.model.Inventory(savedProduct, 0, 10));
            log.info("Created product #{}: SKU '{}', Name '{}' with initial inventory", savedProduct.getId(), savedProduct.getSku(), savedProduct.getName());
            return ProductResponse.fromEntity(savedProduct);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Database constraint error during product creation for SKU '{}': {}", normalizedSku, ex.getMessage());
            throw new SkuAlreadyExistsException("A product with SKU '" + normalizedSku + "' already exists");
        }
    }

    /**
     * Admin-only: Updates existing product catalog fields.
     */
    @Transactional
    public ProductResponse updateProduct(Long id, ProductUpdateRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        String normalizedSku = request.getSku().trim().toUpperCase();

        if (!product.getSku().equalsIgnoreCase(normalizedSku) &&
                productRepository.existsBySkuIgnoreCaseAndIdNot(normalizedSku, id)) {
            log.warn("Product update rejected: SKU '{}' is already in use by another product", normalizedSku);
            throw new SkuAlreadyExistsException("A product with SKU '" + normalizedSku + "' already exists");
        }

        Category category = categoryService.getCategoryEntity(request.getCategoryId());

        product.setName(request.getName().trim());
        product.setSku(normalizedSku);
        product.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        product.setPrice(request.getPrice());
        product.setImageUrl(request.getImageUrl() != null ? request.getImageUrl().trim() : null);
        product.setCategory(category);
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }

        try {
            Product updatedProduct = productRepository.saveAndFlush(product);
            log.info("Updated product #{}: SKU '{}', Name '{}'", updatedProduct.getId(), updatedProduct.getSku(), updatedProduct.getName());
            return ProductResponse.fromEntity(updatedProduct);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Database constraint error updating product #{}: {}", id, ex.getMessage());
            throw new SkuAlreadyExistsException("A product with SKU '" + normalizedSku + "' already exists");
        }
    }

    /**
     * Admin-only: Updates active status of a product.
     */
    @Transactional
    public ProductResponse updateProductStatus(Long id, Boolean active) {
        if (active == null) {
            throw new IllegalArgumentException("Active status parameter must not be null");
        }

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        product.setActive(active);
        Product saved = productRepository.save(product);
        log.info("Product #{} active status updated to {}", id, active);

        return ProductResponse.fromEntity(saved);
    }

    private void validatePaginationAndSorting(int page, int size, String sortBy, String sortDir) {
        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be less than zero");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new IllegalArgumentException(
                    "Invalid sort field '" + sortBy + "'. Allowed sort fields are: " + String.join(", ", ALLOWED_SORT_FIELDS)
            );
        }
        if (!"ASC".equalsIgnoreCase(sortDir) && !"DESC".equalsIgnoreCase(sortDir)) {
            throw new IllegalArgumentException("Invalid sort direction '" + sortDir + "'. Allowed values are: ASC, DESC");
        }
    }
}
