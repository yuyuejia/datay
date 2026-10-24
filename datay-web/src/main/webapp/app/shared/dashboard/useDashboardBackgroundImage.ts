import { onBeforeUnmount, ref, watch } from 'vue';
import type { Ref } from 'vue';

import DashboardBackgroundService from './dashboard-background.service';

/**
 * 通过带 JWT 的 axios 请求加载背景图，并生成可在 CSS 中使用的 blob URL。
 * 路径变化或组件卸载时释放旧的 object URL。
 */
export function useDashboardBackgroundImage(path: Ref<string | undefined>) {
  const service = new DashboardBackgroundService();
  const imageUrl = ref('');
  let objectUrl = '';
  let version = 0;

  const revoke = () => {
    if (objectUrl) {
      URL.revokeObjectURL(objectUrl);
      objectUrl = '';
    }
  };

  watch(
    path,
    async value => {
      const current = ++version;
      revoke();
      imageUrl.value = '';
      if (!value) {
        return;
      }
      try {
        const loaded = await service.loadImageUrl(value);
        if (current !== version) {
          URL.revokeObjectURL(loaded);
          return;
        }
        objectUrl = loaded;
        imageUrl.value = loaded;
      } catch {
        // 图片不存在或无权限时回退到主题默认背景
        imageUrl.value = '';
      }
    },
    { immediate: true },
  );

  onBeforeUnmount(() => {
    version += 1;
    revoke();
  });

  return { imageUrl };
}
