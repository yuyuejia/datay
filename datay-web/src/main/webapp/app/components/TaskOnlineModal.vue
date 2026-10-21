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
      <p class="text-muted online-cron-tip">{{ tip }}</p>
    </div>
    <template #modal-footer>
      <div>
        <el-button @click="hide" :disabled="loading">取消</el-button>
        <el-button type="primary" :loading="loading" :disabled="!cronValid" @click="onConfirm" data-cy="entityConfirmOnlineButton">
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

/** Cron 表达式基础校验：6 段或 7 段。 */
const cronValid = computed(() => {
  const parts = (cronValue.value || '').trim().split(/\s+/).filter(Boolean);
  return parts.length === 6 || parts.length === 7;
});

const show = (initialCron?: string) => {
  cronValue.value = initialCron ?? props.cron ?? '';
  modal.value?.show();
};

const hide = () => {
  modal.value?.hide();
};

const onConfirm = () => {
  if (!cronValid.value) {
    return;
  }
  emit('confirm', cronValue.value.trim());
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

.online-cron-tip {
  margin-top: 12px;
  margin-bottom: 0;
}
</style>
