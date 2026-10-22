<template>
  <app-modal ref="modal" id="taskOnlineModal" size="md">
    <template #modal-title>
      <span>{{ title }}</span>
    </template>
    <div class="modal-body online-modal-body">
      <p v-if="taskName">
        任务：<strong>{{ taskName }}</strong>
      </p>
      <el-alert
        v-if="!cronValue.trim()"
        type="warning"
        :closable="false"
        show-icon
        title="当前任务尚未设置调度周期，请设置 Cron 表达式后再上线。"
        class="mb-3"
      />
      <p v-else class="online-current-cron">
        当前调度周期：<code>{{ cronValue }}</code>
      </p>
      <div class="online-cron-field">
        <label class="online-cron-label">调度周期（Cron 表达式）</label>
        <CronExpressionSelector v-model:value="cronValue" />
      </div>
      <div class="online-policy-row">
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
      <p class="text-muted online-cron-tip">{{ tip }}</p>
    </div>
    <template #modal-footer>
      <div>
        <el-button @click="hide" :disabled="loading">取消</el-button>
        <el-button
          type="primary"
          :loading="loading"
          :disabled="!cronValid || !policyValid"
          @click="onConfirm"
          data-cy="entityConfirmOnlineButton"
        >
          确认上线
        </el-button>
      </div>
    </template>
  </app-modal>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';

import CronExpressionSelector from '@/components/CronExpressionSelector.vue';

const props = defineProps({
  title: {
    type: String,
    default: '上线任务',
  },
  taskName: {
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
  loading: {
    type: Boolean,
    default: false,
  },
  tip: {
    type: String,
    default: '任务上线后将按该 Cron 表达式自动调度，如需调整可在此修改。',
  },
});

const emit = defineEmits(['confirm']);

const modal = ref<any>(null);
const cronValue = ref('');
const retryCountValue = ref(0);
const timeoutSecondsValue = ref(0);
const priorityValue = ref(2);

/** Cron 表达式基础校验：6 段或 7 段。 */
const cronValid = computed(() => {
  const parts = (cronValue.value || '').trim().split(/\s+/).filter(Boolean);
  return parts.length === 6 || parts.length === 7;
});

const policyValid = computed(
  () =>
    Number.isInteger(retryCountValue.value) &&
    retryCountValue.value >= 0 &&
    retryCountValue.value <= 10 &&
    Number.isInteger(timeoutSecondsValue.value) &&
    timeoutSecondsValue.value >= 0 &&
    timeoutSecondsValue.value <= 604800 &&
    Number.isInteger(priorityValue.value) &&
    priorityValue.value >= 1 &&
    priorityValue.value <= 3,
);

const show = (initialCron?: string, initialRetryCount?: number, initialTimeoutSeconds?: number, initialPriority?: number) => {
  cronValue.value = initialCron ?? props.cron ?? '';
  retryCountValue.value = initialRetryCount ?? props.retryCount ?? 0;
  timeoutSecondsValue.value = initialTimeoutSeconds ?? props.timeoutSeconds ?? 0;
  priorityValue.value = initialPriority ?? props.priority ?? 2;
  modal.value?.show();
};

const hide = () => {
  modal.value?.hide();
};

const onConfirm = () => {
  if (!cronValid.value || !policyValid.value) {
    return;
  }
  emit('confirm', {
    cron: cronValue.value.trim(),
    retryCount: retryCountValue.value,
    timeoutSeconds: timeoutSecondsValue.value,
    priority: priorityValue.value,
  });
};

defineExpose({ show, hide });
</script>

<style scoped>
.online-modal-body p {
  margin-bottom: 12px;
}

.online-current-cron code {
  padding: 2px 6px;
  border-radius: 4px;
  background: #f5f7fa;
  color: #409eff;
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
</style>
