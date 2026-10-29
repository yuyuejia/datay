import { beforeEach, describe, expect, it } from 'vitest';

import { TOKEN_STORAGE_KEY } from '@/shared/config/axios-interceptor';
import { consumeUrlToken, isTokenExpired, removeUrlToken } from './url-token';

const base64Url = (value: string): string => {
  const bytes = new TextEncoder().encode(value);
  const binary = Array.from(bytes, byte => String.fromCharCode(byte)).join('');
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
};

const buildJwt = (payload: Record<string, unknown>): string =>
  `${base64Url(JSON.stringify({ alg: 'HS512' }))}.${base64Url(JSON.stringify(payload))}.signature`;

const validToken = () => buildJwt({ sub: 'demo', exp: Math.floor(Date.now() / 1000) + 3600 });

const setUrl = (url: string) => window.history.replaceState({}, '', url);

describe('url token', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    setUrl('/dashboard.html?id=db1');
  });

  it('reads query token, stores it in sessionStorage and removes it from the URL', () => {
    const token = validToken();
    setUrl(`/dashboard.html?id=db1&embed=1&token=${token}`);

    const result = consumeUrlToken();

    expect(result).toEqual({ applied: true, expired: false });
    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBe(token);
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull();
    expect(window.location.search).toBe('?id=db1&embed=1');
    expect(window.location.hash).toBe('');
  });

  it('reads hash token and clears the hash', () => {
    const token = validToken();
    setUrl(`/dashboard.html?id=db1#token=${token}`);

    const result = consumeUrlToken();

    expect(result.applied).toBe(true);
    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBe(token);
    expect(window.location.search).toBe('?id=db1');
    expect(window.location.hash).toBe('');
  });

  it('prefers hash token over query token', () => {
    const queryToken = validToken();
    const hashToken = buildJwt({ sub: 'hash-user', exp: Math.floor(Date.now() / 1000) + 3600 });
    setUrl(`/dashboard.html?token=${queryToken}#token=${hashToken}`);

    consumeUrlToken();

    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBe(hashToken);
  });

  it('overrides a stale localStorage token with the URL token', () => {
    const token = validToken();
    localStorage.setItem(TOKEN_STORAGE_KEY, 'stale-token');
    setUrl(`/dashboard.html#token=${token}`);

    consumeUrlToken();

    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBe(token);
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull();
  });

  it('decodes UTF-8 JWT payloads', () => {
    const token = buildJwt({ sub: '张三', tenantCode: '华东区', exp: Math.floor(Date.now() / 1000) + 3600 });
    setUrl(`/dashboard.html#token=${token}`);

    expect(consumeUrlToken()).toEqual({ applied: true, expired: false });
    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBe(token);
  });

  it('ignores an expired token but still removes it from the URL', () => {
    const expired = buildJwt({ sub: 'demo', exp: 1 });
    setUrl(`/dashboard.html?id=db1&token=${expired}`);

    const result = consumeUrlToken();

    expect(result).toEqual({ applied: false, expired: true });
    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull();
    expect(window.location.search).toBe('?id=db1');
  });

  it('treats a malformed token as expired and removes it', () => {
    setUrl('/dashboard.html?token=not-a-jwt');

    const result = consumeUrlToken();

    expect(result).toEqual({ applied: false, expired: true });
    expect(sessionStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull();
    expect(window.location.search).toBe('');
  });

  it('does nothing when no token is present', () => {
    setUrl('/dashboard.html?id=db1#foo=bar');

    const result = consumeUrlToken();

    expect(result).toEqual({ applied: false, expired: false });
    expect(window.location.search).toBe('?id=db1');
    expect(window.location.hash).toBe('#foo=bar');
  });

  it('removes only the token while preserving other hash parameters', () => {
    setUrl('/dashboard.html#token=abc&foo=bar');

    removeUrlToken();

    expect(window.location.hash).toBe('#foo=bar');
  });

  it('detects token expiration', () => {
    const now = Date.now();
    expect(isTokenExpired(buildJwt({ exp: Math.floor(now / 1000) - 1 }), now)).toBe(true);
    expect(isTokenExpired(buildJwt({ exp: Math.floor(now / 1000) + 60 }), now)).toBe(false);
    expect(isTokenExpired(buildJwt({ sub: 'no-exp' }), now)).toBe(false);
    expect(isTokenExpired('not-a-jwt', now)).toBe(true);
  });
});
