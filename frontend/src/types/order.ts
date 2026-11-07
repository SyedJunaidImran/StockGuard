export interface OrderItemRequest {
  productId: number;
  quantity: number;
}

export interface OrderCreatePayload {
  items: OrderItemRequest[];
  notes?: string | null;
}

export interface OrderItemResponse {
  id: number;
  productId: number;
  productSku: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

export type OrderStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'PROCESSING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'FAILED';

export interface OrderResponse {
  id: number;
  orderNumber: string;
  customerId: number;
  customerEmail: string;
  status: OrderStatus;
  totalAmount: number;
  currency: string;
  notes?: string | null;
  items: OrderItemResponse[];
  createdAt: string;
  updatedAt: string;
}
