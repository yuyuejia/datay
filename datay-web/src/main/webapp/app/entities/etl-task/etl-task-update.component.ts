import { type Ref, computed, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useVuelidate } from '@vuelidate/core';

import ETLTaskService from './etl-task.service';
import { useDateFormat, useValidation } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

import { ETLTask, type IETLTask } from '@/shared/model/etl-task.model';

import CronExpressionSelector from '@/components/CronExpressionSelector.vue';
import etlComponentComponent from '../etl-component/etl-component.component';

export default defineComponent({
  name: 'ETLTaskUpdate',
  components: {
    CronExpressionSelector,
  },
  setup() {
    const eTLTaskService = inject('eTLTaskService', () => new ETLTaskService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const eTLTask: Ref<IETLTask> = ref(new ETLTask());
    const isSaving = ref(false);
    const currentLanguage = inject('currentLanguage', () => computed(() => navigator.language ?? 'zh-cn'), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);

    const onCronChange = (value: string) => {
      eTLTask.value.cron = value;
      console.log('Cron表达式已更新:', value);
    };

    const retrieveETLTask = async (eTLTaskId: string) => {
      try {
        const res = await eTLTaskService().find(eTLTaskId);
        res.updateTime = new Date(res.updateTime);
        res.createTime = new Date(res.createTime);
        eTLTask.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.eTLTaskId) {
      retrieveETLTask(route.params.eTLTaskId);
    }

    const validations = useValidation();
    const validationRules = {
      taskName: {},
      taskCode: {},
      jobId: {},
      taskDesc: {},
      dir: {},
      type: {},
      cron: {},
      jobContext: {},
      status: {},
      lastStatus: {},
      updateTime: {},
      createTime: {},
      creater: {},
      project: {},
      tenantId: {},
      dr: {},
    };
    const v$ = useVuelidate(validationRules, eTLTask as any);
    v$.value.$validate();

    return {
      eTLTaskService,
      alertService,
      eTLTask,
      previousState,
      onCronChange,
      isSaving,
      currentLanguage,
      v$,
      ...useDateFormat({ entityRef: eTLTask }),
    };
  },
  created(): void {},
  methods: {
    save(): void {
      this.isSaving = true;
      if (this.eTLTask.id) {
        this.eTLTaskService()
          .update(this.eTLTask)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showInfo(`A ETLTask is updated with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      } else {
        this.eTLTaskService()
          .create(this.eTLTask)
          .then(param => {
            this.isSaving = false;
            this.previousState();
            this.alertService.showSuccess(`A ETLTask is created with identifier ${param.id}`);
          })
          .catch(error => {
            this.isSaving = false;
            this.alertService.showHttpError(error.response);
          });
      }
    },
  },
});
