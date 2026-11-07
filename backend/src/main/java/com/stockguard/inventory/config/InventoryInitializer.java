package com.stockguard.inventory.config;

import com.stockguard.inventory.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * ApplicationRunner ensuring all catalog products have a corresponding inventory record
 * upon application startup. Safe and idempotent.
 */
@Component
@Order(10)
public class InventoryInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InventoryInitializer.class);

    private final InventoryService inventoryService;

    public InventoryInitializer(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Running InventoryInitializer to ensure inventory coverage across all catalog products...");
        try {
            inventoryService.ensureInventoryExistsForAllProducts();
            log.info("InventoryInitializer completed successfully.");
        } catch (Exception ex) {
            log.error("Failed to run InventoryInitializer: {}", ex.getMessage(), ex);
        }
    }
}
