import { type Ref, defineComponent, inject, ref } from 'vue';
import { useVuelidate } from '@vuelidate/core';
import { maxLength, required } from '@vuelidate/validators';
import { useRoute, useRouter } from 'vue-router';
import TenantManagementService from './tenant-management.service';
import { type ITenant, Tenant } from '@/shared/model/tenant.model';
import { useAlertService } from '@/shared/alert/alert.service';

const validations: any = {
  tenant: {
    code: {
      required,
      maxLength: maxLength(50),
    },
    name: {
      required,
      maxLength: maxLength(100),
    },
  },
};

export default defineComponent({
  name: 'JhiTenantManagementEdit',
  validations,
  setup() {
    const route = useRoute();
    const router = useRouter();

    const alertService = inject('alertService', () => useAlertService(), true);
    const tenantManagementService = inject('tenantManagementService', () => new TenantManagementService(), true);
    const previousState = () => router.go(-1);

    const tenant: Ref<ITenant> = ref({ ...new Tenant(), enabled: true });
    const isSaving: Ref<boolean> = ref(false);

    const loadTenant = async (tenantId: string) => {
      const response = await tenantManagementService.get(Number(tenantId));
      tenant.value = response.data;
    };

    const tenantId = route.params?.tenantId;

    if (tenantId != null) {
      loadTenant(tenantId as string);
    }

    return {
      alertService,
      tenant,
      isSaving,
      tenantManagementService,
      previousState,
      v$: useVuelidate(),
    };
  },
  methods: {
    save(): void {
      this.isSaving = true;
      if (this.tenant.id) {
        this.tenantManagementService
          .update(this.tenant)
          .then(res => {
            this.returnToList();
            this.alertService.showInfo(this.getToastMessageFromHeader(res));
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      } else {
        this.tenantManagementService
          .create(this.tenant)
          .then(res => {
            this.returnToList();
            this.alertService.showSuccess(this.getToastMessageFromHeader(res));
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      }
    },

    returnToList(): void {
      this.isSaving = false;
      this.previousState();
    },

    getToastMessageFromHeader(res: any): string {
      return res.headers['x-datafusionapp-alert'];
    },
  },
});