import React, { useState, useEffect } from 'react';
import { Category, Product, ProductCreatePayload, ProductUpdatePayload } from '../types/product';
import { productService } from '../services/productService';
import { ApiError } from '../services/api';
import { ProductThumbnail } from './ProductThumbnail';
import {
  X,
  Tag,
  DollarSign,
  Layers,
  Image as ImageIcon,
  AlertCircle,
  CheckCircle2,
  ShieldCheck
} from 'lucide-react';

interface AdminProductModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (product: Product) => void;
  productToEdit?: Product | null;
  categories: Category[];
}

export const AdminProductModal: React.FC<AdminProductModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  productToEdit,
  categories,
}) => {
  const isEditing = !!productToEdit;

  const [name, setName] = useState('');
  const [sku, setSku] = useState('');
  const [categoryId, setCategoryId] = useState<number | ''>('');
  const [price, setPrice] = useState('');
  const [imageUrl, setImageUrl] = useState('');
  const [description, setDescription] = useState('');
  const [active, setActive] = useState(true);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (productToEdit) {
      setName(productToEdit.name);
      setSku(productToEdit.sku);
      setCategoryId(productToEdit.category ? productToEdit.category.id : '');
      setPrice(productToEdit.price.toString());
      setImageUrl(productToEdit.imageUrl || '');
      setDescription(productToEdit.description || '');
      setActive(productToEdit.active);
    } else {
      setName('');
      setSku('');
      setCategoryId(categories.length > 0 ? categories[0].id : '');
      setPrice('');
      setImageUrl('');
      setDescription('');
      setActive(true);
    }
    setError(null);
  }, [productToEdit, categories, isOpen]);

  if (!isOpen) return null;

  const selectedCategory = categories.find((c) => c.id === Number(categoryId));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!name.trim()) {
      setError('Product name is required.');
      return;
    }
    if (!sku.trim()) {
      setError('SKU code is required.');
      return;
    }
    if (!categoryId) {
      setError('Please select a valid category.');
      return;
    }
    const parsedPrice = parseFloat(price);
    if (isNaN(parsedPrice) || parsedPrice < 0) {
      setError('Please provide a valid non-negative price.');
      return;
    }

    setIsSubmitting(true);
    try {
      if (isEditing && productToEdit) {
        const payload: ProductUpdatePayload = {
          name: name.trim(),
          sku: sku.trim().toUpperCase(),
          categoryId: Number(categoryId),
          price: parsedPrice,
          imageUrl: imageUrl.trim() || undefined,
          description: description.trim() || undefined,
          active,
        };
        const updated = await productService.updateProduct(productToEdit.id, payload);
        onSuccess(updated);
        onClose();
      } else {
        const payload: ProductCreatePayload = {
          name: name.trim(),
          sku: sku.trim().toUpperCase(),
          categoryId: Number(categoryId),
          price: parsedPrice,
          imageUrl: imageUrl.trim() || undefined,
          description: description.trim() || undefined,
          active,
        };
        const created = await productService.createProduct(payload);
        onSuccess(created);
        onClose();
      }
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        let msg = err.message;
        const details = err.details as { validationErrors?: Record<string, string> } | null;
        if (details?.validationErrors) {
          msg = `${msg}: ${Object.values(details.validationErrors).join(', ')}`;
        }
        setError(msg);
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to save product. Please check input parameters.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="modal-dialog"
        style={{ maxWidth: '720px', width: '100%', maxHeight: '90vh', display: 'flex', flexDirection: 'column' }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Modal Header */}
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
              <ShieldCheck size={20} />
            </div>
            <div>
              <h3 style={{ fontSize: '1.2rem', fontWeight: 600 }}>
                {isEditing ? `Edit Product #${productToEdit?.id}` : 'Create New Product'}
              </h3>
              <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                Admin Operations • POST / PUT /api/v1/admin/products
              </p>
            </div>
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

        {/* Modal Form */}
        <form onSubmit={handleSubmit} noValidate style={{ display: 'flex', flexDirection: 'column', overflowY: 'auto' }}>
          <div className="modal-body" style={{ overflowY: 'auto' }}>
            {error && (
              <div className="alert-box error" style={{ marginBottom: '1.25rem' }}>
                <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
                <div>{error}</div>
              </div>
            )}

            <div
              style={{
                marginBottom: '1.25rem',
                padding: '0.7rem 0.9rem',
                background: 'rgba(56, 189, 248, 0.05)',
                border: '1px solid rgba(56, 189, 248, 0.2)',
                borderRadius: 'var(--radius-sm)',
                fontSize: '0.78rem',
                color: 'var(--text-secondary)',
                lineHeight: 1.5,
              }}
            >
              🔒 <strong>Decoupled Architecture:</strong> Catalog defines SKU and metadata. Real-time stock counts, locks, and ledger movements are managed in the inventory module.
            </div>

            {/* 2-Column Responsive Form Grid */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
                gap: '1rem',
                marginBottom: '1rem',
              }}
            >
              {/* Product Name */}
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label" htmlFor="prod-name">
                  Product Name *
                </label>
                <input
                  id="prod-name"
                  type="text"
                  className="form-input"
                  style={{ paddingLeft: '1rem' }}
                  placeholder="e.g. AMD Ryzen 9 7950X3D"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                />
              </div>

              {/* SKU Code (Auto uppercase) */}
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label" htmlFor="prod-sku">
                  SKU Code *
                </label>
                <div className="input-wrapper">
                  <Tag size={15} className="input-icon" />
                  <input
                    id="prod-sku"
                    type="text"
                    className="form-input"
                    style={{ textTransform: 'uppercase', fontFamily: 'var(--font-mono)' }}
                    placeholder="e.g. CPU-RYZEN-7950X3D"
                    value={sku}
                    onChange={(e) => setSku(e.target.value.toUpperCase())}
                    required
                  />
                </div>
              </div>

              {/* Category Select */}
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label" htmlFor="prod-category">
                  Category *
                </label>
                <div className="input-wrapper">
                  <Layers size={15} className="input-icon" />
                  <select
                    id="prod-category"
                    className="form-input catalog-select"
                    value={categoryId}
                    onChange={(e) => setCategoryId(Number(e.target.value))}
                    required
                  >
                    <option value="" disabled>
                      Select a category
                    </option>
                    {categories.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {/* Unit Price */}
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label" htmlFor="prod-price">
                  Unit Price ($ USD) *
                </label>
                <div className="input-wrapper">
                  <DollarSign size={15} className="input-icon" />
                  <input
                    id="prod-price"
                    type="number"
                    step="0.01"
                    min="0.00"
                    className="form-input"
                    placeholder="0.00"
                    value={price}
                    onChange={(e) => setPrice(e.target.value)}
                    required
                  />
                </div>
              </div>
            </div>

            {/* Image URL & Live Preview Box */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
                gap: '1.25rem',
                alignItems: 'start',
                marginBottom: '1rem',
                background: 'rgba(15, 23, 42, 0.45)',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-sm)',
                padding: '1rem',
              }}
            >
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label" htmlFor="prod-image">
                  Image URL (Optional)
                </label>
                <div className="input-wrapper">
                  <ImageIcon size={15} className="input-icon" />
                  <input
                    id="prod-image"
                    type="url"
                    className="form-input"
                    placeholder="https://images.unsplash.com/..."
                    value={imageUrl}
                    onChange={(e) => setImageUrl(e.target.value)}
                  />
                </div>
                <span
                  style={{
                    fontSize: '0.72rem',
                    color: 'var(--text-muted)',
                    marginTop: '0.4rem',
                    display: 'block',
                    lineHeight: 1.4,
                  }}
                >
                  Supports HTTPS image URLs. If left empty or unreachable, the system automatically renders a hardware silicon circuit die fallback.
                </span>
              </div>

              <div>
                <label className="form-label" style={{ marginBottom: '0.4rem', display: 'block' }}>
                  Live Thumbnail Preview
                </label>
                <div
                  style={{
                    borderRadius: 'var(--radius-sm)',
                    overflow: 'hidden',
                    border: '1px solid var(--border-subtle)',
                    background: 'rgba(15, 23, 42, 0.8)',
                    maxHeight: '130px',
                  }}
                >
                  <ProductThumbnail
                    src={imageUrl}
                    alt={name || 'Product Preview'}
                    categoryName={selectedCategory?.name}
                    sku={sku || 'SKU-PREVIEW'}
                    aspectRatio="video"
                    height={110}
                  />
                </div>
              </div>
            </div>

            {/* Description */}
            <div className="form-group">
              <label className="form-label" htmlFor="prod-desc">
                Description
              </label>
              <textarea
                id="prod-desc"
                className="form-input"
                style={{ minHeight: '85px', paddingLeft: '1rem', resize: 'vertical', lineHeight: 1.5 }}
                placeholder="Technical specifications, architecture details, cache sizes, TDP, and hardware features..."
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </div>

            {/* Active Status Checkbox */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginTop: '0.5rem' }}>
              <input
                id="prod-active"
                type="checkbox"
                checked={active}
                onChange={(e) => setActive(e.target.checked)}
                style={{ width: '16px', height: '16px', accentColor: 'var(--accent-cyan)', cursor: 'pointer' }}
              />
              <label
                htmlFor="prod-active"
                style={{ fontSize: '0.85rem', color: 'var(--text-primary)', cursor: 'pointer' }}
              >
                Active in public catalog (visible to customers for ordering)
              </label>
            </div>
          </div>

          {/* Modal Footer */}
          <div className="modal-footer">
            <button
              type="button"
              onClick={onClose}
              disabled={isSubmitting}
              className="btn-secondary"
            >
              Cancel
            </button>

            <button
              type="submit"
              disabled={isSubmitting}
              className="btn-primary"
              style={{ width: 'auto', marginTop: 0, padding: '0.55rem 1.35rem' }}
            >
              {isSubmitting ? (
                <>
                  <span className="pulse-dot" style={{ background: '#fff' }} />
                  <span>Saving...</span>
                </>
              ) : (
                <>
                  <CheckCircle2 size={16} />
                  <span>{isEditing ? 'Save Changes' : 'Create Product'}</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
