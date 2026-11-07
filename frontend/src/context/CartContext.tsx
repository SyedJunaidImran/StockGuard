import React, { createContext, useContext, useState, useEffect, useRef, useCallback } from 'react';
import { Product } from '../types/product';
import { CartItem } from '../types/cart';
import { useAuth } from './AuthContext';

interface CartContextType {
  items: CartItem[];
  addItem: (product: Product, quantity?: number) => void;
  removeItem: (productId: number) => void;
  updateQuantity: (productId: number, quantity: number) => void;
  clearCart: () => void;
  totalCount: number;
  estimatedTotal: number;
}

const CartContext = createContext<CartContextType | undefined>(undefined);

export const CartProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user } = useAuth();
  const [items, setItems] = useState<CartItem[]>([]);
  const previousUserIdRef = useRef<number | null | undefined>(undefined);

  // Helper to get storage key scoped to customer's stable ID
  const getStorageKey = (userId: number | undefined) => {
    return userId ? `stockguard_cart_${userId}` : null;
  };

  // Synchronize cart state on customer login, switch, or logout
  useEffect(() => {
    const currentUserId = user?.id;
    const prevUserId = previousUserIdRef.current;

    // First mount or user changed
    if (prevUserId !== currentUserId) {
      if (!currentUserId) {
        // Logged out or account switched to anonymous: clear active cart state
        setItems([]);
      } else {
        // Customer authenticated: load scoped cart from localStorage
        const key = getStorageKey(currentUserId);
        if (key) {
          const stored = localStorage.getItem(key);
          if (stored) {
            try {
              const parsed = JSON.parse(stored);
              if (Array.isArray(parsed)) {
                // Sanitize items: ensure no tokens or unexpected fields
                const sanitized: CartItem[] = parsed.map((it) => ({
                  productId: Number(it.productId),
                  sku: String(it.sku || ''),
                  name: String(it.name || ''),
                  price: Number(it.price || 0),
                  imageUrl: it.imageUrl ? String(it.imageUrl) : undefined,
                  quantity: Math.max(1, Number(it.quantity || 1)),
                }));
                setItems(sanitized);
              } else {
                setItems([]);
              }
            } catch {
              setItems([]);
            }
          } else {
            setItems([]);
          }
        }
      }
      previousUserIdRef.current = currentUserId;
    }
  }, [user]);

  // Persist items scoped to authenticated customer whenever items change
  const persistItems = useCallback(
    (newItems: CartItem[]) => {
      if (user?.id) {
        const key = getStorageKey(user.id);
        if (key) {
          if (newItems.length === 0) {
            localStorage.removeItem(key);
          } else {
            localStorage.setItem(key, JSON.stringify(newItems));
          }
        }
      }
    },
    [user]
  );

  const addItem = useCallback(
    (product: Product, quantity = 1) => {
      if (quantity <= 0) return;
      setItems((prev) => {
        const existingIndex = prev.findIndex((item) => item.productId === product.id);
        let next: CartItem[];
        if (existingIndex > -1) {
          next = prev.map((item, idx) =>
            idx === existingIndex
              ? { ...item, quantity: item.quantity + quantity }
              : item
          );
        } else {
          next = [
            ...prev,
            {
              productId: product.id,
              sku: product.sku,
              name: product.name,
              price: product.price,
              imageUrl: product.imageUrl,
              quantity,
            },
          ];
        }
        persistItems(next);
        return next;
      });
    },
    [persistItems]
  );

  const removeItem = useCallback(
    (productId: number) => {
      setItems((prev) => {
        const next = prev.filter((item) => item.productId !== productId);
        persistItems(next);
        return next;
      });
    },
    [persistItems]
  );

  const updateQuantity = useCallback(
    (productId: number, quantity: number) => {
      setItems((prev) => {
        let next: CartItem[];
        if (quantity <= 0) {
          next = prev.filter((item) => item.productId !== productId);
        } else {
          next = prev.map((item) =>
            item.productId === productId ? { ...item, quantity } : item
          );
        }
        persistItems(next);
        return next;
      });
    },
    [persistItems]
  );

  const clearCart = useCallback(() => {
    setItems([]);
    if (user?.id) {
      const key = getStorageKey(user.id);
      if (key) {
        localStorage.removeItem(key);
      }
    }
  }, [user]);

  const totalCount = items.reduce((sum, item) => sum + item.quantity, 0);
  const estimatedTotal = items.reduce(
    (sum, item) => sum + item.price * item.quantity,
    0
  );

  return (
    <CartContext.Provider
      value={{
        items,
        addItem,
        removeItem,
        updateQuantity,
        clearCart,
        totalCount,
        estimatedTotal,
      }}
    >
      {children}
    </CartContext.Provider>
  );
};

export const useCart = (): CartContextType => {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart must be used within a CartProvider');
  }
  return context;
};
