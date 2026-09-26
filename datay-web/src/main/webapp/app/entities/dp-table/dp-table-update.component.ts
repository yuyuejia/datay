import { type Ref, computed, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useVuelidate } from '@vuelidate/core';

import DpTableService from './dp-table.service';
import { useDateFormat, useValidation } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

import { DpTable, type IDpTable } from '@/shared/model/dp-table.model';
import DataSourceSelector from '@/components/DataSourceSelector.vue';

export default defineComponent({
  name: 'DpTableUpdate',
  components: {
    DataSourceSelector,
  },
  setup() {
    const dpTableService = inject('dpTableService', () => new DpTableService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const dpTable: Ref<IDpTable> = ref(new DpTable());
    const isSaving = ref(false);
    const currentLanguage = inject('currentLanguage', () => computed(() => navigator.language ?? 'zh-cn'), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);

    const retrieveDpTable = async dpTableId => {
      try {
        const res = await dpTableService().find(dpTableId);
        res.updateTime = new Date(res.updateTime);
        res.createTime = new Date(res.createTime);
        dpTable.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.dpTableId) {
      retrieveDpTable(route.params.dpTableId);
    }

    const handleSourceSelected = (selection: { dataSourceId: number; schema: string }) => {
      dpTable.value.sourceId = `${selection.dataSourceId}`;
      dpTable.value.sourceSchema = selection.schema;
    };

    const validations = useValidation();
    const validationRules = {
      name: {},
      schemaName: {},
      description: {},
      sourceDBType: {},
      sourceId: {},
      sourceSchema: {},
      sourceTable: {},
      sqlContent: {},
      fileType: {},
      filePath: {},
      updateTime: {},
      createTime: {},
      tenantId: {},
    };
    const v$ = useVuelidate(validationRules, dpTable as any);
    v$.value.$validate();

    return {
      dpTableService,
      alertService,
      dpTable,
      previousState,
      handleSourceSelected,
      isSaving,
      currentLanguage,
      v$,
      ...useDateFormat({ entityRef: dpTable }),
    };
  },
  created(): void {},
  methods: {
    save(): void {
      this.isSaving = true;
      if (this.dpTable.id) {
        this.dpTableService()
          .update(this.dpTable)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showInfo(`A DpTable is updated with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      } else {
        this.dpTableService()
          .create(this.dpTable)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showSuccess(`A DpTable is created with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      }
    },
  },
});
