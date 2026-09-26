import { type Ref, defineComponent, inject, ref } from 'vue';
import TenantManagementService from './tenant-management.service';
import { useAlertService } from '@/shared/alert/alert.service';
import { useDateFormat } from '@/shared/composables';

export default defineComponent({
  name: 'JhiTenantManagementComponent',
  mounted(): void {
    this.loadAll();
  },
  setup() {
    const alertService = inject('alertService', () => useAlertService(), true);
    const { formatDateShort: formatDate } = useDateFormat();
    const tenantManagementService = inject('tenantManagementService', () => new TenantManagementService(), true);

    const propOrder = ref('id');
    const reverse = ref(false);
    const isLoading = ref(false);
    const removeId: Ref<number> = ref(null);
    const tenants: Ref<any[]> = ref([]);

    return {
      formatDate,
      tenantManagementService,
      alertService,
      propOrder,
      reverse,
      isLoading,
      removeId,
      tenants,
    };
  },
  methods: {
    loadAll(): void {
      this.isLoading = true;
      this.tenantManagementService
        .retrieve()
        .then(res => {
          this.isLoading = false;
          this.tenants = res.data;
        })
        .catch(() => {
          this.isLoading = false;
        });
    },
    handleSyncList(): void {
      this.loadAll();
    },
    changeOrder(propOrder: string): void {
      this.propOrder = propOrder;
      this.reverse = !this.reverse;
    },
    deleteTenant(): void {
      this.tenantManagementService
        .remove(this.removeId)
        .then(res => {
          this.alertService.showInfo(res.headers['x-datafusionapp-alert'].toString(), { variant: 'danger' });
          this.removeId = null;
          this.loadAll();
          this.closeDialog();
        })
        .catch(error => {
          this.alertService.showHttpError(error.response);
        });
    },
    prepareRemove(instance): void {
      this.removeId = instance.id;
      if (<any>this.$refs.removeTenant) {
        (<any>this.$refs.removeTenant).show();
      }
    },
    closeDialog(): void {
      if (<any>this.$refs.removeTenant) {
        (<any>this.$refs.removeTenant).hide();
      }
    },
  },
});