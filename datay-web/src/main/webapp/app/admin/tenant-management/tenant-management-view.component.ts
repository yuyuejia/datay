import { type Ref, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import TenantManagementService from './tenant-management.service';
import { type ITenant } from '@/shared/model/tenant.model';
import { useAlertService } from '@/shared/alert/alert.service';
import { useDateFormat } from '@/shared/composables';

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: 'JhiTenantManagementView',
  setup() {
    const route = useRoute();
    const router = useRouter();

    const alertService = inject('alertService', () => useAlertService(), true);
    const { formatDateShort: formatDate } = useDateFormat();
    const tenantManagementService = inject('tenantManagementService', () => new TenantManagementService(), true);
    const previousState = () => router.go(-1);

    const tenant: Ref<ITenant | null> = ref(null);
    const users: Ref<any[]> = ref([]);
    const addUserModal: Ref<boolean> = ref(false);
    const searchQuery: Ref<string> = ref('');
    const searchResults: Ref<any[]> = ref([]);
    const searched: Ref<boolean> = ref(false);

    let searchTimer: any = null;

    const loadTenant = async (tenantId: number) => {
      const response = await tenantManagementService.get(tenantId);
      tenant.value = response.data;
    };

    const loadUsers = async (tenantId: number) => {
      const response = await tenantManagementService.getTenantUsers(tenantId);
      users.value = response.data;
    };

    const tenantId = Number(route.params?.tenantId);

    if (tenantId) {
      loadTenant(tenantId);
      loadUsers(tenantId);
    }

    const debouncedSearch = () => {
      if (searchTimer) {
        clearTimeout(searchTimer);
      }
      searchTimer = setTimeout(() => {
        doSearch();
      }, 300);
    };

    const doSearch = async () => {
      if (!searchQuery.value.trim()) {
        searchResults.value = [];
        searched.value = false;
        return;
      }
      searched.value = true;
      const response = await tenantManagementService.searchUsers(searchQuery.value.trim());
      searchResults.value = response.data;
    };

    const isUserInTenant = (userId: number): boolean => {
      return users.value.some(u => u.id === userId);
    };

    const showAddUserModal = () => {
      searchQuery.value = '';
      searchResults.value = [];
      searched.value = false;
      addUserModal.value = true;
    };

    const addUser = async (user: any) => {
      try {
        await tenantManagementService.addUser(tenantId, user.id);
        alertService.showSuccess(`用户 ${user.login} 已添加到租户`);
        loadUsers(tenantId);
        doSearch();
      } catch (error: any) {
        alertService.showHttpError(error.response);
      }
    };

    const removeUser = async (user: any) => {
      try {
        await tenantManagementService.removeUser(tenantId, user.id);
        alertService.showInfo(`用户 ${user.login} 已从租户移除`);
        loadUsers(tenantId);
      } catch (error: any) {
        alertService.showHttpError(error.response);
      }
    };

    return {
      formatDate,
      tenant,
      users,
      addUserModal,
      searchQuery,
      searchResults,
      searched,
      previousState,
      alertService,
      showAddUserModal,
      debouncedSearch,
      isUserInTenant,
      addUser,
      removeUser,
    };
  },
});