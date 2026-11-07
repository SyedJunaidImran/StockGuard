import { apiClient } from './api';
import { ApiResponse } from '../types/auth';
import { PageResponse } from '../types/product';
import { OrderCreatePayload, OrderResponse } from '../types/order';

export const orderService = {
  /**
   * Places a customer order via POST /api/v1/orders.
   * Accepts product IDs, positive quantities, and an optional idempotency key.
   * Pricing, stock availability, and totals are computed strictly server-side.
   */
  async createOrder(payload: OrderCreatePayload, idempotencyKey?: string): Promise<OrderResponse> {
    const headers: Record<string, string> = {};
    if (idempotencyKey) {
      headers['Idempotency-Key'] = idempotencyKey;
    }

    const res = await apiClient<ApiResponse<OrderResponse>>('/orders', {
      method: 'POST',
      headers,
      body: JSON.stringify(payload),
    });
    return res.data;
  },

  /**
   * Fetches paginated order history strictly for the authenticated customer via GET /api/v1/orders.
   */
  async getMyOrders(page = 0, size = 10): Promise<PageResponse<OrderResponse>> {
    const res = await apiClient<ApiResponse<PageResponse<OrderResponse>>>(
      `/orders?page=${page}&size=${size}`,
      {
        method: 'GET',
      }
    );
    return res.data;
  },

  /**
   * Fetches a specific order by ID via GET /api/v1/orders/{id}.
   * Backend enforces customer ownership; non-owned orders return 404.
   */
  async getOrderById(id: number): Promise<OrderResponse> {
    const res = await apiClient<ApiResponse<OrderResponse>>(`/orders/${id}`, {
      method: 'GET',
    });
    return res.data;
  },
};
