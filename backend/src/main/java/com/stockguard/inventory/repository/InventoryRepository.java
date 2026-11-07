package com.stockguard.inventory.repository;

import com.stockguard.inventory.model.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.stockguard.catalog.model.Product;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @EntityGraph(attributePaths = {"product"})
    Optional<Inventory> findByProductId(Long productId);

    /**
     * Acquires a pessimistic write lock (SELECT ... FOR UPDATE) on the inventory row
     * to serialize concurrent updates and prevent lost updates or race conditions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId")
    Optional<Inventory> findByProductIdWithLock(@Param("productId") Long productId);

    boolean existsByProductId(Long productId);

    @EntityGraph(attributePaths = {"product", "product.category"})
    @Query("SELECT i FROM Inventory i")
    Page<Inventory> findAllWithProduct(Pageable pageable);

    /**
     * Finds all catalog products that currently do not have a corresponding inventory row.
     */
    @Query("SELECT p FROM Product p WHERE NOT EXISTS (SELECT i.id FROM Inventory i WHERE i.product.id = p.id)")
    List<Product> findProductsWithoutInventory();
}
