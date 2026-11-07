export type AdjustmentType = 'ADD' | 'REMOVE';

export type MovementType =
  | 'RESTOCK'
  | 'MANUAL_ADJUSTMENT'
  | 'CORRECTION'
  | 'DAMAGED_WRITE_OFF'
  | 'INITIALIZATION'
  | 'ORDER_PURCHASE';

export interface Inventory {
  id: number;
  productId: number;
  productSku: string;
  productName: string;
  availableStock: number;
  reservedStock: number;
  lowStockThreshold: number;
  inStock: boolean;
  lowStock: boolean;
  updatedAt: string;
}

export interface InventoryMovement {
  id: number;
  inventoryId: number;
  productId: number;
  productSku: string;
  productName: string;
  quantityChange: number;
  previousStock: number;
  newStock: number;
  movementType: MovementType;
  reason: string;
  referenceId: string | null;
  actorId: number | null;
  actorEmail: string | null;
  actorName: string | null;
  createdAt: string;
}

export interface StockAdjustmentPayload {
  quantity: number;
  type: AdjustmentType;
  reason: string;
  referenceId?: string;
}
