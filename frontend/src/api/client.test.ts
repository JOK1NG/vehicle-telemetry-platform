import type { AxiosAdapter, AxiosResponse } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { UserInfo } from '../types';
import { createLocalStorageMock } from '../test/localStorage';

const adminUser = {
  id: 1,
  username: 'admin',
  role: 'ADMIN',
} satisfies UserInfo;

function adapterReturning(data: unknown, inspect?: (config: AxiosResponse['config']) => void): AxiosAdapter {
  return async (config) => {
    inspect?.(config);
    return {
      data,
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    };
  };
}

describe('api client', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', createLocalStorageMock());
    vi.resetModules();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('adds the persisted auth token as a Bearer header', async () => {
    const { useAuthStore } = await import('../stores/auth');
    const request = (await import('./client')).default;
    let authorization: unknown;

    useAuthStore.getState().setAuth('jwt-token', adminUser);
    request.defaults.adapter = adapterReturning({ code: 0, message: 'ok', data: { ok: true } }, (config) => {
      authorization = config.headers.Authorization;
    });

    await request.get('/api/test');

    expect(authorization).toBe('Bearer jwt-token');
  });

  it('passes successful API envelopes through for callers to unwrap data', async () => {
    const request = (await import('./client')).default;
    const envelope = { code: 0, message: 'ok', data: { id: 1 } };

    request.defaults.adapter = adapterReturning(envelope);

    await expect(request.get('/api/test')).resolves.toEqual(envelope);
  });

  it('rejects non-zero API envelopes with the backend message', async () => {
    const request = (await import('./client')).default;
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    request.defaults.adapter = adapterReturning({ code: 401, message: '用户名或密码错误' });

    await expect(request.get('/api/test')).rejects.toThrow('用户名或密码错误');
    expect(consoleError).toHaveBeenCalledWith('[API]', '用户名或密码错误');
  });
});
