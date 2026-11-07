import React, { useState, useEffect } from 'react';
import { Inventory, AdjustmentType } from '../types/inventory';
import { inventoryService } from '../services/inventoryService';
import { ApiError } from '../services/api';
import {
  X,
  PlusCircle,
  MinusCircle,
  AlertCircle,
  CheckCircle2,
  Package,
  Layers,
  FileText,
  Bookmark
} from 'lucide-react';

interface StockAdjustmentModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  inventory: Inventory | null;
}

export const StockAdjustmentModal: React.FC<StockAdjustmentModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  inventory,
}) => {
  const [adjustmentType, setAdjustmentType] = useState<AdjustmentType>('ADD');
  const [quantity, setQuantity] = useState<number | ''>(10);
  const [reason, setReason] = useState('');
  const [referenceId, setReferenceId] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      setAdjustmentType('ADD');
      setQuantity(10);
      setReason('');
      setReferenceId('');
      setError(null);
    }
  }, [isOpen, inventory]);

  if (!isOpen || !inventory) return null;

  const currentAvailable = inventory.availableStock;
  const numQuantity = typeof quantity === 'number' ? quantity : 0;
  const projectedStock =
    adjustmentType === 'ADD'
      ? currentAvailable + numQuantity
      : currentAvailable - numQuantity;

  const isOverdraft = adjustmentType === 'REMOVE' && numQuantity > currentAvailable;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (numQuantity <= 0) {
      setError('Adjustment quantity must be a positive integer greater than zero.');
      return;
    }
    if (!reason.trim()) {
      setError('A business reason is mandatory for the immutable audit ledger.');
      return;
    }
    if (isOverdraft) {
      setError(`Cannot remove ${numQuantity} units: only ${currentAvailable} units available in stock.`);
      return;
    }

    setIsSubmitting(true);
    try {
      await inventoryService.adjustStock(inventory.productId, {
        quantity: numQuantity,
        type: adjustmentType,
        reason: reason.trim(),
        referenceId: referenceId.trim() || undefined,
      });

      onSuccess();
      window.dispatchEvent(new CustomEvent('stockguard:inventory-updated'));
      onClose();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to adjust stock. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const setSuggestedReason = (text: string) => {
    setReason(text);
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="modal-dialog"
        onClick={(e) => e.stopPropagation()}
        style={{ maxWidth: '600px', width: '100%' }}
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
              }}
            >
              <Package size={20} />
            </div>
            <div>
              <h2 style={{ fontSize: '1.25rem', fontWeight: 600 }}>Adjust Product Stock</h2>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                {inventory.productName} ({inventory.productSku})
              </p>
            </div>
          </div>
          <button onClick={onClose} className="modal-close-btn" aria-label="Close modal">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}>
          <div className="modal-body">
            {/* Current vs Projected Stock Banner */}
            <div
              style={{
                marginBottom: '1.25rem',
                padding: '0.85rem 1.15rem',
                borderRadius: 'var(--radius-sm)',
                background: 'rgba(255, 255, 255, 0.03)',
                border: '1px solid var(--border-subtle)',
                display: 'grid',
                gridTemplateColumns: 'repeat(3, 1fr)',
                gap: '0.75rem',
                textAlign: 'center',
              }}
            >
              <div>
                <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>
                  Current Available
                </div>
                <div style={{ fontSize: '1.2rem', fontWeight: 700, color: '#f8fafc', marginTop: '0.2rem' }}>
                  {currentAvailable}
                </div>
              </div>
              <div>
                <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>
                  Operation Delta
                </div>
                <div
                  style={{
                    fontSize: '1.2rem',
                    fontWeight: 700,
                    marginTop: '0.2rem',
                    color: adjustmentType === 'ADD' ? 'var(--accent-emerald)' : 'var(--accent-rose)',
                  }}
                >
                  {adjustmentType === 'ADD' ? `+${numQuantity}` : `-${numQuantity}`}
                </div>
              </div>
              <div>
                <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>
                  Projected Stock
                </div>
                <div
                  style={{
                    fontSize: '1.2rem',
                    fontWeight: 700,
                    marginTop: '0.2rem',
                    color: isOverdraft ? 'var(--accent-rose)' : projectedStock <= inventory.lowStockThreshold ? 'var(--accent-amber)' : 'var(--accent-cyan)',
                  }}
                >
                  {isOverdraft ? 'DEFICIT' : projectedStock}
                </div>
              </div>
            </div>
          {error && (
            <div className="alert-box error" style={{ marginBottom: '1.25rem' }}>
              <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
              <div>{error}</div>
            </div>
          )}

          {isOverdraft && (
            <div
              style={{
                marginBottom: '1.25rem',
                padding: '0.65rem 0.85rem',
                borderRadius: 'var(--radius-sm)',
                background: 'rgba(244, 63, 94, 0.1)',
                border: '1px solid rgba(244, 63, 94, 0.3)',
                color: '#fda4af',
                fontSize: '0.8rem',
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem',
              }}
            >
              <AlertCircle size={16} style={{ flexShrink: 0 }} />
              <span>
                Attempting to deduct more stock than available ({currentAvailable} units).
                Transaction will be rejected by PostgreSQL check constraints.
              </span>
            </div>
          )}

          {/* Direction / Type Selector */}
          <div className="form-group">
            <label className="form-label">
              <span>Adjustment Direction</span>
            </label>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
              <button
                type="button"
                onClick={() => setAdjustmentType('ADD')}
                style={{
                  padding: '0.75rem',
                  borderRadius: 'var(--radius-sm)',
                  border: adjustmentType === 'ADD'
                    ? '2px solid var(--accent-emerald)'
                    : '1px solid var(--border-subtle)',
                  background: adjustmentType === 'ADD'
                    ? 'rgba(16, 185, 129, 0.12)'
                    : 'rgba(255, 255, 255, 0.02)',
                  color: adjustmentType === 'ADD' ? 'var(--accent-emerald)' : 'var(--text-secondary)',
                  fontWeight: 600,
                  fontSize: '0.85rem',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '0.5rem',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                }}
              >
                <PlusCircle size={17} />
                <span>ADD (Restock / Inbound)</span>
              </button>

              <button
                type="button"
                onClick={() => setAdjustmentType('REMOVE')}
                style={{
                  padding: '0.75rem',
                  borderRadius: 'var(--radius-sm)',
                  border: adjustmentType === 'REMOVE'
                    ? '2px solid var(--accent-rose)'
                    : '1px solid var(--border-subtle)',
                  background: adjustmentType === 'REMOVE'
                    ? 'rgba(244, 63, 94, 0.12)'
                    : 'rgba(255, 255, 255, 0.02)',
                  color: adjustmentType === 'REMOVE' ? 'var(--accent-rose)' : 'var(--text-secondary)',
                  fontWeight: 600,
                  fontSize: '0.85rem',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '0.5rem',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                }}
              >
                <MinusCircle size={17} />
                <span>REMOVE (Damage / Correction)</span>
              </button>
            </div>
          </div>

          {/* Quantity & Reference Grid */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div className="form-group">
              <label className="form-label" htmlFor="stock-qty">
                <span>Quantity</span>
                <span style={{ color: 'var(--accent-rose)' }}>*</span>
              </label>
              <div className="input-wrapper">
                <Layers size={16} className="input-icon" />
                <input
                  id="stock-qty"
                  type="number"
                  min="1"
                  step="1"
                  className="form-input"
                  placeholder="e.g. 50"
                  value={quantity}
                  onChange={(e) => setQuantity(e.target.value === '' ? '' : parseInt(e.target.value, 10))}
                  required
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="stock-ref">
                <span>Reference ID</span>
                <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>(Optional)</span>
              </label>
              <div className="input-wrapper">
                <Bookmark size={16} className="input-icon" />
                <input
                  id="stock-ref"
                  type="text"
                  maxLength={100}
                  className="form-input"
                  placeholder="e.g. PO-8921 or RMA-104"
                  value={referenceId}
                  onChange={(e) => setReferenceId(e.target.value)}
                />
              </div>
            </div>
          </div>

          {/* Business Reason */}
          <div className="form-group">
            <label className="form-label" htmlFor="stock-reason">
              <span>Business Reason (Audit Ledger)</span>
              <span style={{ color: 'var(--accent-rose)' }}>*</span>
            </label>
            <div className="input-wrapper">
              <FileText size={16} className="input-icon" />
              <input
                id="stock-reason"
                type="text"
                maxLength={255}
                className="form-input"
                placeholder="State the audit justification for this stock adjustment"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                required
              />
            </div>

            {/* Quick Reason Chips */}
            <div style={{ display: 'flex', gap: '0.4rem', marginTop: '0.5rem', flexWrap: 'wrap' }}>
              <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)', alignSelf: 'center' }}>
                Suggestions:
              </span>
              {[
                'Supplier restock intake',
                'Warehouse cycle count audit',
                'Damaged unit write-off',
                'Customer return restocking',
              ].map((s) => (
                <button
                  key={s}
                  type="button"
                  onClick={() => setSuggestedReason(s)}
                  style={{
                    fontSize: '0.72rem',
                    padding: '0.2rem 0.5rem',
                    borderRadius: 'var(--radius-sm)',
                    background: 'rgba(255, 255, 255, 0.05)',
                    border: '1px solid var(--border-subtle)',
                    color: 'var(--text-secondary)',
                    cursor: 'pointer',
                  }}
                >
                  {s}
                </button>
              ))}
            </div>
          </div>

          </div>

          {/* Form Actions */}
          <div className="modal-footer">
            <button
              type="button"
              onClick={onClose}
              className="btn-secondary"
              disabled={isSubmitting}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="btn-submit"
              disabled={isSubmitting || isOverdraft}
              style={{
                width: 'auto',
                margin: 0,
                padding: '0.65rem 1.5rem',
                background: adjustmentType === 'ADD'
                  ? 'linear-gradient(135deg, #10b981, #059669)'
                  : 'linear-gradient(135deg, #f43f5e, #e11d48)',
              }}
            >
              {isSubmitting ? (
                <>
                  <span className="pulse-dot" style={{ background: '#ffffff' }} />
                  <span>Committing Under Lock...</span>
                </>
              ) : (
                <>
                  <CheckCircle2 size={16} />
                  <span>
                    Commit {adjustmentType} ({numQuantity} Units)
                  </span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
