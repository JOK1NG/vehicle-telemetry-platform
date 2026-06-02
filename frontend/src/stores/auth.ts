import { create } from 'zustand';
import type { UserInfo } from '../types';

const TOKEN_KEY = 'token';
const USER_KEY = 'user';

function readToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

function readUser(): UserInfo | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as UserInfo;
  } catch {
    return null;
  }
}

interface AuthState {
  token: string | null;
  user: UserInfo | null;
  setAuth: (token: string, user: UserInfo) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: readToken(),
  user: readUser(),
  setAuth: (token, user) => {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    set({ token, user });
  },
  logout: () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    set({ token: null, user: null });
  },
}));

export const useAuth = () => {
  const token = useAuthStore((s) => s.token);
  const user = useAuthStore((s) => s.user);
  return {
    token,
    user,
    isLoggedIn: !!token,
    isAdmin: user?.role === 'ADMIN',
    username: user?.username ?? '',
    role: user?.role ?? '',
  };
};
