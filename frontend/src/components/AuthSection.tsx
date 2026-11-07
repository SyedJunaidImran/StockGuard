import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../services/api';
import {
  Lock,
  Mail,
  User as UserIcon,
  Eye,
  EyeOff,
  LogIn,
  UserPlus,
  CheckCircle2,
  AlertCircle,
  LogOut,
  Package,
  Boxes
} from 'lucide-react';

export const AuthSection: React.FC = () => {
  const { user, isAuthenticated, isLoading, login, register, logout } = useAuth();

  const [activeTab, setActiveTab] = useState<'LOGIN' | 'REGISTER'>('LOGIN');

  // Login form state
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [showLoginPassword, setShowLoginPassword] = useState(false);
  const [loginSubmitting, setLoginSubmitting] = useState(false);
  const [loginError, setLoginError] = useState<string | null>(null);

  // Register form state
  const [registerName, setRegisterName] = useState('');
  const [registerEmail, setRegisterEmail] = useState('');
  const [registerPassword, setRegisterPassword] = useState('');
  const [registerConfirmPassword, setRegisterConfirmPassword] = useState('');
  const [showRegisterPassword, setShowRegisterPassword] = useState(false);
  const [registerSubmitting, setRegisterSubmitting] = useState(false);
  const [registerError, setRegisterError] = useState<string | null>(null);
  const [registerSuccessMessage, setRegisterSuccessMessage] = useState<string | null>(null);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoginError(null);

    if (!loginEmail.trim() || !loginPassword) {
      setLoginError('Please enter both email and password.');
      return;
    }

    setLoginSubmitting(true);
    try {
      await login({
        email: loginEmail.trim(),
        password: loginPassword,
      });
      setLoginPassword('');
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setLoginError(err.message);
      } else if (err instanceof Error) {
        setLoginError(err.message);
      } else {
        setLoginError('Failed to authenticate. Verify backend availability.');
      }
    } finally {
      setLoginSubmitting(false);
    }
  };

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setRegisterError(null);
    setRegisterSuccessMessage(null);

    if (!registerName.trim()) {
      setRegisterError('Please provide your name.');
      return;
    }
    if (!registerEmail.trim() || !registerEmail.includes('@')) {
      setRegisterError('Please provide a valid email address.');
      return;
    }
    if (registerPassword.length < 8) {
      setRegisterError('Password must be at least 8 characters long.');
      return;
    }
    if (registerPassword !== registerConfirmPassword) {
      setRegisterError('Passwords do not match.');
      return;
    }

    setRegisterSubmitting(true);
    try {
      await register({
        name: registerName.trim(),
        email: registerEmail.trim(),
        password: registerPassword,
      });
      setRegisterSuccessMessage('Account registered successfully! Authentication session established.');
      setRegisterPassword('');
      setRegisterConfirmPassword('');
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        let msg = err.message;
        const details = err.details as { validationErrors?: Record<string, string> } | null;
        if (details?.validationErrors) {
          const fieldMsgs = Object.values(details.validationErrors);
          if (fieldMsgs.length > 0) {
            msg = `${msg}: ${fieldMsgs.join(', ')}`;
          }
        }
        setRegisterError(msg);
      } else if (err instanceof Error) {
        setRegisterError(err.message);
      } else {
        setRegisterError('Registration failed. Please try again.');
      }
    } finally {
      setRegisterSubmitting(false);
    }
  };

  const scrollTo = (elementId: string) => {
    document.getElementById(elementId)?.scrollIntoView({ behavior: 'smooth' });
  };

  return (
    <section id="auth-console-section" className="auth-wrapper">
      <div className="section-header" style={{ textAlign: 'center', marginBottom: '2rem' }}>
        <div className="hero-pill" style={{ margin: '0 auto 1rem', display: 'inline-flex' }}>
          <UserIcon size={16} />
          <span>Account &amp; Access</span>
        </div>
        <h2 style={{ fontSize: '1.875rem', fontWeight: 700, letterSpacing: '-0.02em', marginBottom: '0.5rem' }}>
          {isAuthenticated ? 'User Profile & ' : 'Sign In to '}
          <span className="gradient-text">StockGuard</span>
        </h2>
        <p style={{ color: 'var(--text-secondary)', maxWidth: '580px', margin: '0 auto', fontSize: '0.95rem' }}>
          {isAuthenticated
            ? 'Manage your active session, view your role permissions, and navigate your workspace.'
            : 'Access catalog inventory, place concurrency-protected customer orders, or manage warehouse stock.'}
        </p>
      </div>

      <div className="auth-card" style={{ maxWidth: isAuthenticated ? '760px' : '520px', margin: '0 auto' }}>
        {isAuthenticated && user ? (
          /* ====================================================================
             ACTIVE SESSION VIEW
             ==================================================================== */
          <div className="auth-body">
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem', marginBottom: '1.5rem', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '1.25rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <div style={{ width: '48px', height: '48px', borderRadius: 'var(--radius-full)', background: 'linear-gradient(135deg, rgba(56, 189, 248, 0.2), rgba(99, 102, 241, 0.2))', border: '1px solid rgba(56, 189, 248, 0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--accent-cyan)' }}>
                  <UserIcon size={24} />
                </div>
                <div>
                  <h3 style={{ fontSize: '1.2rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '0.5rem', margin: 0 }}>
                    <span>{user.firstName} {user.lastName}</span>
                    <span className={`role-badge ${user.role.toLowerCase()}`}>
                      {user.role}
                    </span>
                  </h3>
                  <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', margin: '0.2rem 0 0' }}>
                    {user.email}
                  </p>
                </div>
              </div>

              <button
                onClick={logout}
                className="btn-secondary"
                style={{ color: 'var(--accent-rose)', borderColor: 'rgba(244, 63, 94, 0.3)', display: 'inline-flex', alignItems: 'center', gap: '0.45rem' }}
              >
                <LogOut size={15} />
                <span>Sign Out</span>
              </button>
            </div>

            {/* Session Attributes Grid */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
              <div className="session-stat-card">
                <div className="session-stat-label">User ID</div>
                <div className="session-stat-val">#{user.id}</div>
              </div>

              <div className="session-stat-card">
                <div className="session-stat-label">Account Role</div>
                <div className="session-stat-val" style={{ color: user.role === 'ADMIN' ? 'var(--accent-amber)' : 'var(--accent-cyan)' }}>
                  {user.role}
                </div>
              </div>

              <div className="session-stat-card">
                <div className="session-stat-label">Account Status</div>
                <div className="session-stat-val" style={{ color: user.active ? 'var(--accent-emerald)' : 'var(--accent-rose)' }}>
                  {user.active ? 'Active & Verified' : 'Disabled'}
                </div>
              </div>

              <div className="session-stat-card">
                <div className="session-stat-label">Member Since</div>
                <div className="session-stat-val" style={{ fontSize: '0.85rem' }}>
                  {user.createdAt ? new Date(user.createdAt).toLocaleDateString() : 'Active'}
                </div>
              </div>
            </div>

            {/* Quick Actions Shortcuts */}
            <div style={{ borderTop: '1px solid var(--border-subtle)', paddingTop: '1.25rem' }}>
              <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '0.75rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                Quick Navigation
              </div>
              <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
                <button
                  type="button"
                  onClick={() => scrollTo('catalog-section')}
                  className="btn-secondary"
                  style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.85rem', padding: '0.55rem 1rem' }}
                >
                  <Package size={15} style={{ color: 'var(--accent-cyan)' }} />
                  <span>Browse Catalog</span>
                </button>

                {user.role === 'ADMIN' && (
                  <button
                    type="button"
                    onClick={() => scrollTo('inventory-section')}
                    className="btn-secondary"
                    style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.85rem', padding: '0.55rem 1rem' }}
                  >
                    <Boxes size={15} style={{ color: 'var(--accent-amber)' }} />
                    <span>Manage Inventory &amp; Stock</span>
                  </button>
                )}
              </div>
            </div>
          </div>
        ) : (
          /* ====================================================================
             SIGN IN / REGISTER FORM VIEW
             ==================================================================== */
          <div>
            <div className="auth-tabs">
              <button
                type="button"
                className={`auth-tab ${activeTab === 'LOGIN' ? 'active' : ''}`}
                onClick={() => {
                  setActiveTab('LOGIN');
                  setLoginError(null);
                  setRegisterError(null);
                }}
              >
                <LogIn size={17} />
                <span>Sign In</span>
              </button>
              <button
                type="button"
                className={`auth-tab ${activeTab === 'REGISTER' ? 'active' : ''}`}
                onClick={() => {
                  setActiveTab('REGISTER');
                  setLoginError(null);
                  setRegisterError(null);
                }}
              >
                <UserPlus size={17} />
                <span>Create Account</span>
              </button>
            </div>

            <div className="auth-body">
              {activeTab === 'LOGIN' ? (
                /* LOGIN FORM */
                <form onSubmit={handleLogin} className="auth-form" noValidate>
                  {/* Demo Credentials Quick-Fill Bar */}
                  <div style={{
                    marginBottom: '1.25rem',
                    padding: '0.75rem',
                    borderRadius: 'var(--radius-md)',
                    background: 'rgba(255, 255, 255, 0.03)',
                    border: '1px solid var(--border-subtle)',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '0.5rem'
                  }}>
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 500, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                      ⚡ Quick Fill Demo Account
                    </span>
                    <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                      <button
                        type="button"
                        id="quick-fill-customer"
                        onClick={() => {
                          setLoginEmail('customer@stockguard.io');
                          setLoginPassword('Customer123!');
                          setLoginError(null);
                        }}
                        style={{
                          fontSize: '0.78rem',
                          padding: '0.35rem 0.65rem',
                          borderRadius: 'var(--radius-sm)',
                          background: 'rgba(16, 185, 129, 0.12)',
                          border: '1px solid rgba(16, 185, 129, 0.35)',
                          color: 'var(--accent-emerald)',
                          cursor: 'pointer',
                          fontWeight: 500,
                          transition: 'all 0.15s ease'
                        }}
                      >
                        👤 Customer Demo (customer@stockguard.io)
                      </button>
                    </div>
                  </div>

                  {loginError && (
                    <div className="alert-box error">
                      <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
                      <div>{loginError}</div>
                    </div>
                  )}

                  <div className="form-group">
                    <label className="form-label" htmlFor="login-email">
                      <span>Email Address</span>
                    </label>
                    <div className="input-wrapper">
                      <Mail size={16} className="input-icon" />
                      <input
                        id="login-email"
                        type="email"
                        className="form-input"
                        placeholder="you@example.com"
                        value={loginEmail}
                        onChange={(e) => setLoginEmail(e.target.value)}
                        autoComplete="email"
                        required
                      />
                    </div>
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor="login-password">
                      <span>Password</span>
                    </label>
                    <div className="input-wrapper">
                      <Lock size={16} className="input-icon" />
                      <input
                        id="login-password"
                        type={showLoginPassword ? 'text' : 'password'}
                        className="form-input"
                        placeholder="Enter your password"
                        value={loginPassword}
                        onChange={(e) => setLoginPassword(e.target.value)}
                        autoComplete="current-password"
                        required
                      />
                      <button
                        type="button"
                        className="password-toggle-btn"
                        onClick={() => setShowLoginPassword(!showLoginPassword)}
                        title={showLoginPassword ? 'Hide password' : 'Show password'}
                      >
                        {showLoginPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                      </button>
                    </div>
                  </div>

                  <button
                    type="submit"
                    className="btn-primary"
                    disabled={loginSubmitting || isLoading}
                    style={{ width: '100%', padding: '0.75rem', marginTop: '0.5rem', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem' }}
                  >
                    {loginSubmitting ? (
                      <>
                        <span className="pulse-dot" style={{ background: '#ffffff' }} />
                        <span>Verifying Credentials...</span>
                      </>
                    ) : (
                      <>
                        <LogIn size={18} />
                        <span>Sign In to StockGuard</span>
                      </>
                    )}
                  </button>

                  <p style={{ marginTop: '1.25rem', fontSize: '0.8rem', color: 'var(--text-muted)', textAlign: 'center' }}>
                    Authenticates against <code>POST /api/v1/auth/login</code> and receives a signed Bearer JWT.
                  </p>
                </form>
              ) : (
                /* REGISTRATION FORM */
                <form onSubmit={handleRegister} className="auth-form" noValidate>
                  {registerError && (
                    <div className="alert-box error">
                      <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
                      <div>{registerError}</div>
                    </div>
                  )}

                  {registerSuccessMessage && (
                    <div className="alert-box success">
                      <CheckCircle2 size={18} style={{ flexShrink: 0, marginTop: '2px' }} />
                      <div>{registerSuccessMessage}</div>
                    </div>
                  )}

                  <div className="form-group">
                    <label className="form-label" htmlFor="register-name">
                      <span>Full Name</span>
                    </label>
                    <div className="input-wrapper">
                      <UserIcon size={16} className="input-icon" />
                      <input
                        id="register-name"
                        type="text"
                        className="form-input"
                        placeholder="Jane Doe"
                        value={registerName}
                        onChange={(e) => setRegisterName(e.target.value)}
                        autoComplete="name"
                        required
                      />
                    </div>
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor="register-email">
                      <span>Email Address</span>
                    </label>
                    <div className="input-wrapper">
                      <Mail size={16} className="input-icon" />
                      <input
                        id="register-email"
                        type="email"
                        className="form-input"
                        placeholder="jane.doe@example.com"
                        value={registerEmail}
                        onChange={(e) => setRegisterEmail(e.target.value)}
                        autoComplete="email"
                        required
                      />
                    </div>
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor="register-password">
                      <span>Password</span>
                      <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'none' }}>
                        Min 8 chars
                      </span>
                    </label>
                    <div className="input-wrapper">
                      <Lock size={16} className="input-icon" />
                      <input
                        id="register-password"
                        type={showRegisterPassword ? 'text' : 'password'}
                        className="form-input"
                        placeholder="Create a strong password (min 8 chars)"
                        value={registerPassword}
                        onChange={(e) => setRegisterPassword(e.target.value)}
                        autoComplete="new-password"
                        required
                      />
                      <button
                        type="button"
                        className="password-toggle-btn"
                        onClick={() => setShowRegisterPassword(!showRegisterPassword)}
                        title={showRegisterPassword ? 'Hide password' : 'Show password'}
                      >
                        {showRegisterPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                      </button>
                    </div>
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor="register-confirm-password">
                      <span>Confirm Password</span>
                    </label>
                    <div className="input-wrapper">
                      <Lock size={16} className="input-icon" />
                      <input
                        id="register-confirm-password"
                        type={showRegisterPassword ? 'text' : 'password'}
                        className="form-input"
                        placeholder="Re-enter password"
                        value={registerConfirmPassword}
                        onChange={(e) => setRegisterConfirmPassword(e.target.value)}
                        autoComplete="new-password"
                        required
                      />
                    </div>
                  </div>

                  <div style={{ margin: '0.75rem 0 1.25rem', padding: '0.75rem', background: 'rgba(56, 189, 248, 0.05)', border: '1px solid rgba(56, 189, 248, 0.2)', borderRadius: 'var(--radius-sm)', fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                    🔒 <strong>Security Policy:</strong> Passwords are hashed with BCrypt. Public registrations automatically receive the <code>CUSTOMER</code> role with zero privilege elevation risk.
                  </div>

                  <button
                    type="submit"
                    className="btn-primary"
                    disabled={registerSubmitting || isLoading}
                    style={{ width: '100%', padding: '0.75rem', marginTop: '0.5rem', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem' }}
                  >
                    {registerSubmitting ? (
                      <>
                        <span className="pulse-dot" style={{ background: '#ffffff' }} />
                        <span>Creating Account...</span>
                      </>
                    ) : (
                      <>
                        <UserPlus size={18} />
                        <span>Create Customer Account</span>
                      </>
                    )}
                  </button>

                  <p style={{ marginTop: '1.25rem', fontSize: '0.8rem', color: 'var(--text-muted)', textAlign: 'center' }}>
                    Registers via <code>POST /api/v1/auth/register</code> and signs in immediately.
                  </p>
                </form>
              )}
            </div>
          </div>
        )}
      </div>
    </section>
  );
};
