import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { setAuthToken } from '../lib/authToken';
import type { User } from '../types';

interface AuthContextValue {
  user: User | null;
  token: string | null;
  isLoading: boolean;
  login: (token: string, user: User) => void;
  logout: () => void;
  updateUser: (user: User) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const STORAGE_KEY = 'booki-auth';

interface StoredAuth {
  token: string;
  user: User;
}

/** Guards against a hand-edited / stale localStorage entry of the wrong shape. */
function parseStoredAuth(raw: string): StoredAuth | null {
  try {
    const value: unknown = JSON.parse(raw);
    if (
      value &&
      typeof value === 'object' &&
      typeof (value as StoredAuth).token === 'string' &&
      (value as StoredAuth).token.length > 0 &&
      typeof (value as StoredAuth).user === 'object' &&
      (value as StoredAuth).user !== null &&
      typeof (value as StoredAuth).user.id === 'number' &&
      typeof (value as StoredAuth).user.email === 'string'
    ) {
      return value as StoredAuth;
    }
  } catch {
    // fall through
  }
  return null;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const raw = localStorage.getItem(STORAGE_KEY);
    const parsed = raw ? parseStoredAuth(raw) : null;
    if (parsed) {
      setToken(parsed.token);
      setAuthToken(parsed.token);
      setUser(parsed.user);
    } else if (raw) {
      localStorage.removeItem(STORAGE_KEY);
    }
    setIsLoading(false);
  }, []);

  const login = (newToken: string, newUser: User) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ token: newToken, user: newUser }));
    setAuthToken(newToken);
    setToken(newToken);
    setUser(newUser);
  };

  const logout = () => {
    localStorage.removeItem(STORAGE_KEY);
    setAuthToken(null);
    setToken(null);
    setUser(null);
  };

  const updateUser = (updatedUser: User) => {
    setUser(updatedUser);
    if (token) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ token, user: updatedUser }));
    }
  };

  return (
    <AuthContext.Provider value={{ user, token, isLoading, login, logout, updateUser }}>
      {children}
    </AuthContext.Provider>
  );
}

// Co-located with the provider on purpose (single import site for auth). The
// react-refresh rule only cares about HMR granularity, which doesn't matter for
// this rarely-touched file.
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}

export { STORAGE_KEY };
