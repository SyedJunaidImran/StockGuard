import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import { orderService } from '../services/orderService';
import { OrderResponse, OrderStatus } from '../types/order';
import { PageResponse } from '../types/product';
import { ApiError } from '../services/api';
import {
  X,
  Package,
  Calendar,
  ChevronLeft,
  ChevronRight,
  ArrowLeft,
  Eye,
  RotateCcw,
  AlertCircle,
  CheckCircle2,
  Clock,
  Truck,
  XCircle,
  FileText
} from 'lucide-react';

interface CustomerOrdersModalProps {
  isOpen: boolean;
  onClose: () => void;
  selectedOrderId?: number | null;
}

export const CustomerOrdersModal: React.FC<CustomerOrdersModalProps> = ({
  isOpen,
  onClose,
  selectedOrderId: externalOrderId,
}) => {
  const { user, isAuthenticated } = useAuth();

  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [pageData, setPageData] = useState<PageResponse<OrderResponse> | null>(null);
  const [page, setPage] = useState(0);
  const [pageSize] = useState(8);

  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [selectedOrder, setSelectedOrder] = useState<OrderResponse | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  // Fetch paginated customer orders
  const fetchOrders = useCallback(async () => {
    if (!isAuthenticated) return;

    setIsLoading(true);
    setError(null);

    try {
      const data = await orderService.getMyOrders(page, pageSize);
      setOrders(data.content);
      setPageData(data);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to load order history. Verify service is reachable.');
      }
    } finally {
      setIsLoading(false);
    }
  }, [isAuthenticated, page, pageSize]);

  // Load single order details by ID
  const fetchOrderDetail = useCallback(async (orderId: number) => {
    setDetailLoading(true);
    setDetailError(null);
    try {
      const order = await orderService.getOrderById(orderId);
      setSelectedOrder(order);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setDetailError(err.message);
      } else if (err instanceof Error) {
        setDetailError(err.message);
      } else {
        setDetailError('Unable to load order details.');
      }
    } finally {
      setDetailLoading(false);
    }
  }, []);

  useEffect(() => {
    if (isOpen) {
      if (externalOrderId) {
        fetchOrderDetail(externalOrderId);
      }
      fetchOrders();
    } else {
      setSelectedOrder(null);
      setDetailError(null);
    }
  }, [isOpen, externalOrderId, fetchOrders, fetchOrderDetail]);

  if (!isOpen) return null;

  const renderStatusBadge = (status: OrderStatus) => {
    switch (status) {
      case 'CONFIRMED':
        return (
          <span className="stock-badge in-stock">
            <CheckCircle2 size={12} />
            <span>CONFIRMED</span>
          </span>
        );
      case 'PROCESSING':
        return (
          <span className="stock-badge untracked">
            <Clock size={12} />
            <span>PROCESSING</span>
          </span>
        );
      case 'SHIPPED':
      case 'DELIVERED':
        return (
          <span className="stock-badge in-stock">
            <Truck size={12} />
            <span>{status}</span>
          </span>
        );
      case 'CANCELLED':
      case 'FAILED':
        return (
          <span className="stock-badge out-of-stock">
            <XCircle size={12} />
            <span>{status}</span>
          </span>
        );
      default:
        return (
          <span className="stock-badge low-stock">
            <Clock size={12} />
            <span>{status}</span>
          </span>
        );
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="modal-dialog"
        style={{ maxWidth: '840px', width: '100%', maxHeight: '90vh', display: 'flex', flexDirection: 'column' }}
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
                background: 'rgba(16, 185, 129, 0.15)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'var(--accent-emerald)',
                flexShrink: 0,
              }}
            >
              <Package size={20} />
            </div>
            <div>
              <h3 style={{ margin: 0, fontSize: '1.2rem', fontWeight: 600 }}>
                {selectedOrder ? 'Order Details' : 'My Orders'}
              </h3>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                {selectedOrder
                  ? `Order ${selectedOrder.orderNumber}`
                  : `Authenticated as ${user?.email || 'Customer'}`}
              </span>
            </div>
          </div>

          <button
            onClick={onClose}
            className="btn-secondary"
            style={{ padding: '0.35rem 0.5rem', border: 'none', background: 'transparent' }}
            aria-label="Close orders modal"
          >
            <X size={18} />
          </button>
        </div>

        {/* Body */}
        <div className="modal-body" style={{ overflowY: 'auto' }}>
          {selectedOrder ? (
            /* ==============================================================
               VIEW: SINGLE ORDER DETAILS & HISTORICAL SNAPSHOT
               ============================================================== */
            <div>
              {/* Breadcrumb Back Button */}
              <div style={{ marginBottom: '1.25rem' }}>
                <button
                  type="button"
                  onClick={() => {
                    setSelectedOrder(null);
                    setDetailError(null);
                  }}
                  className="btn-secondary"
                  style={{
                    padding: '0.35rem 0.75rem',
                    fontSize: '0.8rem',
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '0.4rem',
                  }}
                >
                  <ArrowLeft size={14} />
                  <span>Back to Orders List</span>
                </button>
              </div>

              {detailError && (
                <div className="alert-box error" style={{ marginBottom: '1rem' }}>
                  <AlertCircle size={18} style={{ flexShrink: 0 }} />
                  <div>{detailError}</div>
                </div>
              )}

              {detailLoading ? (
                <div style={{ textAlign: 'center', padding: '3.5rem 1rem', color: 'var(--text-muted)' }}>
                  <span className="pulse-dot" style={{ background: 'var(--accent-cyan)', margin: '0 auto 0.75rem' }} />
                  <div>Loading order details...</div>
                </div>
              ) : (
                <div>
                  {/* Top Summary Card */}
                  <div
                    style={{
                      background: 'rgba(15, 23, 42, 0.6)',
                      border: '1px solid var(--border-subtle)',
                      borderRadius: 'var(--radius-sm)',
                      padding: '1.25rem',
                      marginBottom: '1.25rem',
                    }}
                  >
                    <div
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'flex-start',
                        flexWrap: 'wrap',
                        gap: '1rem',
                        marginBottom: '1rem',
                      }}
                    >
                      <div>
                        <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '0.25rem' }}>
                          Order Reference Number
                        </div>
                        <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#f8fafc', letterSpacing: '0.02em', fontFamily: 'var(--font-mono)' }}>
                          {selectedOrder.orderNumber}
                        </div>
                      </div>

                      <div style={{ textAlign: 'right' }}>
                        <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '0.25rem' }}>
                          Status
                        </div>
                        {renderStatusBadge(selectedOrder.status)}
                      </div>
                    </div>

                    <div
                      style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
                        gap: '0.75rem',
                        fontSize: '0.8rem',
                        color: 'var(--text-secondary)',
                        borderTop: '1px solid rgba(255, 255, 255, 0.05)',
                        paddingTop: '0.85rem',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                        <Calendar size={14} style={{ color: 'var(--accent-indigo)' }} />
                        <span>Placed: {new Date(selectedOrder.createdAt).toLocaleString()}</span>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                        <FileText size={14} style={{ color: 'var(--accent-cyan)' }} />
                        <span>Currency: {selectedOrder.currency}</span>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                        <CheckCircle2 size={14} style={{ color: 'var(--accent-emerald)' }} />
                        <span>Inventory Allocated</span>
                      </div>
                    </div>

                    {selectedOrder.notes && (
                      <div
                        style={{
                          marginTop: '0.85rem',
                          padding: '0.65rem 0.85rem',
                          background: 'rgba(255, 255, 255, 0.03)',
                          border: '1px solid var(--border-subtle)',
                          borderRadius: 'var(--radius-sm)',
                          fontSize: '0.8rem',
                          color: 'var(--text-secondary)',
                        }}
                      >
                        <strong style={{ color: '#f8fafc' }}>Customer Delivery Notes:</strong> {selectedOrder.notes}
                      </div>
                    )}
                  </div>

                  {/* Line Items Container */}
                  <div
                    style={{
                      background: 'rgba(15, 23, 42, 0.6)',
                      border: '1px solid var(--border-subtle)',
                      borderRadius: 'var(--radius-sm)',
                      padding: '1.25rem',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.5rem' }}>
                      <h4 style={{ margin: 0, fontSize: '1rem', fontWeight: 600 }}>
                        Order Items ({selectedOrder.items?.length || 0})
                      </h4>
                    </div>

                    {/* Desktop/Tablet Table Layout */}
                    <div className="order-items-table-container" style={{ overflowX: 'auto' }}>
                      <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
                        <thead>
                          <tr
                            style={{
                              borderBottom: '1px solid var(--border-subtle)',
                              color: 'var(--text-muted)',
                              fontSize: '0.725rem',
                              textTransform: 'uppercase',
                              letterSpacing: '0.05em',
                            }}
                          >
                            <th style={{ padding: '0.65rem 0.75rem' }}>Product Name</th>
                            <th style={{ padding: '0.65rem 0.75rem' }}>SKU</th>
                            <th style={{ padding: '0.65rem 0.75rem', textAlign: 'right' }}>Unit Price</th>
                            <th style={{ padding: '0.65rem 0.75rem', textAlign: 'center' }}>Qty</th>
                            <th style={{ padding: '0.65rem 0.75rem', textAlign: 'right' }}>Subtotal</th>
                          </tr>
                        </thead>
                        <tbody>
                          {selectedOrder.items?.map((item) => (
                            <tr
                              key={item.id}
                              style={{
                                borderBottom: '1px solid rgba(255, 255, 255, 0.04)',
                              }}
                            >
                              <td style={{ padding: '0.8rem 0.75rem' }}>
                                <div style={{ fontWeight: 600, color: '#f8fafc' }}>
                                  {item.productName || `Product #${item.productId}`}
                                </div>
                              </td>
                              <td style={{ padding: '0.8rem 0.75rem' }}>
                                <span className="product-sku-pill" style={{ fontSize: '0.7rem' }}>
                                  {item.productSku}
                                </span>
                              </td>
                              <td style={{ padding: '0.8rem 0.75rem', textAlign: 'right', color: 'var(--text-secondary)' }}>
                                ${Number(item.unitPrice).toFixed(2)}
                              </td>
                              <td style={{ padding: '0.8rem 0.75rem', textAlign: 'center', fontWeight: 600 }}>
                                {item.quantity}
                              </td>
                              <td style={{ padding: '0.8rem 0.75rem', textAlign: 'right', fontWeight: 600, color: '#f8fafc' }}>
                                ${Number(item.subtotal).toFixed(2)}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                        <tfoot>
                          <tr style={{ borderTop: '2px solid var(--border-subtle)' }}>
                            <td colSpan={4} style={{ padding: '1rem 0.75rem', textAlign: 'right', fontWeight: 600, color: 'var(--text-secondary)' }}>
                              Order Grand Total:
                            </td>
                            <td style={{ padding: '1rem 0.75rem', textAlign: 'right', fontSize: '1.25rem', fontWeight: 700, color: 'var(--accent-cyan)' }}>
                              ${Number(selectedOrder.totalAmount).toFixed(2)}{' '}
                              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{selectedOrder.currency}</span>
                            </td>
                          </tr>
                        </tfoot>
                      </table>
                    </div>
                  </div>
                </div>
              )}
            </div>
          ) : (
            /* ==============================================================
               VIEW: CUSTOMER ORDERS LIST (Clickable rows & responsive cards)
               ============================================================== */
            <div>
              {error && (
                <div className="alert-box error" style={{ marginBottom: '1.25rem' }}>
                  <AlertCircle size={18} style={{ flexShrink: 0 }} />
                  <div>{error}</div>
                  <button onClick={fetchOrders} className="btn-secondary" style={{ marginLeft: 'auto', padding: '0.25rem 0.5rem', fontSize: '0.75rem' }}>
                    <RotateCcw size={12} />
                    <span>Retry</span>
                  </button>
                </div>
              )}

              {isLoading ? (
                <div style={{ textAlign: 'center', padding: '3.5rem 1rem', color: 'var(--text-muted)' }}>
                  <span className="pulse-dot" style={{ background: 'var(--accent-cyan)', margin: '0 auto 0.75rem' }} />
                  <div>Retrieving customer order records from PostgreSQL...</div>
                </div>
              ) : orders.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '3.5rem 1rem' }}>
                  <div
                    style={{
                      width: '64px',
                      height: '64px',
                      borderRadius: 'var(--radius-full)',
                      background: 'rgba(255, 255, 255, 0.05)',
                      border: '1px solid var(--border-subtle)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      margin: '0 auto 1.25rem',
                      color: 'var(--text-muted)',
                    }}
                  >
                    <Package size={30} />
                  </div>
                  <h4 style={{ fontSize: '1.15rem', fontWeight: 600, marginBottom: '0.4rem', color: '#f8fafc' }}>
                    No Orders Placed Yet
                  </h4>
                  <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', maxWidth: '400px', margin: '0 auto 1.75rem', lineHeight: 1.5 }}>
                    When you place an order, your purchase price snapshots and real-time order lifecycle status will be tracked here.
                  </p>
                  <button
                    onClick={() => {
                      onClose();
                      document.getElementById('catalog-section')?.scrollIntoView({ behavior: 'smooth' });
                    }}
                    className="btn-primary"
                  >
                    Browse Product Catalog
                  </button>
                </div>
              ) : (
                <div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                    {orders.map((ord) => (
                      <div
                        key={ord.id}
                        onClick={() => setSelectedOrder(ord)}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          padding: '1rem 1.25rem',
                          background: 'rgba(15, 23, 42, 0.55)',
                          border: '1px solid var(--border-subtle)',
                          borderRadius: 'var(--radius-sm)',
                          flexWrap: 'wrap',
                          gap: '1rem',
                          cursor: 'pointer',
                          transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)',
                        }}
                        className="customer-order-row"
                        role="button"
                        tabIndex={0}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault();
                            setSelectedOrder(ord);
                          }
                        }}
                      >
                        <div style={{ flex: '1 1 240px' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '0.35rem', flexWrap: 'wrap' }}>
                            <span style={{ fontWeight: 700, fontSize: '0.98rem', color: '#f8fafc', fontFamily: 'var(--font-mono)' }}>
                              {ord.orderNumber}
                            </span>
                            {renderStatusBadge(ord.status)}
                          </div>
                          <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                            Placed on {new Date(ord.createdAt).toLocaleDateString()} at{' '}
                            {new Date(ord.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} •{' '}
                            {ord.items?.length || 0} {ord.items?.length === 1 ? 'item' : 'items'}
                          </div>
                        </div>

                        <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem', marginLeft: 'auto' }}>
                          <div style={{ textAlign: 'right' }}>
                            <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Order Total</div>
                            <div style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--accent-cyan)' }}>
                              ${Number(ord.totalAmount).toFixed(2)}
                            </div>
                          </div>

                          <div
                            className="btn-secondary"
                            style={{
                              padding: '0.4rem 0.75rem',
                              fontSize: '0.78rem',
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '0.35rem',
                            }}
                          >
                            <Eye size={13} />
                            <span>Details</span>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>

                  {/* Pagination Controls */}
                  {pageData && pageData.totalPages > 1 && (
                    <div className="pagination-wrapper" style={{ marginTop: '1.5rem' }}>
                      <div className="pagination-info" style={{ fontSize: '0.78rem' }}>
                        Showing page {pageData.pageNumber + 1} of {pageData.totalPages} ({pageData.totalElements} orders)
                      </div>
                      <div className="pagination-controls">
                        <button
                          onClick={() => setPage((p) => Math.max(0, p - 1))}
                          disabled={pageData.first || isLoading}
                          className="pagination-btn"
                          aria-label="Previous orders page"
                        >
                          <ChevronLeft size={16} />
                        </button>
                        <span style={{ fontSize: '0.8rem', padding: '0 0.5rem', color: 'var(--text-secondary)' }}>
                          {pageData.pageNumber + 1}
                        </span>
                        <button
                          onClick={() => setPage((p) => (pageData.last ? p : p + 1))}
                          disabled={pageData.last || isLoading}
                          className="pagination-btn"
                          aria-label="Next orders page"
                        >
                          <ChevronRight size={16} />
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="modal-footer" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
            PostgreSQL-backed Order Ledger • Customer Ownership Enforced
          </div>
          <button onClick={onClose} className="btn-secondary" style={{ fontSize: '0.8rem' }}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
