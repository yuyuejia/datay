<template>
  <app-modal ref="modal" id="taskOnlineModal" size="lg">
    <template #modal-title>
      <span>{{ taskName ? `${title} - ${taskName}` : title }}</span>
    </template>
    <div class="modal-body online-modal-body">
      <el-alert
        v-if="!cronValue.trim()"
        type="warning"
        :closable="false"
        show-icon
        title="当前任务尚未设置调度周期，请设置 Cron 表达式后再上线。"
        class="mb-3"
      />
      <div class="online-cron-field">
        <label class="online-cron-label">调度周期（Cron 表达式）</label>
        <CronExpressionSelector v-model:value="cronValue" />
      </div>
      <div v-if="showPolicy" class="online-policy-row">
        <div class="online-policy-field">
          <label class="online-cron-label">重试次数</label>
          <el-input-number v-model="retryCountValue" :min="0" :max="10" :step="1" controls-position="right" />
          <span class="online-policy-tip">0 表示不重试，任务失败或超时后自动重试</span>
        </div>
        <div class="online-policy-field">
          <label class="online-cron-label">超时时间（秒）</label>
          <el-input-number v-model="timeoutSecondsValue" :min="0" :max="604800" :step="60" controls-position="right" />
          <span class="online-policy-tip">0 表示不限制，每次尝试单独计时</span>
        </div>
        <div class="online-policy-field">
          <label class="online-cron-label">优先级</label>
          <el-radio-group v-model="priorityValue">
            <el-radio-button :value="3">高</el-radio-button>
            <el-radio-button :value="2">中</el-radio-button>
            <el-radio-button :value="1">低</el-radio-button>
          </el-radio-group>
          <span class="online-policy-tip">仅影响排队顺序，不抢占正在执行的任务</span>
        </div>
      </div>

      <el-alert
        v-if="showDependencies && dependencyError"
        type="error"
        :closable="false"
        show-icon
        class="mb-3 dependency-error"
        title="上游依赖加载失败，请重试后再上线，本次不会修改已有依赖。"
      >
        <el-button size="small" text type="primary" @click="reloadDependencies">重新加载</el-button>
      </el-alert>
      <TaskDependencyEditor
        v-if="showDependencies"
        v-model="dependencies"
        :jobs="jobs"
        :loading="dependencyLoading"
        :excluded-job-ids="excludedJobIds"
      />

      <p class="text-muted online-cron-tip">{{ tip }}</p>
    </div>
    <template #modal-footer>
      <div>
        <el-button @click="hide" :disabled="loading">取消</el-button>
        <el-button type="primary" :loading="loading" :disabled="confirmDisabled" @click="onConfirm" data-cy="entityConfirmOnlineButton">
          确认上线
        </el-button>
      </div>
    </template>
  </app-modal>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';

import CronExpressionSelector from '@/components/CronExpressionSelector.vue';
import TaskDependencyEditor from '@/components/TaskDependencyEditor.vue';
import JobService from '@/entities/job/job.service';
import JobDependService from '@/entities/job-depend/job-depend.service';
import type { IJob } from '@/shared/model/job.model';
import type { IJobDepend } from '@/shared/model/job-depend.model';
import type { ITaskDependencyFormItem, ITaskOnlineConfirmPayload, ITaskOnlineShowPayload } from '@/shared/model/task-dependency.model';

const DEFAULT_LAST_INTERVAL_MINUTES = 1440;

const props = defineProps({
  title: {
    type: String,
    default: '上线任务',
  },
  taskName: {
    type: String,
    default: '',
  },
  jobId: {
    type: String,
    default: '',
  },
  cron: {
    type: String,
    default: '',
  },
  retryCount: {
    type: Number,
    default: 0,
  },
  timeoutSeconds: {
    type: Number,
    default: 0,
  },
  priority: {
    type: Number,
    default: 2,
  },
  /** 是否展示重试次数 / 超时时间 / 优先级等调度策略。 */
  showPolicy: {
    type: Boolean,
    default: true,
  },
  /** 是否展示上游依赖编辑器；不需要依赖的任务可关闭以减少请求。 */
  showDependencies: {
    type: Boolean,
    default: true,
  },
  loading: {
    type: Boolean,
    default: false,
  },
  tip: {
    type: String,
    default: '任务上线后将按该 Cron 表达式自动调度，如需调整可在此修改。',
  },
});

const emit = defineEmits<{
  confirm: [payload: ITaskOnlineConfirmPayload];
}>();

const modal = ref<any>(null);
const cronValue = ref('');
const retryCountValue = ref(0);
const timeoutSecondsValue = ref(0);
const priorityValue = ref(2);

const jobs = ref<IJob[]>([]);
const dependencies = ref<ITaskDependencyFormItem[]>([]);
const dependencyLoading = ref(false);
const dependencyError = ref('');
const dependenciesLoaded = ref(true);
const currentJobId = ref('');
let dependencyRequestId = 0;

/** Cron 表达式基础校验：6 段或 7 段。 */
const cronValid = computed(() => {
  const parts = (cronValue.value || '').trim().split(/\s+/).filter(Boolean);
  return parts.length === 6 || parts.length === 7;
});

const policyValid = computed(
  () =>
    !props.showPolicy ||
    (Number.isInteger(retryCountValue.value) &&
      retryCountValue.value >= 0 &&
      retryCountValue.value <= 10 &&
      Number.isInteger(timeoutSecondsValue.value) &&
      timeoutSecondsValue.value >= 0 &&
      timeoutSecondsValue.value <= 604800 &&
      Number.isInteger(priorityValue.value) &&
      priorityValue.value >= 1 &&
      priorityValue.value <= 3),
);

const dependencyValid = computed(() => {
  if (!props.showDependencies) {
    return true;
  }
  const selected = new Set<string>();
  return dependencies.value.every(item => {
    const parentJobCode = (item.parentJobCode || '').trim();
    if (!parentJobCode || parentJobCode === currentJobId.value || selected.has(parentJobCode)) {
      return false;
    }
    selected.add(parentJobCode);
    return Number.isFinite(item.lastIntervalMinutes) && item.lastIntervalMinutes >= 1;
  });
});

const dependenciesReady = computed(
  () => !props.showDependencies || (dependenciesLoaded.value && !dependencyLoading.value && !dependencyError.value),
);

const confirmDisabled = computed(() => !cronValid.value || !policyValid.value || !dependencyValid.value || !dependenciesReady.value);

const excludedJobIds = computed(() => (currentJobId.value ? [currentJobId.value] : []));

const millisecondsToMinutes = (value?: number | null) =>
  Math.max(1, Math.round((value ?? DEFAULT_LAST_INTERVAL_MINUTES * 60 * 1000) / 60000));

const loadAllJobs = async (): Promise<IJob[]> => {
  const pageSize = 500;
  const allJobs: IJob[] = [];
  let page = 0;
  // 分页拉全量任务，避免下拉框只显示第一页。
  while (true) {
    const res = await new JobService().retrieve({ page, size: pageSize, sort: ['jobName,asc'] });
    const rows: IJob[] = res.data || [];
    allJobs.push(...rows);
    const total = Number(res.headers?.['x-total-count']);
    if (Number.isFinite(total)) {
      if (allJobs.length >= total) {
        break;
      }
    } else if (rows.length < pageSize) {
      break;
    }
    if (rows.length === 0) {
      break;
    }
    page += 1;
  }
  return allJobs;
};

const loadDependencies = async (jobId: string) => {
  const requestId = ++dependencyRequestId;
  currentJobId.value = jobId;
  dependencyError.value = '';
  if (!jobId || !props.showDependencies) {
    jobs.value = [];
    dependencies.value = [];
    dependencyLoading.value = false;
    dependenciesLoaded.value = true;
    return;
  }
  dependencyLoading.value = true;
  dependenciesLoaded.value = false;
  try {
    const [jobList, existingDependencies] = await Promise.all([loadAllJobs(), new JobDependService().retrieveByChildJobCode(jobId)]);
    if (requestId !== dependencyRequestId) {
      return;
    }
    jobs.value = jobList;
    dependencies.value = (existingDependencies || []).map((item: IJobDepend) => ({
      parentJobCode: item.parentJobCode || '',
      lastIntervalMinutes: millisecondsToMinutes(item.lastInterval),
    }));
    dependenciesLoaded.value = true;
  } catch {
    if (requestId !== dependencyRequestId) {
      return;
    }
    jobs.value = [];
    dependencies.value = [];
    dependencyError.value = 'load-failed';
    dependenciesLoaded.value = false;
  } finally {
    if (requestId === dependencyRequestId) {
      dependencyLoading.value = false;
    }
  }
};

const reloadDependencies = () => {
  void loadDependencies(currentJobId.value);
};

const show = (payload: ITaskOnlineShowPayload = {}) => {
  cronValue.value = payload.cron ?? props.cron ?? '';
  retryCountValue.value = payload.retryCount ?? props.retryCount ?? 0;
  timeoutSecondsValue.value = payload.timeoutSeconds ?? props.timeoutSeconds ?? 0;
  priorityValue.value = payload.priority ?? props.priority ?? 2;
  dependencies.value = [];
  dependencyError.value = '';
  dependencyLoading.value = false;
  dependenciesLoaded.value = true;
  const jobId = payload.jobId || props.jobId || '';
  currentJobId.value = jobId;
  modal.value?.show();
  void loadDependencies(jobId);
};

const hide = () => {
  modal.value?.hide();
};

const onConfirm = () => {
  if (confirmDisabled.value) {
    return;
  }
  emit('confirm', {
    cron: cronValue.value.trim(),
    retryCount: retryCountValue.value,
    timeoutSeconds: timeoutSecondsValue.value,
    priority: priorityValue.value,
    dependencies: dependencies.value.map(item => ({
      parentJobCode: item.parentJobCode.trim(),
      lastInterval: Math.round(item.lastIntervalMinutes * 60 * 1000),
    })),
  });
};

defineExpose({ show, hide });
</script>

<style scoped>
.online-modal-body p {
  margin-bottom: 12px;
}

.online-cron-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}

.online-cron-label {
  font-size: 13px;
  color: #606266;
}

.online-policy-row {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  margin-top: 16px;
}

.online-policy-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 200px;
}

.online-policy-tip {
  font-size: 12px;
  color: #909399;
}

.online-cron-tip {
  margin-top: 12px;
  margin-bottom: 0;
}

.dependency-error {
  margin-top: 16px;
}
</style>
