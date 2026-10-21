import { computed, defineComponent, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import dayjs from 'dayjs';

import { useStore } from '@/store';
import AlertCenterService from '@/entities/alert-center/alert-center.service';
import type { INotification } from '@/shared/model/alert.model';

export default defineComponent({
  name: 'NotificationBell',
  setup() {
    const router = useRouter();
    const store = useStore();
    const service = new AlertCenterService();

    const unread = ref(0);
    const notifications = ref<INotification[]>([]);
    const popoverVisible = ref(false);
    const loading = ref(false);
    let timer: ReturnType<typeof setInterval> | null = null;

    const authenticated = computed(() => store.authenticated);

    const loadUnread = async () => {
      if (!authenticated.value) {
        unread.value = 0;
        return;
      }
      try {
        unread.value = await service.unreadCount();
      } catch {
        // 导航栏不打断主流程
      }
    };

    const loadNotifications = async () => {
      if (!authenticated.value) {
        return;
      }
      loading.value = true;
      try {
        const res = await service.retrieveNotifications({ page: 0, size: 10, sort: ['createTime,desc'] });
        notifications.value = res.data ?? [];
        await loadUnread();
      } catch {
        // 静默失败，铃铛仍可正常显示
      } finally {
        loading.value = false;
      }
    };

    const markRead = async (notification: INotification) => {
      if (!notification.id) {
        return;
      }
      try {
        if (!notification.readFlag) {
          await service.markNotificationRead(notification.id);
          notification.readFlag = true;
          unread.value = Math.max(0, unread.value - 1);
        }
        popoverVisible.value = false;
        router.push('/alert-center');
      } catch {
        // 忽略单条已读失败
      }
    };

    const markAllRead = async () => {
      try {
        await service.markAllNotificationsRead();
        notifications.value.forEach(item => (item.readFlag = true));
        unread.value = 0;
      } catch {
        // 忽略
      }
    };

    const goToAlertCenter = () => {
      popoverVisible.value = false;
      router.push('/alert-center');
    };

    const formatTime = (value?: Date | string): string => {
      return value ? dayjs(value).format('MM-DD HH:mm') : '';
    };

    watch(authenticated, value => {
      if (value) {
        loadUnread();
      } else {
        unread.value = 0;
        notifications.value = [];
      }
    });

    // 切换租户后立即刷新未读数，避免铃铛继续显示上一个租户的数据。
    watch(
      () => store.tenantVersion,
      () => {
        notifications.value = [];
        popoverVisible.value = false;
        loadUnread();
      },
    );

    onMounted(() => {
      loadUnread();
      timer = setInterval(loadUnread, 60000);
    });

    onUnmounted(() => {
      if (timer) {
        clearInterval(timer);
      }
    });

    return {
      authenticated,
      unread,
      notifications,
      popoverVisible,
      loading,
      loadNotifications,
      markRead,
      markAllRead,
      goToAlertCenter,
      formatTime,
    };
  },
});
