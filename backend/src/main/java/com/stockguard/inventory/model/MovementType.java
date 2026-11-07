package com.stockguard.inventory.model;

/**
 * Categorization of an inventory ledger movement.
 */
public enum MovementType {
    RESTOCK,
    MANUAL_ADJUSTMENT,
    CORRECTION,
    DAMAGED_WRITE_OFF,
    INITIALIZATION,
    ORDER_PURCHASE
}
