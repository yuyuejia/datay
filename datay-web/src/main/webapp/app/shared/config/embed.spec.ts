import { beforeEach, describe, expect, it } from 'vitest';

import { isEmbedMode } from './embed';

const setUrl = (url: string) => window.history.replaceState({}, '', url);

describe('embed mode', () => {
  beforeEach(() => {
    sessionStorage.clear();
    setUrl('/dashboard.html?id=db1');
  });

  it('enables embed mode with embed=1', () => {
    setUrl('/dashboard.html?id=db1&embed=1');

    expect(isEmbedMode()).toBe(true);
    expect(sessionStorage.getItem('jhi-embed-mode')).toBe('1');
  });

  it('supports embed=1 in the hash', () => {
    setUrl('/dashboard.html#embed=1');

    expect(isEmbedMode()).toBe(true);
  });

  it('stays in embed mode after the URL parameter disappears', () => {
    setUrl('/dashboard.html?embed=1');
    expect(isEmbedMode()).toBe(true);

    setUrl('/dashboard.html');
    expect(isEmbedMode()).toBe(true);
  });

  it('exits embed mode with embed=0', () => {
    setUrl('/dashboard.html?embed=1');
    expect(isEmbedMode()).toBe(true);

    setUrl('/dashboard.html?embed=0');
    expect(isEmbedMode()).toBe(false);
    expect(sessionStorage.getItem('jhi-embed-mode')).toBeNull();
  });

  it('defaults to normal mode', () => {
    expect(isEmbedMode()).toBe(false);
  });
});
