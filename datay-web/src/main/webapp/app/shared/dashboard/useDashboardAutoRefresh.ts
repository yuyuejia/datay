import { onBeforeUnmount, watch } from 'vue';
import type { Ref } from 'vue';

/**
 * 看板自动刷新：按秒数递归调度，上一次请求完成后才安排下一次。
 *
 * <p>页面隐藏时跳过本次刷新；间隔改为 0 或组件卸载时清理定时器。
 */
export function useDashboardAutoRefresh(intervalSeconds: Ref<number>, refresh: () => Promise<void>) {
  let timer: ReturnType<typeof setTimeout> | undefined;
  let stopped = false;

  const clear = () => {
    if (timer !== undefined) {
      clearTimeout(timer);
      timer = undefined;
    }
  };

  const schedule = () => {
    clear();
    const seconds = Math.max(0, Math.floor(intervalSeconds.value || 0));
    if (stopped || seconds <= 0) {
      return;
    }
    timer = setTimeout(async () => {
      if (stopped) {
        return;
      }
      try {
        if (typeof document === 'undefined' || !document.hidden) {
          await refresh();
        }
      } finally {
        schedule();
      }
    }, seconds * 1000);
  };

  watch(intervalSeconds, schedule, { immediate: true });
  onBeforeUnmount(() => {
    stopped = true;
    clear();
  });

  return { schedule, clear };
}
