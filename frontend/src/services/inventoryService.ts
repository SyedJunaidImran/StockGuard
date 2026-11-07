import { apiClient } from './api';
import { ApiResponse } from '../types/auth';
import { PageResponse } from '../types/product';
import { Inventory, InventoryMovement, StockAdjustmentPayload } from '../types/inventory';

export const inventoryService = {
  /**
   * Fetches paginated inventory overview across all products (ADMIN only)
   */
  async getAllInventory(page = 0, size = 20): Promise<PageResponse<Inventory>> {
    const res = await apiClient<ApiResponse<PageResponse<Inventory>>>(
      `/admin/inventory?page=${page}&size=${size}`,
      { method: 'GET' }
    );
    return res.data;
  },

  /**
   * Fetches inventory levels for a specific product
   */
  async getInventoryByProductId(productId: number): Promise<Inventory> {
    const res = await apiClient<ApiResponse<Inventory>>(
      `/admin/inventory/${productId}`,
      { method: 'GET' }
    );
    return res.data;
  },

  /**
   * Executes an administrative stock adjustment (ADD or REMOVE) under pessimistic write lock
   */
  async adjustStock(productId: number, payload: StockAdjustmentPayload): Promise<Inventory> {
    const res = await apiClient<ApiResponse<Inventory>>(
      `/admin/inventory/${productId}/adjustments`,
      {
        method: 'POST',
        body: JSON.stringify(payload),
      }
    );
    return res.data;
  },

  /**
   * Fetches paginated global movement audit ledger across all products (ADMIN only)
   */
  async getAllMovements(page = 0, size = 15): Promise<PageResponse<InventoryMovement>> {
    const res = await apiClient<ApiResponse<PageResponse<InventoryMovement>>>(
      `/admin/inventory/movements?page=${page}&size=${size}`,
      { method: 'GET' }
    );
    return res.data;
  },

  /**
   * Fetches paginated movement audit ledger for a specific product
   */
  async getMovementsByProductId(productId: number, page = 0, size = 15): Promise<PageResponse<InventoryMovement>> {
    const res = await apiClient<ApiResponse<PageResponse<InventoryMovement>>>(
      `/admin/inventory/${productId}/movements?page=${page}&size=${size}`,
      { method: 'GET' }
    );
    return res.data;
  },
};
