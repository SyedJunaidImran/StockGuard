import { apiClient } from './api';
import { ApiResponse } from '../types/auth';
import {
  Category,
  PageResponse,
  Product,
  ProductCreatePayload,
  ProductFilterParams,
  ProductUpdatePayload,
} from '../types/product';

export const productService = {
  /**
   * Fetches paginated products with search, category filtering, and sorting
   */
  async getProducts(params: ProductFilterParams = {}): Promise<PageResponse<Product>> {
    const query = new URLSearchParams();

    if (params.search && params.search.trim()) {
      query.set('search', params.search.trim());
    }
    if (params.categoryId !== undefined && params.categoryId !== null) {
      query.set('categoryId', params.categoryId.toString());
    }
    if (params.active !== undefined) {
      query.set('active', params.active.toString());
    }
    if (params.page !== undefined) {
      query.set('page', params.page.toString());
    }
    if (params.size !== undefined) {
      query.set('size', params.size.toString());
    }
    if (params.sortBy) {
      query.set('sortBy', params.sortBy);
    }
    if (params.sortDir) {
      query.set('sortDir', params.sortDir);
    }

    const endpoint = `/products${query.toString() ? `?${query.toString()}` : ''}`;
    const res = await apiClient<ApiResponse<PageResponse<Product>>>(endpoint, {
      method: 'GET',
    });
    return res.data;
  },

  /**
   * Fetches single product details
   */
  async getProductById(id: number): Promise<Product> {
    const res = await apiClient<ApiResponse<Product>>(`/products/${id}`, {
      method: 'GET',
    });
    return res.data;
  },

  /**
   * Fetches all product categories
   */
  async getCategories(): Promise<Category[]> {
    const res = await apiClient<ApiResponse<Category[]>>('/categories', {
      method: 'GET',
    });
    return res.data;
  },

  /**
   * Admin-only: Creates a new product
   */
  async createProduct(payload: ProductCreatePayload): Promise<Product> {
    const res = await apiClient<ApiResponse<Product>>('/admin/products', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
    return res.data;
  },

  /**
   * Admin-only: Updates existing product
   */
  async updateProduct(id: number, payload: ProductUpdatePayload): Promise<Product> {
    const res = await apiClient<ApiResponse<Product>>(`/admin/products/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    });
    return res.data;
  },

  /**
   * Admin-only: Toggles active status
   */
  async updateProductStatus(id: number, active: boolean): Promise<Product> {
    const res = await apiClient<ApiResponse<Product>>(`/admin/products/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ active }),
    });
    return res.data;
  },
};
