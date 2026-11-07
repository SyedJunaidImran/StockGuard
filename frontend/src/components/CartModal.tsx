import React, { useState, useRef, useEffect } from 'react';
import { useCart } from '../context/CartContext';
import { useAuth } from '../context/AuthContext';
import { orderService } from '../services/orderService';
import { OrderResponse } from '../types/order';
import { ApiError } from '../services/api';
import { ProductThumbnail } from './ProductThumbnail';
import {
  X,
  ShoppingCart,
  Trash2,
  Plus,
  Minus,
  AlertCircle,
  ShieldCheck,
  ArrowRight,
  ArrowLeft,
  Loader2,
  Lock,
  RefreshCw,
  AlertTriangle
} from 'lucide-react';

interface CartModalProps {
  isOpen: boolean;
  onClose: () => void;
  onOrderPlaced: (order: OrderResponse) => void;
  onOpenAuth: () => void;
}

export const CartModal: React.FC<CartModalProps> = ({
  isOpen,
  onClose,
  onOrderPlaced,
  onOpenAuth,
}) => {
  const { items, updateQuantity, removeItem, clearCart, estimatedTotal, totalCount } = useCart();
  const { user, isAuthenticated } = useAuth();
  const isCustomer = isAuthenticated && user?.role === 'CUSTOMER';

  const [notes, setNotes] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const checkoutKeyRef = useRef<string | null>(null);

  // Invalidate idempotency key if user alters cart items or order notes
  useEffect(() => {
    checkoutKeyRef.current = null;
  }, [items, notes]);

  if (!isOpen) return null;

  const handleContinueShopping = () => {
    onClose();
    const catalogEl = document.getElementById('catalog-section');
    if (catalogEl) {
      catalogEl.scrollIntoView({ behavior: 'smooth' });
    }
  };

  const handlePlaceOrder = async () => {
    if (items.length === 0 || isSubmitting) return;

    if (!isAuthenticated) {
      setSubmitError('Please sign in with a Customer account to place an order.');
      onOpenAuth();
      return;
    }

    if (!isCustomer) {
      setSubmitError('Administrative accounts cannot place customer orders. Please sign in as a Customer.');
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);

    // Reuse in-flight/retry key if payload has not changed, otherwise generate a fresh UUID
    if (!checkoutKeyRef.current) {
      checkoutKeyRef.current = crypto.randomUUID();
    }

    try {
      // Send ONLY product IDs, quantities, and optional notes.
      // Server calculates all prices, snapshots, and order totals authoritatively under row lock.
      const payload = {
        items: items.map((it) => ({
          productId: it.productId,
          quantity: it.quantity,
        })),
        notes: notes.trim() ? notes.trim() : null,
      };

      const placedOrder = await orderService.createOrder(payload, checkoutKeyRef.current);
      checkoutKeyRef.current = null;
      clearCart();
      setNotes('');
      onOrderPlaced(placedOrder);
      onClose();
    } catch (err: unknown) {
      // On failure, cart items are strictly PRESERVED
      if (err instanceof ApiError) {
        setSubmitError(err.message);
      } else if (err instanceof Error) {
        setSubmitError(err.message);
      } else {
        setSubmitError('Failed to place order. Please verify stock availability and try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const isStockError =
    submitError &&
    (submitError.toLowerCase().includes('stock') ||
      submitError.toLowerCase().includes('insufficient') ||
      submitError.toLowerCase().includes('available'));

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="modal-dialog"
        style={{ maxWidth: '720px', width: '100%', maxHeight: '90vh', display: 'flex', flexDirection: 'column' }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="modal-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem' }}>
            <div
              style={{
                width: '36px',
                height: '36px',
                borderRadius: 'var(--radius-sm)',
                background: 'rgba(56, 189, 248, 0.15)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'var(--accent-cyan)',
                flexShrink: 0,
              }}
            >
              <ShoppingCart size={18} />
            </div>
            <div>
              <h3 style={{ margin: 0, fontSize: '1.2rem', fontWeight: 600 }}>
                Shopping Cart
              </h3>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                {totalCount} {totalCount === 1 ? 'item' : 'items'} in your active session
              </span>
            </div>
          </div>

          <button
            onClick={onClose}
            className="btn-secondary"
            style={{ padding: '0.35rem 0.5rem', border: 'none', background: 'transparent' }}
            title="Close Cart"
            aria-label="Close cart dialog"
          >
            <X size={18} />
          </button>
        </div>

        {/* Body */}
        <div className="modal-body" style={{ overflowY: 'auto' }}>
          {items.length === 0 ? (
            /* Professional Empty Cart State */
            <div style={{ textAlign: 'center', padding: '3.5rem 1.5rem' }}>
              <div
                style={{
                  width: '64px',
                  height: '64px',
                  borderRadius: 'var(--radius-full)',
                  background: 'rgba(56, 189, 248, 0.08)',
                  border: '1px solid rgba(56, 189, 248, 0.25)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  margin: '0 auto 1.25rem',
                  color: 'var(--accent-cyan)',
                }}
              >
                <ShoppingCart size={32} />
              </div>
              <h4 style={{ fontSize: '1.25rem', fontWeight: 600, marginBottom: '0.5rem', color: '#f8fafc' }}>
                Your Cart is Empty
              </h4>
              <p
                style={{
                  fontSize: '0.88rem',
                  color: 'var(--text-secondary)',
                  maxWidth: '420px',
                  margin: '0 auto 1.75rem',
                  lineHeight: 1.5,
                }}
              >
                Explore our catalog to select enterprise components and experience real-time, concurrency-safe inventory allocation.
              </p>
              <button
                onClick={handleContinueShopping}
                className="btn-primary"
                style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', padding: '0.65rem 1.4rem' }}
              >
                <span>Continue Shopping</span>
                <ArrowRight size={16} />
              </button>
            </div>
          ) : (
            <div>
              {/* Idempotency Protection Indicator Banner */}
              <div
                style={{
                  marginBottom: '1.25rem',
                  padding: '0.75rem 1rem',
                  background: 'rgba(16, 185, 129, 0.08)',
                  border: '1px solid rgba(16, 185, 129, 0.25)',
                  borderRadius: 'var(--radius-sm)',
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: '0.65rem',
                  fontSize: '0.78rem',
                  color: 'var(--text-secondary)',
                  lineHeight: 1.45,
                }}
              >
                <RefreshCw size={15} style={{ color: 'var(--accent-emerald)', flexShrink: 0, marginTop: '2px' }} />
                <div>
                  <strong style={{ color: 'var(--accent-emerald)' }}>Duplicate Order Protection:</strong> Safe checkout ensures network retries or multiple clicks never duplicate your order or double-charge stock.
                </div>
              </div>

              {/* Cart Items List */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                {items.map((item) => (
                  <div
                    key={item.productId}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '0.85rem 1rem',
                      background: 'rgba(15, 23, 42, 0.55)',
                      border: '1px solid var(--border-subtle)',
                      borderRadius: 'var(--radius-sm)',
                      gap: '1rem',
                      flexWrap: 'wrap',
                    }}
                  >
                    {/* Item Thumbnail */}
                    <div
                      style={{
                        width: '52px',
                        height: '52px',
                        borderRadius: 'var(--radius-sm)',
                        overflow: 'hidden',
                        border: '1px solid var(--border-subtle)',
                        flexShrink: 0,
                      }}
                    >
                      <ProductThumbnail
                        src={item.imageUrl}
                        alt={item.name}
                        sku={item.sku}
                        aspectRatio="square"
                        height={52}
                      />
                    </div>

                    {/* Product Details */}
                    <div style={{ flex: '1 1 200px', minWidth: 0 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem', marginBottom: '0.2rem' }}>
                        <span className="product-sku-pill" style={{ fontSize: '0.68rem' }}>
                          {item.sku}
                        </span>
                      </div>
                      <div
                        style={{
                          fontWeight: 600,
                          fontSize: '0.92rem',
                          color: '#f8fafc',
                          whiteSpace: 'nowrap',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                        }}
                        title={item.name}
                      >
                        {item.name}
                      </div>
                      <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                        ${item.price.toFixed(2)} each
                      </div>
                    </div>

                    {/* Quantity Selector */}
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        background: 'rgba(255, 255, 255, 0.05)',
                        border: '1px solid var(--border-subtle)',
                        borderRadius: 'var(--radius-sm)',
                        padding: '0.15rem',
                      }}
                    >
                      <button
                        onClick={() => updateQuantity(item.productId, item.quantity - 1)}
                        className="btn-secondary"
                        style={{ padding: '0.25rem 0.45rem', border: 'none', background: 'transparent' }}
                        title="Decrease quantity"
                        aria-label="Decrease quantity"
                      >
                        <Minus size={13} />
                      </button>

                      <span
                        style={{
                          minWidth: '32px',
                          textAlign: 'center',
                          fontSize: '0.85rem',
                          fontWeight: 600,
                          fontFamily: 'var(--font-mono)',
                        }}
                      >
                        {item.quantity}
                      </span>

                      <button
                        onClick={() => updateQuantity(item.productId, item.quantity + 1)}
                        className="btn-secondary"
                        style={{ padding: '0.25rem 0.45rem', border: 'none', background: 'transparent' }}
                        title="Increase quantity"
                        aria-label="Increase quantity"
                      >
                        <Plus size={13} />
                      </button>
                    </div>

                    {/* Subtotal */}
                    <div style={{ textAlign: 'right', minWidth: '75px' }}>
                      <div style={{ fontWeight: 700, fontSize: '0.95rem', color: '#f8fafc' }}>
                        ${(item.price * item.quantity).toFixed(2)}
                      </div>
                    </div>

                    {/* Remove Button */}
                    <button
                      onClick={() => removeItem(item.productId)}
                      className="btn-secondary"
                      style={{ padding: '0.35rem 0.45rem', border: 'none', color: 'var(--accent-rose)' }}
                      title="Remove item"
                      aria-label={`Remove ${item.name}`}
                    >
                      <Trash2 size={15} />
                    </button>
                  </div>
                ))}
              </div>

              {/* Order Notes Field */}
              <div style={{ marginTop: '1.25rem' }}>
                <label
                  style={{
                    display: 'block',
                    fontSize: '0.78rem',
                    color: 'var(--text-secondary)',
                    marginBottom: '0.4rem',
                    fontWeight: 500,
                  }}
                  htmlFor="cart-order-notes"
                >
                  Order Delivery Instructions / Notes (Optional)
                </label>
                <textarea
                  id="cart-order-notes"
                  className="form-input"
                  rows={2}
                  maxLength={500}
                  placeholder="e.g. Please verify hardware packaging seal, deliver to receiving dock..."
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  style={{ resize: 'vertical', fontSize: '0.85rem' }}
                />
                <div style={{ textAlign: 'right', fontSize: '0.7rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                  {notes.length} / 500 characters
                </div>
              </div>

              {/* Server-Authoritative Disclaimer */}
              <div
                style={{
                  marginTop: '1rem',
                  padding: '0.75rem 0.9rem',
                  background: 'rgba(56, 189, 248, 0.05)',
                  border: '1px solid rgba(56, 189, 248, 0.2)',
                  borderRadius: 'var(--radius-sm)',
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: '0.5rem',
                  fontSize: '0.78rem',
                  color: 'var(--text-secondary)',
                  lineHeight: 1.45,
                }}
              >
                <ShieldCheck size={16} style={{ color: 'var(--accent-cyan)', flexShrink: 0, marginTop: '2px' }} />
                <div>
                  <strong>Server-Authoritative Settlement:</strong> Display subtotals are frontend catalog estimates. Authoritative financial totals, unit price snapshots, and inventory deductions are computed atomically by the backend inside a single PostgreSQL transaction.
                </div>
              </div>

              {/* Error Alert */}
              {submitError && (
                <div
                  className="alert-box error"
                  style={{
                    marginTop: '1rem',
                    background: isStockError ? 'rgba(245, 158, 11, 0.12)' : undefined,
                    borderColor: isStockError ? 'var(--border-amber)' : undefined,
                    color: isStockError ? '#fef3c7' : undefined,
                  }}
                >
                  {isStockError ? (
                    <AlertTriangle size={18} style={{ color: 'var(--accent-amber)', flexShrink: 0 }} />
                  ) : (
                    <AlertCircle size={18} style={{ flexShrink: 0 }} />
                  )}
                  <div style={{ fontSize: '0.85rem', flex: 1 }}>
                    <div style={{ fontWeight: 600, marginBottom: '0.2rem' }}>
                      {isStockError ? 'Inventory Allocation Constraint' : 'Order Placement Blocked'}
                    </div>
                    <div>{submitError}</div>
                    {isStockError && (
                      <div style={{ marginTop: '0.35rem', fontSize: '0.75rem', opacity: 0.9 }}>
                        Tip: Reduce item quantity or check the live catalog stock status before retrying.
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Footer */}
        {items.length > 0 && (
          <div
            className="modal-footer"
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '1.25rem 1.5rem',
              flexWrap: 'wrap',
              gap: '1rem',
            }}
          >
            <div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Estimated Cart Total</div>
              <div style={{ fontSize: '1.45rem', fontWeight: 700, color: 'var(--accent-cyan)' }}>
                ${estimatedTotal.toFixed(2)}{' '}
                <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)', fontWeight: 500 }}>USD</span>
              </div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem', flexWrap: 'wrap' }}>
              <button
                type="button"
                onClick={handleContinueShopping}
                className="btn-secondary"
                style={{ fontSize: '0.8rem', display: 'inline-flex', alignItems: 'center', gap: '0.35rem' }}
              >
                <ArrowLeft size={14} />
                <span>Continue Shopping</span>
              </button>

              <button
                type="button"
                onClick={clearCart}
                disabled={isSubmitting}
                className="btn-secondary"
                style={{ fontSize: '0.8rem' }}
              >
                Clear Cart
              </button>

              <button
                type="button"
                onClick={handlePlaceOrder}
                disabled={isSubmitting}
                className="btn-primary"
                style={{
                  width: 'auto',
                  marginTop: 0,
                  padding: '0.65rem 1.4rem',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '0.5rem',
                }}
              >
                {isSubmitting ? (
                  <>
                    <Loader2 size={16} className="animate-spin" />
                    <span>Processing Order...</span>
                  </>
                ) : !isAuthenticated ? (
                  <>
                    <Lock size={15} />
                    <span>Sign in to Checkout</span>
                  </>
                ) : (
                  <>
                    <ShieldCheck size={16} />
                    <span>Place Order</span>
                  </>
                )}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
