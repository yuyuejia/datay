import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, h, ref } from 'vue';
import type { Ref } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useDashboardBackgroundImage } from './useDashboardBackgroundImage';

const { loadImageUrl } = vi.hoisted(() => ({
  loadImageUrl: vi.fn(),
}));

vi.mock('./dashboard-background.service', () => ({
  default: class {
    loadImageUrl = loadImageUrl;
  },
}));

function mountBackgroundImage(path: Ref<string>) {
  const Comp = defineComponent({
    setup() {
      const { imageUrl } = useDashboardBackgroundImage(path);
      return () => h('div', { class: 'background-url' }, imageUrl.value);
    },
  });
  return mount(Comp);
}

describe('useDashboardBackgroundImage', () => {
  beforeEach(() => {
    loadImageUrl.mockReset();
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('loads a blob URL for the current background path', async () => {
    loadImageUrl.mockResolvedValue('blob:background');
    const path = ref('dashboard-backgrounds/a/bg.png');
    const wrapper = mountBackgroundImage(path);

    await flushPromises();

    expect(loadImageUrl).toHaveBeenCalledWith('dashboard-backgrounds/a/bg.png');
    expect(wrapper.find('.background-url').text()).toBe('blob:background');
    wrapper.unmount();
  });

  it('clears and revokes the old URL when the path is removed', async () => {
    loadImageUrl.mockResolvedValue('blob:background');
    const path = ref('dashboard-backgrounds/a/bg.png');
    const wrapper = mountBackgroundImage(path);
    await flushPromises();

    path.value = '';
    await flushPromises();

    expect(wrapper.find('.background-url').text()).toBe('');
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:background');
    wrapper.unmount();
  });

  it('falls back silently when loading fails', async () => {
    loadImageUrl.mockRejectedValue(new Error('not found'));
    const path = ref('dashboard-backgrounds/a/missing.png');
    const wrapper = mountBackgroundImage(path);

    await flushPromises();

    expect(wrapper.find('.background-url').text()).toBe('');
    wrapper.unmount();
  });
});
