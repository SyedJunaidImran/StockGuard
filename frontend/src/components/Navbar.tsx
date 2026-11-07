import React, { useState, useEffect } from 'react';
import {
  ShieldCheck,
  Database,
  Boxes,
  LogIn,
  LogOut,
  User as UserIcon,
  ShoppingCart,
  Package,
  Menu,
  X
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useCart } from '../context/CartContext';

interface NavbarProps {
  onOpenCart?: () => void;
  onOpenOrders?: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({
  onOpenCart,
  onOpenOrders,
}) => {
  const { user, isAuthenticated, logout } = useAuth();
  const { totalCount } = useCart();
  const isCustomer = isAuthenticated && user?.role === 'CUSTOMER';

  const [isDrawerOpen, setIsDrawerOpen] = useState(false);

  // Lock body scroll when mobile drawer is open
  useEffect(() => {
    if (isDrawerOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
    return () => {
      document.body.style.overflow = '';
    };
  }, [isDrawerOpen]);

  // Handle ESC key to dismiss drawer
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isDrawerOpen) {
        setIsDrawerOpen(false);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isDrawerOpen]);

  const scrollTo = (elementId: string) => {
    setIsDrawerOpen(false);
    setTimeout(() => {
      document.getElementById(elementId)?.scrollIntoView({ behavior: 'smooth' });
    }, 100);
  };

  return (
    <header className="navbar">
      <div className="container navbar-inner">
        {/* Brand Group */}
        <div className="brand" style={{ cursor: 'pointer' }} onClick={() => scrollTo('catalog-section')}>
          <div className="brand-icon-wrapper">
            <ShieldCheck size={22} />
          </div>
          <div className="brand-text">
            <span>StockGuard</span>
          </div>
        </div>

        {/* Desktop Navigation Links (hidden <= 900px) */}
        <nav className="nav-links desktop-nav" aria-label="Desktop Navigation">
          <button
            onClick={() => scrollTo('catalog-section')}
            className="nav-link"
          >
            <Database size={15} />
            <span>Catalog</span>
          </button>

          {isCustomer && onOpenOrders && (
            <button
              onClick={onOpenOrders}
              className="nav-link"
              title="View customer order history"
            >
              <Package size={15} style={{ color: 'var(--accent-emerald)' }} />
              <span>My Orders</span>
            </button>
          )}

          <button
            onClick={() => scrollTo('inventory-section')}
            className="nav-link"
          >
            <Boxes size={15} />
            <span>Inventory</span>
          </button>

          <button
            onClick={() => scrollTo('auth-console-section')}
            className="nav-link"
          >
            <UserIcon size={15} />
            <span>Account</span>
          </button>
        </nav>

        {/* Right Actions: Cart, Auth & Mobile Hamburger */}
        <div className="navbar-actions">
          {/* Cart Trigger (always visible on desktop and mobile) */}
          {onOpenCart && (
            <button
              onClick={onOpenCart}
              className="btn-secondary"
              style={{
                padding: '0.4rem 0.8rem',
                fontSize: '0.825rem',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.45rem',
                background: totalCount > 0 ? 'rgba(56, 189, 248, 0.12)' : 'rgba(255, 255, 255, 0.05)',
                borderColor: totalCount > 0 ? 'rgba(56, 189, 248, 0.4)' : undefined,
              }}
              title="View Cart"
              aria-label={`Shopping cart with ${totalCount} items`}
            >
              <ShoppingCart size={15} style={{ color: totalCount > 0 ? 'var(--accent-cyan)' : 'var(--text-secondary)' }} />
              <span className="desktop-only">Cart</span>
              {totalCount > 0 && (
                <span
                  style={{
                    background: 'var(--accent-cyan)',
                    color: '#020617',
                    borderRadius: 'var(--radius-full)',
                    padding: '0.1rem 0.45rem',
                    fontSize: '0.7rem',
                    fontWeight: 700,
                    lineHeight: 1,
                  }}
                >
                  {totalCount}
                </span>
              )}
            </button>
          )}

          {/* User Auth Info (Desktop) */}
          <div className="desktop-nav" style={{ display: 'flex', alignItems: 'center', gap: '0.65rem' }}>
            {isAuthenticated && user ? (
              <>
                <button
                  onClick={() => scrollTo('auth-console-section')}
                  className="btn-secondary"
                  style={{ padding: '0.35rem 0.75rem', fontSize: '0.8rem', borderRadius: 'var(--radius-full)' }}
                  title="View active session"
                >
                  <UserIcon size={14} style={{ color: 'var(--accent-cyan)' }} />
                  <span>{user.firstName}</span>
                  <span className={`role-badge ${user.role.toLowerCase()}`} style={{ fontSize: '0.65rem', padding: '0.1rem 0.45rem' }}>
                    {user.role}
                  </span>
                </button>

                <button
                  onClick={logout}
                  className="btn-secondary"
                  title="Sign Out"
                  style={{ padding: '0.35rem 0.6rem', color: 'var(--accent-rose)' }}
                  aria-label="Sign out"
                >
                  <LogOut size={14} />
                </button>
              </>
            ) : (
              <button
                onClick={() => scrollTo('auth-console-section')}
                className="btn-secondary"
                style={{
                  padding: '0.4rem 0.85rem',
                  fontSize: '0.8rem',
                  background: 'rgba(56, 189, 248, 0.1)',
                  borderColor: 'rgba(56, 189, 248, 0.3)',
                  color: '#38bdf8',
                  fontWeight: 600,
                }}
              >
                <LogIn size={14} />
                <span>Sign In</span>
              </button>
            )}
          </div>

          {/* Hamburger Button (Mobile / Tablet <= 900px) */}
          <button
            onClick={() => setIsDrawerOpen(!isDrawerOpen)}
            className="hamburger-btn"
            aria-label={isDrawerOpen ? 'Close navigation menu' : 'Open navigation menu'}
            aria-expanded={isDrawerOpen}
          >
            {isDrawerOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>
      </div>

      {/* Slide-out Mobile Navigation Drawer */}
      {isDrawerOpen && (
        <div
          className="mobile-drawer-overlay"
          onClick={() => setIsDrawerOpen(false)}
          aria-hidden="true"
        >
          <div
            className="mobile-drawer"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
            aria-label="Mobile Navigation Drawer"
          >
            {/* Drawer Header */}
            <div className="mobile-drawer-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem' }}>
                <div className="brand-icon-wrapper" style={{ width: '2rem', height: '2rem' }}>
                  <ShieldCheck size={18} />
                </div>
                <span style={{ fontWeight: 700, fontSize: '1.1rem' }}>StockGuard</span>
              </div>
              <button
                onClick={() => setIsDrawerOpen(false)}
                className="btn-icon"
                aria-label="Close navigation drawer"
              >
                <X size={18} />
              </button>
            </div>

            {/* Drawer Body */}
            <div className="mobile-drawer-body">
              {/* User Session Banner */}
              {isAuthenticated && user ? (
                <div
                  style={{
                    padding: '0.85rem',
                    background: 'rgba(255, 255, 255, 0.04)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: 'var(--radius-sm)',
                    marginBottom: '0.5rem',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.35rem' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem', fontWeight: 600, fontSize: '0.9rem' }}>
                      <UserIcon size={15} style={{ color: 'var(--accent-cyan)' }} />
                      <span>{user.firstName} {user.lastName}</span>
                    </div>
                    <span className={`role-badge ${user.role.toLowerCase()}`} style={{ fontSize: '0.65rem' }}>
                      {user.role}
                    </span>
                  </div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                    {user.email}
                  </div>
                </div>
              ) : (
                <button
                  onClick={() => scrollTo('auth-console-section')}
                  className="btn-primary"
                  style={{ width: '100%', marginBottom: '0.5rem', fontSize: '0.85rem' }}
                >
                  <LogIn size={15} />
                  <span>Sign In / Register</span>
                </button>
              )}

              {/* Navigation Links */}
              <button
                onClick={() => scrollTo('catalog-section')}
                className="mobile-drawer-link"
              >
                <Database size={16} style={{ color: 'var(--accent-cyan)' }} />
                <span>Product Catalog</span>
              </button>

              {isCustomer && onOpenOrders && (
                <button
                  onClick={() => {
                    setIsDrawerOpen(false);
                    onOpenOrders();
                  }}
                  className="mobile-drawer-link"
                >
                  <Package size={16} style={{ color: 'var(--accent-emerald)' }} />
                  <span>My Orders</span>
                </button>
              )}

              <button
                onClick={() => scrollTo('inventory-section')}
                className="mobile-drawer-link"
              >
                <Boxes size={16} style={{ color: 'var(--accent-amber)' }} />
                <span>Inventory &amp; Stock</span>
              </button>

              <button
                onClick={() => scrollTo('auth-console-section')}
                className="mobile-drawer-link"
              >
                <UserIcon size={16} style={{ color: 'var(--accent-cyan)' }} />
                <span>My Account</span>
              </button>

              {onOpenCart && (
                <button
                  onClick={() => {
                    setIsDrawerOpen(false);
                    onOpenCart();
                  }}
                  className="mobile-drawer-link"
                  style={{ marginTop: '0.25rem', border: '1px solid rgba(56, 189, 248, 0.2)' }}
                >
                  <ShoppingCart size={16} style={{ color: 'var(--accent-cyan)' }} />
                  <span>Shopping Cart</span>
                  {totalCount > 0 && (
                    <span
                      style={{
                        marginLeft: 'auto',
                        background: 'var(--accent-cyan)',
                        color: '#020617',
                        borderRadius: 'var(--radius-full)',
                        padding: '0.1rem 0.45rem',
                        fontSize: '0.7rem',
                        fontWeight: 700,
                      }}
                    >
                      {totalCount} items
                    </span>
                  )}
                </button>
              )}
            </div>

            {/* Drawer Footer */}
            <div className="mobile-drawer-footer">
              {isAuthenticated ? (
                <button
                  onClick={() => {
                    setIsDrawerOpen(false);
                    logout();
                  }}
                  className="btn-secondary"
                  style={{ width: '100%', color: 'var(--accent-rose)', justifyContent: 'center' }}
                >
                  <LogOut size={15} />
                  <span>Sign Out</span>
                </button>
              ) : (
                <div style={{ textAlign: 'center', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                  StockGuard Enterprise
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </header>
  );
};
