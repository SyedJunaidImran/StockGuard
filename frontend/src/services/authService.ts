import { apiClient } from './api';
import { ApiResponse, AuthResponse, LoginPayload, RegisterPayload, User } from '../types/auth';

const TOKEN_KEY = 'stockguard_token';
const USER_KEY = 'stockguard_user';

export const authService = {
  /**
   * Submits credentials to POST /api/v1/auth/login
   */
  async login(payload: LoginPayload): Promise<AuthResponse> {
    const res = await apiClient<ApiResponse<AuthResponse>>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
    this.saveSession(res.data);
    return res.data;
  },

  /**
   * Submits registration to POST /api/v1/auth/register
   */
  async register(payload: RegisterPayload): Promise<AuthResponse> {
    const res = await apiClient<ApiResponse<AuthResponse>>('/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
    this.saveSession(res.data);
    return res.data;
  },

  /**
   * Fetches current authenticated profile from GET /api/v1/auth/me
   */
  async getMe(): Promise<User> {
    const res = await apiClient<ApiResponse<User>>('/auth/me', {
      method: 'GET',
    });
    localStorage.setItem(USER_KEY, JSON.stringify(res.data));
    return res.data;
  },

  /**
   * Tests CUSTOMER role-guarded endpoint GET /api/v1/test/customer-only
   */
  async testCustomerResource(): Promise<Record<string, unknown>> {
    const res = await apiClient<ApiResponse<Record<string, unknown>>>('/test/customer-only', {
      method: 'GET',
    });
    return res.data;
  },

  /**
   * Tests ADMIN role-guarded endpoint GET /api/v1/test/admin-only
   */
  async testAdminResource(): Promise<Record<string, unknown>> {
    const res = await apiClient<ApiResponse<Record<string, unknown>>>('/test/admin-only', {
      method: 'GET',
    });
    return res.data;
  },

  /**
   * Tests authenticated endpoint GET /api/v1/test/authenticated
   */
  async testAuthenticatedResource(): Promise<Record<string, unknown>> {
    const res = await apiClient<ApiResponse<Record<string, unknown>>>('/test/authenticated', {
      method: 'GET',
    });
    return res.data;
  },

  saveSession(auth: AuthResponse): void {
    if (auth.token) {
      localStorage.setItem(TOKEN_KEY, auth.token);
    }
    if (auth.user) {
      localStorage.setItem(USER_KEY, JSON.stringify(auth.user));
    }
  },

  getSessionToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  },

  getSessionUser(): User | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as User;
    } catch {
      return null;
    }
  },

  clearSession(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  },
};
