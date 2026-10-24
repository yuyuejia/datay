import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, h, nextTick, ref } from 'vue';
import type { Ref } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useDashboardAutoRefresh } from './useDashboardAutoRefresh';

function mountAutoRefresh(intervalSeconds: Ref<number>, refresh: () => Promise<void>) {
  const Comp = defineComponent({
    setup() {
      useDashboardAutoRefresh(intervalSeconds, refresh);
      return () => h('div');
    },
  });
  return mount(Comp);
}

describe('useDashboardAutoRefresh', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('refreshes on the configured interval and stops when disabled', async () => {
    const interval = ref(10);
    const refresh = vi.fn().mockResolvedValue(undefined);
    const wrapper = mountAutoRefresh(interval, refresh);

    vi.advanceTimersByTime(10_000);
    await flushPromises();
    expect(refresh).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(10_000);
    await flushPromises();
    expect(refresh).toHaveBeenCalledTimes(2);

    interval.value = 0;
    await nextTick();
    vi.advanceTimersByTime(60_000);
    await flushPromises();
    expect(refresh).toHaveBeenCalledTimes(2);

    wrapper.unmount();
  });

  it('clears the pending timer on unmount', async () => {
    const interval = ref(30);
    const refresh = vi.fn().mockResolvedValue(undefined);
    const wrapper = mountAutoRefresh(interval, refresh);

    wrapper.unmount();
    vi.advanceTimersByTime(60_000);
    await flushPromises();
    expect(refresh).not.toHaveBeenCalled();
  });

  it('skips a tick while the document is hidden', async () => {
    const interval = ref(10);
    const refresh = vi.fn().mockResolvedValue(undefined);
    const wrapper = mountAutoRefresh(interval, refresh);
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(true);

    vi.advanceTimersByTime(10_000);
    await flushPromises();
    expect(refresh).not.toHaveBeenCalled();

    wrapper.unmount();
  });
});
