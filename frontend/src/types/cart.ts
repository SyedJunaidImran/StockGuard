/**
 * Client-side cart item representation.
 * Contains only non-sensitive display properties.
 * Price is display-only; the server is authoritative during order placement.
 */
export interface CartItem {
  productId: number;
  sku: string;
  name: string;
  price: number; // Display estimate only
  imageUrl?: string | null;
  quantity: number;
}
