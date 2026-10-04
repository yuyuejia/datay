import { type PropType, computed, defineComponent, inject, reactive, ref, watch } from 'vue';

import DataSourceService from '@/entities/data-source/data-source.service';
import AppPackageService from './app-package.service';
import {
  type IAppPackage,
  type IAppPackageInitResult,
  parsePackageContent,
  pickHttpErrorMessage,
  summaryEntries,
} from '@/shared/model/app-package.model';
import { useAlertService } from '@/shared/alert/alert.service';

const COUNT_LABELS: Record<string, string> = {
  dataSources: '数据源',
  dataSourcesCreated: '新建数据源',
  dataSourcesReused: '复用数据源',
  dataSourcesBound: '指定绑定数据源',
  modelDirectories: '模型目录',
  models: '数据模型',
  modelsSkipped: '跳过的模型',
  modelFields: '模型字段',
  metricDirectories: '指标目录',
  metrics: '指标',
  metricsSkipped: '跳过的指标',
  etlTasks: 'ETL 任务',
  sqlJobs: 'SQL 任务',
  dagJobs: '编排任务',
  otherJobs: '其它任务',
  jobDepends: '任务依赖',
};

/**
 * 数据应用初始化对话框。
 *
 * <p>支持两种入口：基于市场里的资产包（传 {@code pkg}），或基于上传的资产包 JSON（传 {@code content}）。
 * 初始化前可以查看将安装的资产、选择冲突策略、为包内数据源指定目标租户的已有数据源。
 */
export default defineComponent({
  name: 'AppPackageInit',
  props: {
    modelValue: {
      type: Boolean,
      default: false,
    },
    pkg: {
      type: Object as PropType<IAppPackage | null>,
      default: null,
    },
    content: {
      type: String as PropType<string | null>,
      default: null,
    },
  },
  emits: ['update:modelValue', 'initialized'],
  setup(props, { emit }) {
    const appPackageService = inject('appPackageService', () => new AppPackageService());
    const dataSourceService = inject('dataSourceService', () => new DataSourceService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const step = ref<'form' | 'done'>('form');
    const submitting = ref(false);
    const errorMessage = ref('');
    const conflictStrategy = ref<'RENAME' | 'SKIP'>('RENAME');
    const onlineJobs = ref(false);
    const dataSourceMapping = reactive<Record<string, number | null>>({});
    const dataSourceOptions = ref<Array<{ id: number; label: string }>>([]);
    const result = ref<IAppPackageInitResult | null>(null);

    const parsed = computed(() => parsePackageContent(props.content ?? props.pkg?.content));

    const dataSources = computed(() => parsed.value?.dataSources || []);

    const summary = computed(() => {
      const fromContent = parsed.value?.summary;
      if (fromContent && Object.keys(fromContent).length > 0) {
        return summaryEntries(fromContent);
      }
      return summaryEntries(props.pkg?.itemSummary);
    });

    const displayName = computed(() => props.pkg?.name || parsed.value?.meta?.name || '上传的资产包');
    const displayCode = computed(() => props.pkg?.code || parsed.value?.meta?.code || '-');
    const displayDescription = computed(() => props.pkg?.description || parsed.value?.meta?.description || '');

    const loadDataSources = async () => {
      try {
        const res = await dataSourceService().retrieve({ page: 0, size: 500 });
        dataSourceOptions.value = (res.data || []).map((item: any) => ({
          id: item.id,
          label: `${item.name}（${item.type}）`,
        }));
      } catch (error) {
        // 数据源列表加载失败时仍允许走自动匹配流程
      }
    };

    const reset = () => {
      step.value = 'form';
      submitting.value = false;
      errorMessage.value = '';
      conflictStrategy.value = 'RENAME';
      onlineJobs.value = false;
      result.value = null;
      Object.keys(dataSourceMapping).forEach((key) => delete dataSourceMapping[key]);
    };

    watch(
      () => props.modelValue,
      async (visible) => {
        if (visible) {
          reset();
          await loadDataSources();
        }
      }
    );

    const collectMapping = (): Record<string, number> => {
      const mapping: Record<string, number> = {};
      Object.entries(dataSourceMapping).forEach(([key, value]) => {
        if (value != null) {
          mapping[key] = value;
        }
      });
      return mapping;
    };

    const submit = async () => {
      submitting.value = true;
      errorMessage.value = '';
      const payload = {
        conflictStrategy: conflictStrategy.value,
        onlineJobs: onlineJobs.value,
        dataSourceMapping: collectMapping(),
      };
      try {
        const response = props.pkg?.id
          ? await appPackageService().initPackage(props.pkg.id, payload)
          : await appPackageService().importContent({ ...payload, content: props.content });
        result.value = response;
        step.value = 'done';
        emit('initialized', response);
        alertService.showSuccess('数据应用初始化完成');
      } catch (error: any) {
        errorMessage.value = pickHttpErrorMessage(error, '初始化失败，请检查资产包内容与当前租户的已有资产');
      } finally {
        submitting.value = false;
      }
    };

    const close = () => {
      emit('update:modelValue', false);
    };

    const countLabel = (key: string) => COUNT_LABELS[key] || key;

    const actionLabel = (action?: string) => {
      if (action === 'CREATED') {
        return '新建';
      }
      if (action === 'REUSED') {
        return '复用已有';
      }
      return '指定绑定';
    };

    return {
      step,
      submitting,
      errorMessage,
      conflictStrategy,
      onlineJobs,
      dataSourceMapping,
      dataSourceOptions,
      dataSources,
      summary,
      result,
      displayName,
      displayCode,
      displayDescription,
      submit,
      close,
      countLabel,
      actionLabel,
    };
  },
});
