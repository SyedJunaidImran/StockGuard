import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { User, LoginPayload, RegisterPayload } from '../types/auth';
import { authService } from '../services/authService';

interface AuthContextType {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (payload: LoginPayload) => Promise<void>;
  register: (payload: RegisterPayload) => Promise<void>;
  logout: () => void;
  refreshProfile: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [token, setToken] = useState<string | null>(() => authService.getSessionToken());
  const [user, setUser] = useState<User | null>(() => authService.getSessionUser());
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const logout = useCallback(() => {
    authService.clearSession();
    setToken(null);
    setUser(null);
  }, []);

  const refreshProfile = useCallback(async () => {
    if (!authService.getSessionToken()) {
      setIsLoading(false);
      return;
    }
    try {
      const fetchedUser = await authService.getMe();
      setUser(fetchedUser);
    } catch {
      // Token expired or invalid; clear bad state
      logout();
    } finally {
      setIsLoading(false);
    }
  }, [logout]);

  useEffect(() => {
    refreshProfile();
  }, [refreshProfile]);

  const login = async (payload: LoginPayload) => {
    const authData = await authService.login(payload);
    setToken(authData.token);
    setUser(authData.user);
  };

  const register = async (payload: RegisterPayload) => {
    const authData = await authService.register(payload);
    setToken(authData.token);
    setUser(authData.user);
  };

  const value: AuthContextType = {
    user,
    token,
    isAuthenticated: !!token && !!user,
    isLoading,
    login,
    register,
    logout,
    refreshProfile,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
