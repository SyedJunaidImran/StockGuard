package com.stockguard.inventory.repository;

import com.stockguard.inventory.model.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    @EntityGraph(attributePaths = {"product", "actor"})
    @Query("SELECT m FROM InventoryMovement m WHERE m.product.id = :productId")
    Page<InventoryMovement> findByProductId(@Param("productId") Long productId, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "actor"})
    @Query("SELECT m FROM InventoryMovement m WHERE m.inventory.id = :inventoryId")
    Page<InventoryMovement> findByInventoryId(@Param("inventoryId") Long inventoryId, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "actor"})
    @Query("SELECT m FROM InventoryMovement m")
    Page<InventoryMovement> findAllWithDetails(Pageable pageable);
}
