import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';
import { Category, Product, PageResponse } from '../types/product';
import { Inventory } from '../types/inventory';
import { productService } from '../services/productService';
import { inventoryService } from '../services/inventoryService';
import { ApiError } from '../services/api';
import { AdminProductModal } from './AdminProductModal';
import { ProductDetailModal } from './ProductDetailModal';
import { ProductThumbnail } from './ProductThumbnail';
import {
  Search,
  Plus,
  SlidersHorizontal,
  ChevronLeft,
  ChevronRight,
  Package,
  Edit2,
  Power,
  RotateCcw,
  AlertCircle,
  Eye,
  CheckCircle2,
  Shield,
  ShoppingCart
} from 'lucide-react';

export const ProductCatalogSection: React.FC = () => {
  const { user } = useAuth();
  const { addItem } = useCart();
  const isAdmin = user?.role === 'ADMIN';

  // Catalog state
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [pageData, setPageData] = useState<PageResponse<Product> | null>(null);

  // Live Inventory Mapping State (ADMIN view maps live stock from inventory service)
  const [inventoryMap, setInventoryMap] = useState<Record<number, Inventory>>({});

  // Filters state
  const [searchInput, setSearchInput] = useState('');
  const [activeSearch, setActiveSearch] = useState('');
  const [selectedCategoryId, setSelectedCategoryId] = useState<number | null>(null);
  const [sortBy, setSortBy] = useState<'createdAt' | 'price' | 'name'>('createdAt');
  const [sortDir, setSortDir] = useState<'ASC' | 'DESC'>('DESC');
  const [page, setPage] = useState(0);
  const pageSize = 8;
  const [showInactive, setShowInactive] = useState(false);

  // UI States
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusActionId, setStatusActionId] = useState<number | null>(null);
  const [actionSuccessMsg, setActionSuccessMsg] = useState<string | null>(null);
  const [recentlyAddedId, setRecentlyAddedId] = useState<number | null>(null);

  // Modals state
  const [isAdminModalOpen, setIsAdminModalOpen] = useState(false);
  const [productToEdit, setProductToEdit] = useState<Product | null>(null);
  const [detailModalProduct, setDetailModalProduct] = useState<Product | null>(null);

  // Load categories
  useEffect(() => {
    productService.getCategories()
      .then(setCategories)
      .catch((err) => console.warn('Could not load categories:', err));
  }, []);

  // Load inventory mapping for ADMIN users (retrieves real stock records)
  const loadInventory = useCallback(async () => {
    if (!isAdmin) {
      setInventoryMap({});
      return;
    }
    try {
      const data = await inventoryService.getAllInventory(0, 100);
      const map: Record<number, Inventory> = {};
      data.content.forEach((inv) => {
        map[inv.productId] = inv;
      });
      setInventoryMap(map);
    } catch (err: unknown) {
      console.warn('Could not load inventory records:', err);
    }
  }, [isAdmin]);

  // Load products
  const fetchProducts = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await productService.getProducts({
        search: activeSearch || undefined,
        categoryId: selectedCategoryId || undefined,
        active: isAdmin && showInactive ? undefined : true,
        page,
        size: pageSize,
        sortBy,
        sortDir,
      });
      setProducts(data.content);
      setPageData(data);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to load catalog products. Verify backend service is reachable.');
      }
    } finally {
      setIsLoading(false);
    }
  }, [activeSearch, selectedCategoryId, isAdmin, showInactive, page, sortBy, sortDir]);

  useEffect(() => {
    fetchProducts();
    if (isAdmin) {
      loadInventory();
    }
  }, [fetchProducts, loadInventory, isAdmin]);

  // Listen for global inventory adjustment events to refresh stock levels
  useEffect(() => {
    const handleInventoryUpdate = () => {
      if (isAdmin) {
        loadInventory();
      }
    };
    window.addEventListener('stockguard:inventory-updated', handleInventoryUpdate);
    return () => {
      window.removeEventListener('stockguard:inventory-updated', handleInventoryUpdate);
    };
  }, [isAdmin, loadInventory]);

  // Handlers
  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    setActiveSearch(searchInput.trim());
  };

  const handleResetFilters = () => {
    setSearchInput('');
    setActiveSearch('');
    setSelectedCategoryId(null);
    setSortBy('createdAt');
    setSortDir('DESC');
    setPage(0);
    setShowInactive(false);
  };

  const handleToggleStatus = async (product: Product) => {
    setStatusActionId(product.id);
    setActionSuccessMsg(null);
    try {
      const updated = await productService.updateProductStatus(product.id, !product.active);
      setProducts((prev) =>
        prev.map((p) => (p.id === updated.id ? updated : p))
      );
      setActionSuccessMsg(`Product "${updated.name}" status changed to ${updated.active ? 'ACTIVE' : 'INACTIVE'}.`);
      setTimeout(() => setActionSuccessMsg(null), 4000);
      if (isAdmin) {
        loadInventory();
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to update product status';
      setError(msg);
    } finally {
      setStatusActionId(null);
    }
  };

  const openCreateModal = () => {
    setProductToEdit(null);
    setIsAdminModalOpen(true);
  };

  const openEditModal = (product: Product) => {
    setProductToEdit(product);
    setIsAdminModalOpen(true);
  };

  // Stock Badge Renderer
  const renderStockBadge = (product: Product, inv?: Inventory) => {
    if (inv) {
      if (inv.availableStock <= 0) {
        return (
          <span className="stock-badge out-of-stock" title="Warehouse reports zero available stock">
            <span className="stock-badge-dot" />
            Out of Stock (0 units)
          </span>
        );
      }
      if (inv.lowStock || inv.availableStock <= inv.lowStockThreshold) {
        return (
          <span className="stock-badge low-stock" title={`Low stock alert threshold: ${inv.lowStockThreshold} units`}>
            <span className="stock-badge-dot" />
            Low Stock ({inv.availableStock} left)
          </span>
        );
      }
      return (
        <span className="stock-badge in-stock" title="Ready for dispatch">
          <span className="stock-badge-dot" />
          In Stock ({inv.availableStock} units)
        </span>
      );
    }

    // Honest availability state for non-admin visitors / guests
    if (!product.active) {
      return (
        <span className="stock-badge inactive" title="Item is currently inactive in catalog">
          <span className="stock-badge-dot" />
          Catalog Inactive
        </span>
      );
    }

    return (
      <span className="stock-badge in-stock" title="Available for purchase">
        <span className="stock-badge-dot" />
        In Stock
      </span>
    );
  };

  return (
    <section id="catalog-section" className="catalog-wrapper">
      {/* Section Header */}
      <div className="section-header" style={{ textAlign: 'center', marginBottom: '2rem' }}>
        <div className="hero-pill" style={{ margin: '0 auto 1rem', display: 'inline-flex' }}>
          <Package size={16} />
          <span>Product Catalog</span>
        </div>
        <h2 style={{ fontSize: '1.875rem', fontWeight: 700, letterSpacing: '-0.02em', marginBottom: '0.5rem' }}>
          Explore Products &amp; <span className="gradient-text">Hardware</span>
        </h2>
        <p style={{ color: 'var(--text-secondary)', maxWidth: '680px', margin: '0 auto', fontSize: '0.95rem' }}>
          Browse high-performance enterprise hardware components with live stock availability and instant ordering.
        </p>
      </div>

      {actionSuccessMsg && (
        <div className="alert-box success" style={{ maxWidth: '1200px', margin: '0 auto 1.5rem' }}>
          <CheckCircle2 size={18} style={{ flexShrink: 0 }} />
          <span>{actionSuccessMsg}</span>
        </div>
      )}

      {/* Catalog Controls Console */}
      <div className="catalog-controls" style={{ maxWidth: '1200px', margin: '0 auto 2rem' }}>
        <div className="controls-row-main">
          {/* Search Form */}
          <form onSubmit={handleSearchSubmit} className="catalog-search-box">
            <Search size={16} className="input-icon" />
            <input
              type="text"
              className="catalog-search-input"
              placeholder="Search products by title or description..."
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
            />
          </form>

          {/* Sort & Filter Controls */}
          <div className="controls-select-group">
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', color: 'var(--text-muted)', fontSize: '0.8rem' }}>
              <SlidersHorizontal size={14} />
              <span>Sort:</span>
            </div>

            <select
              className="catalog-select"
              value={`${sortBy}_${sortDir}`}
              onChange={(e) => {
                const [sb, sd] = e.target.value.split('_');
                setSortBy(sb as 'createdAt' | 'price' | 'name');
                setSortDir(sd as 'ASC' | 'DESC');
                setPage(0);
              }}
            >
              <option value="createdAt_DESC">Newest First</option>
              <option value="price_ASC">Price: Low to High</option>
              <option value="price_DESC">Price: High to Low</option>
              <option value="name_ASC">Name: A to Z</option>
            </select>

            {/* Admin Action Button */}
            {isAdmin && (
              <button
                onClick={openCreateModal}
                className="btn-primary"
                style={{
                  width: 'auto',
                  marginTop: 0,
                  padding: '0.65rem 1.15rem',
                  fontSize: '0.85rem',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '0.4rem',
                }}
              >
                <Plus size={16} />
                <span>Add Product</span>
              </button>
            )}
          </div>
        </div>

        {/* Category Chips Bar (Horizontally scrollable on mobile) */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '1rem', flexWrap: 'wrap', borderTop: '1px solid var(--border-subtle)', paddingTop: '0.85rem' }}>
          <div className="category-filter-chips">
            <button
              onClick={() => {
                setSelectedCategoryId(null);
                setPage(0);
              }}
              className={`category-chip ${selectedCategoryId === null ? 'active' : ''}`}
            >
              All Categories
            </button>
            {categories.map((c) => (
              <button
                key={c.id}
                onClick={() => {
                  setSelectedCategoryId(c.id);
                  setPage(0);
                }}
                className={`category-chip ${selectedCategoryId === c.id ? 'active' : ''}`}
              >
                {c.name}
              </button>
            ))}
          </div>

          {/* Admin Inactive Products Toggle */}
          {isAdmin && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
              <input
                id="show-inactive-toggle"
                type="checkbox"
                checked={showInactive}
                onChange={(e) => {
                  setShowInactive(e.target.checked);
                  setPage(0);
                }}
                style={{ accentColor: 'var(--accent-amber)', cursor: 'pointer' }}
              />
              <label htmlFor="show-inactive-toggle" style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '0.3rem' }}>
                <Shield size={13} style={{ color: 'var(--accent-amber)' }} />
                <span>Show Inactive (Admin View)</span>
              </label>
            </div>
          )}
        </div>
      </div>

      {/* Main Content Area */}
      <div style={{ maxWidth: '1200px', margin: '0 auto' }}>
        {error ? (
          <div className="alert-box error" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', gap: '0.75rem' }}>
            <AlertCircle size={28} />
            <div style={{ fontWeight: 600, fontSize: '1rem' }}>Failed to retrieve product catalog</div>
            <div style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', maxWidth: '500px' }}>{error}</div>
            <button onClick={fetchProducts} className="btn-secondary" style={{ marginTop: '0.5rem' }}>
              <RotateCcw size={15} />
              <span>Retry Request</span>
            </button>
          </div>
        ) : isLoading ? (
          /* Loading Skeletons */
          <div className="product-grid">
            {[1, 2, 3, 4, 5, 6, 7, 8].map((n) => (
              <div key={n} className="product-card" style={{ opacity: 0.6 }}>
                <div className="product-thumb-wrapper">
                  <span className="pulse-dot" style={{ background: 'var(--accent-cyan)' }} />
                </div>
                <div className="product-card-body">
                  <div style={{ height: '14px', background: 'rgba(255,255,255,0.06)', borderRadius: '4px', width: '40%', marginBottom: '0.75rem' }} />
                  <div style={{ height: '18px', background: 'rgba(255,255,255,0.1)', borderRadius: '4px', width: '85%', marginBottom: '0.5rem' }} />
                  <div style={{ height: '12px', background: 'rgba(255,255,255,0.05)', borderRadius: '4px', width: '100%', marginBottom: '1.5rem' }} />
                  <div style={{ height: '24px', background: 'rgba(255,255,255,0.08)', borderRadius: '4px', width: '30%', marginTop: 'auto' }} />
                </div>
              </div>
            ))}
          </div>
        ) : products.length === 0 ? (
          /* Empty State */
          <div className="glass-card" style={{ padding: '3.5rem 2rem', textAlign: 'center', marginBottom: '3rem' }}>
            <div style={{ width: '56px', height: '56px', borderRadius: 'var(--radius-full)', background: 'rgba(56, 189, 248, 0.1)', border: '1px solid rgba(56, 189, 248, 0.25)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 1.25rem', color: 'var(--accent-cyan)' }}>
              <Package size={28} />
            </div>
            <h3 style={{ fontSize: '1.25rem', fontWeight: 600, marginBottom: '0.5rem' }}>
              No Products Found
            </h3>
            <p style={{ color: 'var(--text-secondary)', maxWidth: '440px', margin: '0 auto 1.5rem', fontSize: '0.875rem' }}>
              {activeSearch
                ? `No catalog items matched your search query "${activeSearch}".`
                : selectedCategoryId
                ? 'No items currently listed in this category.'
                : 'The product catalog database is currently empty.'}
            </p>
            <div style={{ display: 'flex', justifyContent: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
              {(activeSearch || selectedCategoryId !== null) && (
                <button onClick={handleResetFilters} className="btn-secondary">
                  <RotateCcw size={15} />
                  <span>Clear All Filters</span>
                </button>
              )}
              {isAdmin && (
                <button onClick={openCreateModal} className="btn-primary" style={{ width: 'auto', marginTop: 0, padding: '0.55rem 1.25rem' }}>
                  <Plus size={16} />
                  <span>Create First Product</span>
                </button>
              )}
            </div>
          </div>
        ) : (
          /* Product Grid */
          <>
            <div className="product-grid">
              {products.map((p) => {
                const inv = inventoryMap[p.id];
                const isOutOfStock = inv ? inv.availableStock <= 0 : false;

                return (
                  <div key={p.id} className="product-card">
                    {/* Admin Quick Action Floating Overlay */}
                    {isAdmin && (
                      <div className="product-card-admin-bar">
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            openEditModal(p);
                          }}
                          className="admin-mini-btn"
                          title="Edit Product (Admin)"
                          aria-label={`Edit ${p.name}`}
                        >
                          <Edit2 size={13} />
                        </button>

                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            handleToggleStatus(p);
                          }}
                          disabled={statusActionId === p.id}
                          className="admin-mini-btn"
                          style={{
                            color: p.active ? 'var(--accent-rose)' : 'var(--accent-emerald)',
                          }}
                          title={p.active ? 'Deactivate Product' : 'Activate Product'}
                          aria-label={p.active ? 'Deactivate product' : 'Activate product'}
                        >
                          <Power size={13} />
                        </button>
                      </div>
                    )}

                    {/* Thumbnail with hardware SVG fallback */}
                    <ProductThumbnail
                      src={p.imageUrl}
                      alt={p.name}
                      categoryName={p.category?.name}
                      sku={p.sku}
                    />

                    {/* Body */}
                    <div className="product-card-body">
                      <div className="product-card-header">
                        <span className="product-category-tag">
                          {p.category?.name || 'Uncategorized'}
                        </span>
                        <span className="product-sku-pill">{p.sku}</span>
                      </div>

                      <h4 className="product-card-title">{p.name}</h4>
                      <p className="product-card-desc">{p.description || 'Enterprise catalog component.'}</p>

                      {/* Stock Status Row */}
                      <div style={{ marginBottom: '0.85rem' }}>
                        {renderStockBadge(p, inv)}
                      </div>

                      {/* Footer */}
                      <div className="product-card-footer">
                        <div>
                          <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Unit Price</div>
                          <div className="product-card-price">${p.price.toFixed(2)}</div>
                        </div>

                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem' }}>
                          <button
                            onClick={() => setDetailModalProduct(p)}
                            className="btn-secondary"
                            style={{ padding: '0.4rem 0.7rem', fontSize: '0.8rem' }}
                            title="View Product Specifications"
                          >
                            <Eye size={14} />
                            <span>Details</span>
                          </button>

                          <button
                            onClick={() => {
                              addItem(p, 1);
                              setRecentlyAddedId(p.id);
                              setTimeout(() => setRecentlyAddedId(null), 1400);
                            }}
                            disabled={!p.active || isOutOfStock}
                            className="btn-primary"
                            style={{
                              padding: '0.4rem 0.8rem',
                              fontSize: '0.8rem',
                              opacity: !p.active || isOutOfStock ? 0.5 : 1,
                              cursor: !p.active || isOutOfStock ? 'not-allowed' : 'pointer',
                            }}
                            title={
                              !p.active
                                ? 'Product is inactive'
                                : isOutOfStock
                                ? 'Out of Stock'
                                : 'Add 1 unit to cart'
                            }
                          >
                            {recentlyAddedId === p.id ? (
                              <>
                                <CheckCircle2 size={13} />
                                <span>Added</span>
                              </>
                            ) : (
                              <>
                                <ShoppingCart size={13} />
                                <span>{isOutOfStock ? 'Sold Out' : 'Add'}</span>
                              </>
                            )}
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Pagination Bar */}
            {pageData && pageData.totalPages > 1 && (
              <div className="pagination-wrapper">
                <div className="pagination-info">
                  Showing <strong>{page * pageSize + 1}</strong> -{' '}
                  <strong>{Math.min((page + 1) * pageSize, pageData.totalElements)}</strong> of{' '}
                  <strong>{pageData.totalElements}</strong> products
                </div>

                <div className="pagination-controls">
                  <button
                    onClick={() => setPage((prev) => Math.max(0, prev - 1))}
                    disabled={pageData.first || isLoading}
                    className="pagination-btn"
                  >
                    <ChevronLeft size={16} />
                    <span>Previous</span>
                  </button>

                  <span className="pagination-current">
                    Page {page + 1} of {pageData.totalPages}
                  </span>

                  <button
                    onClick={() => setPage((prev) => prev + 1)}
                    disabled={pageData.last || isLoading}
                    className="pagination-btn"
                  >
                    <span>Next</span>
                    <ChevronRight size={16} />
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {/* Admin Create / Edit Modal */}
      <AdminProductModal
        isOpen={isAdminModalOpen}
        onClose={() => setIsAdminModalOpen(false)}
        onSuccess={() => {
          fetchProducts();
          if (isAdmin) {
            loadInventory();
          }
          setActionSuccessMsg(
            productToEdit ? 'Product updated successfully.' : 'Product created successfully.'
          );
          setTimeout(() => setActionSuccessMsg(null), 4000);
        }}
        productToEdit={productToEdit}
        categories={categories}
      />

      {/* Product Detail Modal */}
      <ProductDetailModal
        product={detailModalProduct}
        inventory={detailModalProduct ? inventoryMap[detailModalProduct.id] : undefined}
        onClose={() => setDetailModalProduct(null)}
      />
    </section>
  );
};
