import { useState } from 'react';
import { AuthProvider } from './context/AuthContext';
import { CartProvider } from './context/CartContext';
import { Navbar } from './components/Navbar';
import { AuthSection } from './components/AuthSection';
import { ProductCatalogSection } from './components/ProductCatalogSection';
import { InventorySection } from './components/InventorySection';
import { CartModal } from './components/CartModal';
import { CustomerOrdersModal } from './components/CustomerOrdersModal';
import { OrderResponse } from './types/order';
import { ShieldCheck, Package, Boxes, User } from 'lucide-react';

function AppContent() {
  const [isCartOpen, setIsCartOpen] = useState(false);
  const [isOrdersOpen, setIsOrdersOpen] = useState(false);
  const [selectedOrderId, setSelectedOrderId] = useState<number | null>(null);

  const scrollToAuth = () => {
    document.getElementById('auth-console-section')?.scrollIntoView({ behavior: 'smooth' });
  };

  const scrollToCatalog = () => {
    document.getElementById('catalog-section')?.scrollIntoView({ behavior: 'smooth' });
  };

  const scrollToInventory = () => {
    document.getElementById('inventory-section')?.scrollIntoView({ behavior: 'smooth' });
  };

  const handleOrderPlaced = (order: OrderResponse) => {
    setSelectedOrderId(order.id);
    setIsOrdersOpen(true);
  };

  return (
    <div className="app-layout">
      <Navbar
        onOpenCart={() => setIsCartOpen(true)}
        onOpenOrders={() => {
          setSelectedOrderId(null);
          setIsOrdersOpen(true);
        }}
      />

      <main className="container">
        {/* Commercial SaaS Hero Section */}
        <section className="hero">
          <div className="hero-pill">
            <ShieldCheck size={16} />
            <span>Enterprise Inventory &amp; Order Management</span>
          </div>

          <h1 className="hero-title">
            Modern Inventory Control &amp; <br />
            <span className="gradient-text">Automated Order Fulfillment</span>
          </h1>

          <p className="hero-subtitle">
            StockGuard empowers operations teams to manage product catalogs, track live stock quantities across categories, and process high-volume customer orders with guaranteed zero overselling.
          </p>

          <div className="hero-cta-group">
            <button
              onClick={scrollToCatalog}
              className="btn btn-primary btn-lg"
            >
              <Package size={18} />
              <span>Explore Products</span>
            </button>

            <button
              onClick={scrollToInventory}
              className="btn btn-secondary btn-lg"
            >
              <Boxes size={18} />
              <span>Manage Inventory</span>
            </button>

            <button
              onClick={scrollToAuth}
              className="btn btn-outline btn-lg"
            >
              <User size={18} />
              <span>Sign In / Account</span>
            </button>
          </div>
        </section>

        {/* Product Catalog & Admin Product CRUD */}
        <ProductCatalogSection />

        {/* Inventory & Audit Movement Ledger */}
        <InventorySection />

        {/* Account Authentication & User Profile */}
        <AuthSection />
      </main>

      {/* Cart Modal */}
      <CartModal
        isOpen={isCartOpen}
        onClose={() => setIsCartOpen(false)}
        onOpenAuth={scrollToAuth}
        onOrderPlaced={handleOrderPlaced}
      />

      {/* Customer Orders History & Detail Modal */}
      <CustomerOrdersModal
        isOpen={isOrdersOpen}
        onClose={() => setIsOrdersOpen(false)}
        selectedOrderId={selectedOrderId}
      />

      {/* Footer */}
      <footer className="footer">
        <div className="container footer-inner">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontWeight: 600, color: 'var(--text-secondary)' }}>
            <ShieldCheck size={18} style={{ color: 'var(--accent-cyan)' }} />
            <span>StockGuard — Enterprise Inventory Management</span>
          </div>
          <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
            &copy; {new Date().getFullYear()} StockGuard SaaS. All rights reserved.
          </div>
        </div>
      </footer>
    </div>
  );
}

export function App() {
  return (
    <AuthProvider>
      <CartProvider>
        <AppContent />
      </CartProvider>
    </AuthProvider>
  );
}

export default App;
