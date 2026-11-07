import React, { useState, useEffect } from 'react';
import { Product } from '../types/product';
import { Inventory } from '../types/inventory';
import { useCart } from '../context/CartContext';
import { ProductThumbnail } from './ProductThumbnail';
import {
  X,
  Tag,
  Calendar,
  Layers,
  ShieldCheck,
  ShoppingCart,
  CheckCircle2,
  Minus,
  Plus,
  Boxes,
  Lock,
  AlertTriangle
} from 'lucide-react';

interface ProductDetailModalProps {
  product: Product | null;
  inventory?: Inventory | null;
  onClose: () => void;
}

export const ProductDetailModal: React.FC<ProductDetailModalProps> = ({
  product,
  inventory,
  onClose,
}) => {
  const { addItem } = useCart();
  const [quantity, setQuantity] = useState(1);
  const [added, setAdded] = useState(false);

  const hasInventory = inventory !== undefined && inventory !== null;
  const availableStock = hasInventory ? inventory.availableStock : null;
  const isOutOfStock = hasInventory ? (availableStock ?? 0) <= 0 : false;
  const isLowStock = hasInventory
    ? (inventory.lowStock || ((availableStock ?? 0) > 0 && (availableStock ?? 0) <= inventory.lowStockThreshold))
    : false;
  const maxQuantity = hasInventory ? Math.max(0, availableStock ?? 0) : 99;

  useEffect(() => {
    if (product) {
      if (isOutOfStock || !product.active) {
        setQuantity(0);
      } else {
        setQuantity(1);
      }
      setAdded(false);
    }
  }, [product, isOutOfStock]);

  if (!product) return null;

  const subtotal = ((isOutOfStock || !product.active ? 0 : quantity) * product.price).toFixed(2);

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="modal-dialog"
        style={{ maxWidth: '680px', width: '100%', maxHeight: '90vh', display: 'flex', flexDirection: 'column' }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Modal Header */}
        <div className="modal-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', flexWrap: 'wrap' }}>
            <span className="product-category-tag">
              {product.category ? product.category.name : 'Uncategorized'}
            </span>
            <span className="product-sku-pill">{product.sku}</span>
          </div>

          <button
            onClick={onClose}
            className="btn-secondary"
            style={{ padding: '0.35rem 0.5rem', border: 'none', background: 'transparent' }}
            aria-label="Close dialog"
          >
            <X size={18} />
          </button>
        </div>

        {/* Modal Body */}
        <div className="modal-body" style={{ overflowY: 'auto' }}>
          {/* Product Thumbnail */}
          <div
            style={{
              marginBottom: '1.25rem',
              borderRadius: 'var(--radius-sm)',
              overflow: 'hidden',
              border: '1px solid var(--border-subtle)',
            }}
          >
            <ProductThumbnail
              src={product.imageUrl}
              alt={product.name}
              categoryName={product.category?.name}
              sku={product.sku}
              aspectRatio="video"
              height={220}
            />
          </div>

          {/* Title & Price Header */}
          <div
            style={{
              display: 'flex',
              alignItems: 'flex-start',
              justifyContent: 'space-between',
              gap: '1rem',
              marginBottom: '1rem',
              flexWrap: 'wrap',
            }}
          >
            <div style={{ flex: '1 1 300px' }}>
              <h2 style={{ fontSize: '1.35rem', fontWeight: 700, lineHeight: 1.3, marginBottom: '0.5rem' }}>
                {product.name}
              </h2>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', flexWrap: 'wrap' }}>
                {hasInventory ? (
                  isOutOfStock ? (
                    <span className="stock-badge out-of-stock">
                      <span className="stock-badge-dot" />
                      Out of Stock (0 Available)
                    </span>
                  ) : isLowStock ? (
                    <span className="stock-badge low-stock">
                      <span className="stock-badge-dot" />
                      Low Stock: Only {availableStock} Remaining
                    </span>
                  ) : (
                    <span className="stock-badge in-stock">
                      <span className="stock-badge-dot" />
                      In Stock: {availableStock} Units Available
                    </span>
                  )
                ) : product.active ? (
                  <span className="stock-badge in-stock">
                    <span className="stock-badge-dot" />
                    In Stock
                  </span>
                ) : (
                  <span className="stock-badge inactive">
                    <span className="stock-badge-dot" />
                    Catalog Inactive
                  </span>
                )}

                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                  Product ID #{product.id}
                </span>
              </div>
            </div>

            <div style={{ textAlign: 'right', flexShrink: 0 }}>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'block' }}>Unit Price</span>
              <span className="product-card-price" style={{ fontSize: '1.75rem' }}>
                ${product.price.toFixed(2)}
              </span>
            </div>
          </div>

          {/* Live Inventory Telemetry Box (when inventory data is mapped) */}
          {hasInventory && (
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))',
                gap: '0.75rem',
                background: 'rgba(15, 23, 42, 0.55)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-sm)',
                padding: '0.85rem 1rem',
                marginBottom: '1.25rem',
              }}
            >
              <div>
                <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em', display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
                  <Boxes size={12} style={{ color: isOutOfStock ? 'var(--accent-rose)' : isLowStock ? 'var(--accent-amber)' : 'var(--accent-emerald)' }} />
                  <span>Available Stock</span>
                </div>
                <div style={{ fontSize: '1.15rem', fontWeight: 700, fontFamily: 'var(--font-mono)', color: isOutOfStock ? 'var(--accent-rose)' : isLowStock ? 'var(--accent-amber)' : 'var(--accent-emerald)', marginTop: '0.2rem' }}>
                  {inventory.availableStock}
                </div>
              </div>

              <div>
                <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em', display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
                  <Lock size={12} style={{ color: 'var(--accent-indigo)' }} />
                  <span>Reserved Stock</span>
                </div>
                <div style={{ fontSize: '1.15rem', fontWeight: 700, fontFamily: 'var(--font-mono)', color: 'var(--accent-indigo)', marginTop: '0.2rem' }}>
                  {inventory.reservedStock}
                </div>
              </div>

              <div>
                <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em', display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
                  <AlertTriangle size={12} style={{ color: 'var(--text-secondary)' }} />
                  <span>Low Threshold</span>
                </div>
                <div style={{ fontSize: '1.15rem', fontWeight: 700, fontFamily: 'var(--font-mono)', color: 'var(--text-secondary)', marginTop: '0.2rem' }}>
                  {inventory.lowStockThreshold}
                </div>
              </div>
            </div>
          )}

          {/* Description */}
          <div
            style={{
              background: 'rgba(15, 23, 42, 0.4)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius-sm)',
              padding: '1rem',
              marginBottom: '1.25rem',
            }}
          >
            <h4
              style={{
                fontSize: '0.78rem',
                textTransform: 'uppercase',
                color: 'var(--text-secondary)',
                letterSpacing: '0.05em',
                marginBottom: '0.4rem',
              }}
            >
              Product Specifications &amp; Overview
            </h4>
            <p style={{ fontSize: '0.88rem', color: 'var(--text-primary)', lineHeight: 1.6, whiteSpace: 'pre-wrap' }}>
              {product.description || 'No detailed description provided for this catalog entry.'}
            </p>
          </div>

          {/* Metadata Grid */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
              gap: '0.75rem',
              fontSize: '0.8rem',
              color: 'var(--text-secondary)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
              <Layers size={14} style={{ color: 'var(--accent-cyan)' }} />
              <span>Category: {product.category?.name || 'None'}</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
              <Tag size={14} style={{ color: 'var(--accent-emerald)' }} />
              <span>SKU: {product.sku}</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
              <Calendar size={14} style={{ color: 'var(--accent-indigo)' }} />
              <span>Listed: {new Date(product.createdAt).toLocaleDateString()}</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
              <ShieldCheck size={14} style={{ color: 'var(--accent-cyan)' }} />
              <span>Verified Authentic</span>
            </div>
          </div>
        </div>

        {/* Modal Footer */}
        <div
          className="modal-footer"
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '1rem',
            flexWrap: 'wrap',
          }}
        >
          {/* Quantity Controls & Dynamic Subtotal */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem', flexWrap: 'wrap' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>Quantity:</span>
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
                  type="button"
                  onClick={() => setQuantity((q) => Math.max(1, q - 1))}
                  className="btn-secondary"
                  style={{ padding: '0.25rem 0.45rem', border: 'none', background: 'transparent' }}
                  disabled={!product.active || isOutOfStock || quantity <= 1}
                  aria-label="Decrease quantity"
                >
                  <Minus size={12} />
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
                  {isOutOfStock ? 0 : quantity}
                </span>
                <button
                  type="button"
                  onClick={() => setQuantity((q) => Math.min(maxQuantity, q + 1))}
                  className="btn-secondary"
                  style={{ padding: '0.25rem 0.45rem', border: 'none', background: 'transparent' }}
                  disabled={!product.active || isOutOfStock || quantity >= maxQuantity}
                  aria-label="Increase quantity"
                >
                  <Plus size={12} />
                </button>
              </div>

              {hasInventory && !isOutOfStock && (
                <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
                  (Max: {maxQuantity})
                </span>
              )}
            </div>

            {/* Subtotal Display */}
            <div style={{ display: 'flex', alignItems: 'baseline', gap: '0.35rem' }}>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Subtotal:</span>
              <span style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--accent-cyan)' }}>
                ${subtotal}
              </span>
            </div>
          </div>

          {/* Action Buttons */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <button onClick={onClose} className="btn-secondary">
              Close
            </button>

            <button
              onClick={() => {
                if (quantity > 0) {
                  addItem(product, quantity);
                  setAdded(true);
                  setTimeout(() => setAdded(false), 1500);
                }
              }}
              disabled={!product.active || isOutOfStock || quantity < 1}
              className="btn-primary"
              style={{
                width: 'auto',
                marginTop: 0,
                padding: '0.55rem 1.25rem',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.5rem',
                opacity: !product.active || isOutOfStock ? 0.5 : 1,
                cursor: !product.active || isOutOfStock ? 'not-allowed' : 'pointer',
              }}
            >
              {added ? (
                <>
                  <CheckCircle2 size={16} />
                  <span>Added {quantity} to Cart</span>
                </>
              ) : (
                <>
                  <ShoppingCart size={16} />
                  <span>
                    {!product.active
                      ? 'Item Inactive'
                      : isOutOfStock
                      ? 'Out of Stock'
                      : `Add ${quantity} to Cart`}
                  </span>
                </>
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
