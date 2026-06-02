import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { UserInfo } from '../types';
import { createLocalStorageMock } from '../test/localStorage';

const adminUser = {
  id: 1,
  username: 'admin',
  role: 'ADMIN',
} satisfies UserInfo;

describe('auth store', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', createLocalStorageMock());
    vi.resetModules();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('stores auth data in Vue-compatible localStorage keys', async () => {
    const { useAuthStore } = await import('./auth');

    useAuthStore.getState().setAuth('jwt-token', adminUser);

    expect(localStorage.getItem('token')).toBe('jwt-token');
    expect(localStorage.getItem('user')).toBe(JSON.stringify(adminUser));
    expect(useAuthStore.getState().token).toBe('jwt-token');
    expect(useAuthStore.getState().user).toEqual(adminUser);
  });

  it('hydrates auth data from existing localStorage keys', async () => {
    localStorage.setItem('token', 'existing-token');
    localStorage.setItem('user', JSON.stringify(adminUser));

    const { useAuthStore } = await import('./auth');

    expect(useAuthStore.getState().token).toBe('existing-token');
    expect(useAuthStore.getState().user).toEqual(adminUser);
  });

  it('clears auth keys without touching unrelated localStorage entries', async () => {
    localStorage.setItem('unrelated', 'keep-me');
    const { useAuthStore } = await import('./auth');

    useAuthStore.getState().setAuth('jwt-token', adminUser);
    useAuthStore.getState().logout();

    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
    expect(localStorage.getItem('unrelated')).toBe('keep-me');
    expect(useAuthStore.getState().token).toBeNull();
    expect(useAuthStore.getState().user).toBeNull();
  });

  it('ignores malformed stored user JSON', async () => {
    localStorage.setItem('token', 'token-with-bad-user-json');
    localStorage.setItem('user', '{not-json');

    const { useAuthStore } = await import('./auth');

    expect(useAuthStore.getState().token).toBe('token-with-bad-user-json');
    expect(useAuthStore.getState().user).toBeNull();
  });
});
