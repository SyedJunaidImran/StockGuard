import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import { Inventory, InventoryMovement, MovementType } from '../types/inventory';
import { PageResponse } from '../types/product';
import { inventoryService } from '../services/inventoryService';
import { StockAdjustmentModal } from './StockAdjustmentModal';
import { ApiError } from '../services/api';
import {
  Boxes,
  History,
  AlertTriangle,
  CheckCircle2,
  RefreshCw,
  Search,
  ChevronLeft,
  ChevronRight,
  ShieldAlert,
  ArrowUpRight,
  ArrowDownRight,
  Lock,
  Layers,
  Activity,
  FileSpreadsheet,
  PackageCheck,
  XCircle,
  Filter
} from 'lucide-react';

const MOVEMENT_TYPES: { value: string; label: string }[] = [
  { value: 'ALL', label: 'All Movement Types' },
  { value: 'RESTOCK', label: 'RESTOCK (Supplier Intake)' },
  { value: 'MANUAL_ADJUSTMENT', label: 'MANUAL_ADJUSTMENT' },
  { value: 'CORRECTION', label: 'CORRECTION (Audit Fix)' },
  { value: 'DAMAGED_WRITE_OFF', label: 'DAMAGED_WRITE_OFF' },
  { value: 'INITIALIZATION', label: 'INITIALIZATION' },
  { value: 'ORDER_PURCHASE', label: 'ORDER_PURCHASE (Customer)' },
];

export const InventorySection: React.FC = () => {
  const { user, isAuthenticated } = useAuth();
  const isAdmin = isAuthenticated && user?.role === 'ADMIN';

  // Active view tab: 'INVENTORY' | 'AUDIT_LEDGER'
  const [activeTab, setActiveTab] = useState<'INVENTORY' | 'AUDIT_LEDGER'>('INVENTORY');

  // Inventory Table State
  const [inventoryList, setInventoryList] = useState<Inventory[]>([]);
  const [inventoryPageData, setInventoryPageData] = useState<PageResponse<Inventory> | null>(null);
  const [inventoryPage, setInventoryPage] = useState(0);
  const [inventoryLoading, setInventoryLoading] = useState(false);
  const [inventoryError, setInventoryError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState('');

  // Movements Audit Ledger State
  const [movements, setMovements] = useState<InventoryMovement[]>([]);
  const [movementsPageData, setMovementsPageData] = useState<PageResponse<InventoryMovement> | null>(null);
  const [movementsPage, setMovementsPage] = useState(0);
  const [movementsLoading, setMovementsLoading] = useState(false);
  const [movementsError, setMovementsError] = useState<string | null>(null);
  const [movementTypeFilter, setMovementTypeFilter] = useState<string>('ALL');

  // Adjustment Modal State
  const [selectedInventory, setSelectedInventory] = useState<Inventory | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [actionNotice, setActionNotice] = useState<string | null>(null);

  // Load Inventory Data
  const fetchInventory = useCallback(async () => {
    if (!isAdmin) return;
    setInventoryLoading(true);
    setInventoryError(null);
    try {
      const data = await inventoryService.getAllInventory(inventoryPage, 10);
      setInventoryList(data.content);
      setInventoryPageData(data);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setInventoryError(err.message);
      } else if (err instanceof Error) {
        setInventoryError(err.message);
      } else {
        setInventoryError('Failed to load inventory data.');
      }
    } finally {
      setInventoryLoading(false);
    }
  }, [isAdmin, inventoryPage]);

  // Load Movements Data
  const fetchMovements = useCallback(async () => {
    if (!isAdmin) return;
    setMovementsLoading(true);
    setMovementsError(null);
    try {
      const data = await inventoryService.getAllMovements(movementsPage, 10);
      setMovements(data.content);
      setMovementsPageData(data);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setMovementsError(err.message);
      } else if (err instanceof Error) {
        setMovementsError(err.message);
      } else {
        setMovementsError('Failed to load movement audit ledger.');
      }
    } finally {
      setMovementsLoading(false);
    }
  }, [isAdmin, movementsPage]);

  // Initial and reactive data fetching
  useEffect(() => {
    if (isAdmin) {
      if (activeTab === 'INVENTORY') {
        fetchInventory();
      } else {
        fetchMovements();
      }
    }
  }, [isAdmin, activeTab, fetchInventory, fetchMovements]);

  const handleOpenAdjustment = (inv: Inventory) => {
    setSelectedInventory(inv);
    setIsModalOpen(true);
  };

  const handleAdjustmentSuccess = () => {
    setActionNotice('Stock adjustment applied and ledger entry written successfully.');
    fetchInventory();
    fetchMovements();
    setTimeout(() => setActionNotice(null), 5000);
  };

  // Filtered inventory list based on search term
  const filteredInventory = inventoryList.filter((inv) => {
    if (!searchTerm.trim()) return true;
    const term = searchTerm.toLowerCase();
    return (
      inv.productName.toLowerCase().includes(term) ||
      inv.productSku.toLowerCase().includes(term)
    );
  });

  // Filtered movements based on movement type filter
  const filteredMovements = movements.filter((m) => {
    if (movementTypeFilter === 'ALL') return true;
    return m.movementType === (movementTypeFilter as MovementType);
  });

  // Calculate high-level metrics
  const totalUnits = inventoryList.reduce((acc, curr) => acc + curr.availableStock, 0);
  const lowStockCount = inventoryList.filter((inv) => inv.lowStock && inv.availableStock > 0).length;
  const outOfStockCount = inventoryList.filter((inv) => inv.availableStock === 0).length;

  return (
    <section id="inventory-section" className="catalog-wrapper" style={{ marginTop: '3.5rem' }}>
      {/* Section Header */}
      <div className="section-header">
        <div className="hero-pill" style={{ marginBottom: '1rem', display: 'inline-flex' }}>
          <Boxes size={16} />
          <span>Inventory &amp; Warehouse</span>
        </div>
        <h2 style={{ fontSize: '1.875rem', fontWeight: 700, letterSpacing: '-0.02em', marginBottom: '0.5rem' }}>
          Inventory Management &amp; <span className="gradient-text">Stock Ledger</span>
        </h2>
        <p style={{ color: 'var(--text-secondary)', maxWidth: '680px', margin: '0 auto', fontSize: '0.95rem' }}>
          Track warehouse stock levels, configure threshold alerts, execute stock adjustments, and inspect the complete audit movement history.
        </p>
      </div>

      {/* Security Guard Notice for non-admin users */}
      {!isAdmin ? (
        <div
          className="glass-card"
          style={{
            maxWidth: '680px',
            margin: '2rem auto',
            padding: '2.5rem 2rem',
            textAlign: 'center',
            border: '1px solid rgba(245, 158, 11, 0.3)',
            background: 'rgba(245, 158, 11, 0.04)',
          }}
        >
          <div
            style={{
              width: '56px',
              height: '56px',
              borderRadius: 'var(--radius-full)',
              background: 'rgba(245, 158, 11, 0.15)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 1.25rem',
              color: 'var(--accent-amber)',
            }}
          >
            <Lock size={26} />
          </div>
          <h3 style={{ fontSize: '1.25rem', fontWeight: 600, marginBottom: '0.5rem', color: '#fef3c7' }}>
            Administrator Access Required
          </h3>
          <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', maxWidth: '500px', margin: '0 auto 1.5rem', lineHeight: 1.5 }}>
            Inventory mutation and movement audit ledger inspection are restricted to authenticated administrator accounts.
          </p>

          <button
            onClick={() => {
              document.getElementById('auth-console-section')?.scrollIntoView({ behavior: 'smooth' });
            }}
            className="btn-primary"
            style={{
              padding: '0.65rem 1.35rem',
              fontSize: '0.85rem',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.5rem',
            }}
          >
            <span>Sign In as Administrator</span>
          </button>
        </div>
      ) : (
        /* ====================================================================
           ADMIN INVENTORY CONSOLE
           ==================================================================== */
        <div>
          {/* Action Success Toast */}
          {actionNotice && (
            <div
              className="alert-box success"
              style={{ maxWidth: '900px', margin: '0 auto 1.5rem', display: 'flex', alignItems: 'center', gap: '0.65rem' }}
            >
              <CheckCircle2 size={18} style={{ flexShrink: 0 }} />
              <div>{actionNotice}</div>
            </div>
          )}

          {/* High-level KPI Cards */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
              gap: '1rem',
              marginBottom: '1.75rem',
            }}
          >
            {/* Tracked Products */}
            <div className="glass-card" style={{ padding: '1.15rem 1.25rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', color: 'var(--text-muted)', fontSize: '0.78rem', marginBottom: '0.4rem' }}>
                <span>Tracked SKUs</span>
                <Layers size={16} style={{ color: 'var(--accent-cyan)' }} />
              </div>
              <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#f8fafc', fontFamily: 'var(--font-mono)' }}>
                {inventoryPageData?.totalElements ?? inventoryList.length}
              </div>
            </div>

            {/* Total Units */}
            <div className="glass-card" style={{ padding: '1.15rem 1.25rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', color: 'var(--text-muted)', fontSize: '0.78rem', marginBottom: '0.4rem' }}>
                <span>Available Units</span>
                <PackageCheck size={16} style={{ color: 'var(--accent-emerald)' }} />
              </div>
              <div style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--accent-emerald)', fontFamily: 'var(--font-mono)' }}>
                {totalUnits}
              </div>
            </div>

            {/* Low Stock Alerts */}
            <div className="glass-card" style={{ padding: '1.15rem 1.25rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', color: 'var(--text-muted)', fontSize: '0.78rem', marginBottom: '0.4rem' }}>
                <span>Low Stock Alerts</span>
                <AlertTriangle size={16} style={{ color: lowStockCount > 0 ? 'var(--accent-amber)' : 'var(--text-muted)' }} />
              </div>
              <div style={{ fontSize: '1.5rem', fontWeight: 700, color: lowStockCount > 0 ? 'var(--accent-amber)' : '#f8fafc', fontFamily: 'var(--font-mono)' }}>
                {lowStockCount}
              </div>
            </div>

            {/* Out of Stock Items */}
            <div className="glass-card" style={{ padding: '1.15rem 1.25rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', color: 'var(--text-muted)', fontSize: '0.78rem', marginBottom: '0.4rem' }}>
                <span>Out of Stock</span>
                <XCircle size={16} style={{ color: outOfStockCount > 0 ? 'var(--accent-rose)' : 'var(--text-muted)' }} />
              </div>
              <div style={{ fontSize: '1.5rem', fontWeight: 700, color: outOfStockCount > 0 ? 'var(--accent-rose)' : '#f8fafc', fontFamily: 'var(--font-mono)' }}>
                {outOfStockCount}
              </div>
            </div>

            {/* Total Audit Movements */}
            <div className="glass-card" style={{ padding: '1.15rem 1.25rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', color: 'var(--text-muted)', fontSize: '0.78rem', marginBottom: '0.4rem' }}>
                <span>Total Audit Movements</span>
                <Activity size={16} style={{ color: 'var(--accent-indigo)' }} />
              </div>
              <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#f8fafc', fontFamily: 'var(--font-mono)' }}>
                {movementsPageData?.totalElements ?? movements.length}
              </div>
            </div>
          </div>

          {/* Navigation Tabs and Refresh Button */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              flexWrap: 'wrap',
              gap: '1rem',
              marginBottom: '1.25rem',
            }}
          >
            <div className="auth-tabs" style={{ maxWidth: '420px', margin: 0 }}>
              <button
                type="button"
                className={`auth-tab ${activeTab === 'INVENTORY' ? 'active' : ''}`}
                onClick={() => setActiveTab('INVENTORY')}
              >
                <Boxes size={16} />
                <span>Stock Levels</span>
              </button>
              <button
                type="button"
                className={`auth-tab ${activeTab === 'AUDIT_LEDGER' ? 'active' : ''}`}
                onClick={() => setActiveTab('AUDIT_LEDGER')}
              >
                <History size={16} />
                <span>Audit Movement Ledger</span>
              </button>
            </div>

            <button
              onClick={() => {
                if (activeTab === 'INVENTORY') fetchInventory();
                else fetchMovements();
              }}
              className="btn-secondary"
              disabled={inventoryLoading || movementsLoading}
              title="Refresh inventory"
              style={{ padding: '0.55rem 1rem', display: 'inline-flex', alignItems: 'center', gap: '0.45rem' }}
            >
              <RefreshCw size={15} className={inventoryLoading || movementsLoading ? 'spin' : ''} />
              <span>Refresh</span>
            </button>
          </div>

          {/* ==================================================================
              TAB 1: INVENTORY LEVELS TABLE
              ================================================================== */}
          {activeTab === 'INVENTORY' && (
            <div className="glass-card" style={{ padding: '1.5rem' }}>
              {/* Table search filter */}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem', flexWrap: 'wrap', gap: '0.75rem' }}>
                <div style={{ position: 'relative', width: '280px' }}>
                  <Search size={15} style={{ position: 'absolute', left: '0.85rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
                  <input
                    type="text"
                    className="form-input"
                    placeholder="Filter by product name or SKU..."
                    style={{ paddingLeft: '2.4rem', fontSize: '0.825rem', height: '36px' }}
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                  />
                </div>

                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                  Showing {filteredInventory.length} of {inventoryPageData?.totalElements ?? inventoryList.length} tracked items
                </div>
              </div>

              {inventoryError && (
                <div className="alert-box error" style={{ marginBottom: '1rem' }}>
                  <ShieldAlert size={18} style={{ flexShrink: 0 }} />
                  <div>{inventoryError}</div>
                </div>
              )}

              {inventoryLoading ? (
                <div style={{ textAlign: 'center', padding: '3.5rem 1rem', color: 'var(--text-muted)' }}>
                  <span className="pulse-dot" style={{ background: 'var(--accent-cyan)', margin: '0 auto 0.75rem' }} />
                  <div>Loading inventory levels...</div>
                </div>
              ) : filteredInventory.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '3.5rem 1rem', color: 'var(--text-muted)' }}>
                  <Boxes size={32} style={{ margin: '0 auto 0.75rem', opacity: 0.4 }} />
                  <p>No inventory records matching your query.</p>
                </div>
              ) : (
                <div style={{ overflowX: 'auto' }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.875rem' }}>
                    <thead>
                      <tr style={{ borderBottom: '1px solid var(--border-subtle)', color: 'var(--text-muted)', fontSize: '0.725rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                        <th style={{ padding: '0.75rem 1rem' }}>Product Details</th>
                        <th style={{ padding: '0.75rem 1rem' }}>SKU</th>
                        <th style={{ padding: '0.75rem 1rem' }}>Available Stock</th>
                        <th style={{ padding: '0.75rem 1rem' }}>Reserved</th>
                        <th style={{ padding: '0.75rem 1rem' }}>Threshold</th>
                        <th style={{ padding: '0.75rem 1rem' }}>Status</th>
                        <th style={{ padding: '0.75rem 1rem', textAlign: 'right' }}>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredInventory.map((inv) => (
                        <tr
                          key={inv.id}
                          style={{
                            borderBottom: '1px solid rgba(255, 255, 255, 0.04)',
                            transition: 'background 0.15s ease',
                          }}
                        >
                          <td style={{ padding: '0.9rem 1rem' }}>
                            <div style={{ fontWeight: 600, color: '#f8fafc' }}>{inv.productName}</div>
                            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Product #{inv.productId}</div>
                          </td>
                          <td style={{ padding: '0.9rem 1rem' }}>
                            <span className="product-sku-pill" style={{ fontSize: '0.75rem' }}>
                              {inv.productSku}
                            </span>
                          </td>
                          <td style={{ padding: '0.9rem 1rem' }}>
                            <span
                              style={{
                                fontSize: '1.05rem',
                                fontWeight: 700,
                                fontFamily: 'var(--font-mono)',
                                color: inv.availableStock === 0 ? 'var(--accent-rose)' : inv.lowStock ? 'var(--accent-amber)' : 'var(--accent-emerald)',
                              }}
                            >
                              {inv.availableStock}
                            </span>
                          </td>
                          <td style={{ padding: '0.9rem 1rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
                            {inv.reservedStock}
                          </td>
                          <td style={{ padding: '0.9rem 1rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
                            {inv.lowStockThreshold}
                          </td>
                          <td style={{ padding: '0.9rem 1rem' }}>
                            {inv.availableStock === 0 ? (
                              <span className="stock-badge out-of-stock">
                                <span className="stock-badge-dot" />
                                Out of Stock
                              </span>
                            ) : inv.lowStock ? (
                              <span className="stock-badge low-stock">
                                <span className="stock-badge-dot" />
                                Low Stock
                              </span>
                            ) : (
                              <span className="stock-badge in-stock">
                                <span className="stock-badge-dot" />
                                Healthy
                              </span>
                            )}
                          </td>
                          <td style={{ padding: '0.9rem 1rem', textAlign: 'right' }}>
                            <button
                              onClick={() => handleOpenAdjustment(inv)}
                              className="btn-secondary"
                              style={{
                                fontSize: '0.78rem',
                                padding: '0.35rem 0.75rem',
                                color: 'var(--accent-cyan)',
                                borderColor: 'rgba(56, 189, 248, 0.3)',
                                background: 'rgba(56, 189, 248, 0.08)',
                              }}
                              title="Execute pessimistic write lock stock adjustment"
                            >
                              <span>⚡ Adjust Stock</span>
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {/* Pagination Controls */}
              {inventoryPageData && inventoryPageData.totalPages > 1 && (
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: '1.5rem', paddingTop: '1rem', borderTop: '1px solid var(--border-subtle)' }}>
                  <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                    Page {inventoryPageData.pageNumber + 1} of {inventoryPageData.totalPages}
                  </div>
                  <div style={{ display: 'flex', gap: '0.5rem' }}>
                    <button
                      onClick={() => setInventoryPage((p) => Math.max(0, p - 1))}
                      disabled={inventoryPageData.first}
                      className="btn-secondary"
                      style={{ padding: '0.35rem 0.65rem' }}
                      aria-label="Previous inventory page"
                    >
                      <ChevronLeft size={16} />
                    </button>
                    <button
                      onClick={() => setInventoryPage((p) => p + 1)}
                      disabled={inventoryPageData.last}
                      className="btn-secondary"
                      style={{ padding: '0.35rem 0.65rem' }}
                      aria-label="Next inventory page"
                    >
                      <ChevronRight size={16} />
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* ==================================================================
              TAB 2: IMMUTABLE AUDIT LEDGER TABLE
              ================================================================== */}
          {activeTab === 'AUDIT_LEDGER' && (
            <div className="glass-card" style={{ padding: '1.5rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.25rem', flexWrap: 'wrap', gap: '0.75rem' }}>
                <div>
                  <h3 style={{ fontSize: '1.1rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <FileSpreadsheet size={18} style={{ color: 'var(--accent-indigo)' }} />
                    <span>Immutable Audit Movement Ledger</span>
                  </h3>
                  <p style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                    Append-only record of all historical stock adjustments with actor attribution.
                  </p>
                </div>

                {/* Movement Type Filter Dropdown */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', color: 'var(--text-muted)', fontSize: '0.78rem' }}>
                    <Filter size={14} />
                    <span>Type:</span>
                  </div>
                  <select
                    className="catalog-select"
                    style={{ padding: '0.4rem 0.75rem', fontSize: '0.8rem' }}
                    value={movementTypeFilter}
                    onChange={(e) => setMovementTypeFilter(e.target.value)}
                  >
                    {MOVEMENT_TYPES.map((t) => (
                      <option key={t.value} value={t.value}>
                        {t.label}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {movementsError && (
                <div className="alert-box error" style={{ marginBottom: '1rem' }}>
                  <ShieldAlert size={18} style={{ flexShrink: 0 }} />
                  <div>{movementsError}</div>
                </div>
              )}

              {movementsLoading ? (
                <div style={{ textAlign: 'center', padding: '3.5rem 1rem', color: 'var(--text-muted)' }}>
                  <span className="pulse-dot" style={{ background: 'var(--accent-indigo)', margin: '0 auto 0.75rem' }} />
                  <div>Loading immutable ledger records from PostgreSQL...</div>
                </div>
              ) : filteredMovements.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '3.5rem 1rem', color: 'var(--text-muted)' }}>
                  <History size={32} style={{ margin: '0 auto 0.75rem', opacity: 0.4 }} />
                  <p>No inventory movements matching the selected filter.</p>
                </div>
              ) : (
                <div style={{ overflowX: 'auto' }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
                    <thead>
                      <tr style={{ borderBottom: '1px solid var(--border-subtle)', color: 'var(--text-muted)', fontSize: '0.725rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                        <th style={{ padding: '0.75rem 1rem' }}>Timestamp</th>
                        <th style={{ padding: '0.75rem 1rem' }}>Product &amp; SKU</th>
                        <th style={{ padding: '0.75rem 1rem' }}>Movement Type</th>
                        <th style={{ padding: '0.75rem 1rem' }}>Delta</th>
                        <th style={{ padding: '0.75rem 1rem' }}>Stock Before &rarr; After</th>
                        <th style={{ padding: '0.75rem 1rem' }}>Audit Reason</th>
                        <th style={{ padding: '0.75rem 1rem' }}>Reference ID</th>
                        <th style={{ padding: '0.75rem 1rem' }}>Actor</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredMovements.map((m) => {
                        const dateObj = new Date(m.createdAt);
                        const formattedDate = dateObj.toLocaleDateString();
                        const formattedTime = dateObj.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });

                        return (
                          <tr
                            key={m.id}
                            style={{
                              borderBottom: '1px solid rgba(255, 255, 255, 0.04)',
                              transition: 'background 0.15s ease',
                            }}
                          >
                            <td
                              style={{ padding: '0.85rem 1rem', color: 'var(--text-secondary)', fontSize: '0.78rem', whiteSpace: 'nowrap' }}
                              title={`Full ISO timestamp: ${m.createdAt}`}
                            >
                              <div>{formattedDate}</div>
                              <div style={{ color: 'var(--text-muted)', fontSize: '0.72rem' }}>{formattedTime}</div>
                            </td>
                            <td style={{ padding: '0.85rem 1rem' }}>
                              <div style={{ fontWeight: 600, color: '#f8fafc' }}>{m.productName}</div>
                              <span className="product-sku-pill" style={{ fontSize: '0.68rem' }}>
                                {m.productSku}
                              </span>
                            </td>
                            <td style={{ padding: '0.85rem 1rem' }}>
                              <span style={{ fontSize: '0.72rem', padding: '0.2rem 0.5rem', borderRadius: '4px', background: 'rgba(255, 255, 255, 0.06)', fontWeight: 600, fontFamily: 'var(--font-mono)' }}>
                                {m.movementType}
                              </span>
                            </td>
                            <td style={{ padding: '0.85rem 1rem' }}>
                              <span className={`delta-badge ${m.quantityChange > 0 ? 'positive' : m.quantityChange < 0 ? 'negative' : ''}`}>
                                {m.quantityChange > 0 ? <ArrowUpRight size={13} /> : m.quantityChange < 0 ? <ArrowDownRight size={13} /> : null}
                                <span>{m.quantityChange > 0 ? `+${m.quantityChange}` : m.quantityChange}</span>
                              </span>
                            </td>
                            <td style={{ padding: '0.85rem 1rem', whiteSpace: 'nowrap', color: 'var(--text-secondary)' }}>
                              <span style={{ fontFamily: 'var(--font-mono)' }}>{m.previousStock}</span>
                              <span style={{ margin: '0 0.35rem', color: 'var(--text-muted)' }}>&rarr;</span>
                              <span style={{ fontWeight: 700, color: '#f8fafc', fontFamily: 'var(--font-mono)' }}>{m.newStock}</span>
                            </td>
                            <td style={{ padding: '0.85rem 1rem', color: '#f8fafc', maxWidth: '240px' }}>
                              {m.reason}
                            </td>
                            <td style={{ padding: '0.85rem 1rem', color: 'var(--text-muted)', fontSize: '0.78rem', fontFamily: 'var(--font-mono)' }}>
                              {m.referenceId || '—'}
                            </td>
                            <td style={{ padding: '0.85rem 1rem', fontSize: '0.78rem', color: 'var(--text-secondary)' }}>
                              {m.actorEmail ? (
                                <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem' }}>
                                  <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: 'var(--accent-cyan)' }} />
                                  <span>{m.actorEmail}</span>
                                </span>
                              ) : (
                                <span style={{ color: 'var(--text-muted)', fontWeight: 600 }}>SYSTEM</span>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}

              {/* Pagination Controls */}
              {movementsPageData && movementsPageData.totalPages > 1 && (
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: '1.5rem', paddingTop: '1rem', borderTop: '1px solid var(--border-subtle)' }}>
                  <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                    Page {movementsPageData.pageNumber + 1} of {movementsPageData.totalPages} ({movementsPageData.totalElements} records)
                  </div>
                  <div style={{ display: 'flex', gap: '0.5rem' }}>
                    <button
                      onClick={() => setMovementsPage((p) => Math.max(0, p - 1))}
                      disabled={movementsPageData.first}
                      className="btn-secondary"
                      style={{ padding: '0.35rem 0.65rem' }}
                      aria-label="Previous audit ledger page"
                    >
                      <ChevronLeft size={16} />
                    </button>
                    <button
                      onClick={() => setMovementsPage((p) => p + 1)}
                      disabled={movementsPageData.last}
                      className="btn-secondary"
                      style={{ padding: '0.35rem 0.65rem' }}
                      aria-label="Next audit ledger page"
                    >
                      <ChevronRight size={16} />
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Stock Adjustment Modal */}
          <StockAdjustmentModal
            isOpen={isModalOpen}
            onClose={() => setIsModalOpen(false)}
            onSuccess={handleAdjustmentSuccess}
            inventory={selectedInventory}
          />
        </div>
      )}
    </section>
  );
};
