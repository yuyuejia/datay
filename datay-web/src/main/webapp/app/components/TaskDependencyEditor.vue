<template>
  <div class="task-dependency-editor">
    <div class="task-dependency-header">
      <label class="online-cron-label">上游依赖任务（可选）</label>
      <el-button size="small" :disabled="loading" @click="addDependency">
        <font-awesome-icon icon="plus" />
        <span>添加上游任务</span>
      </el-button>
    </div>
    <p class="task-dependency-tip">仅当上游任务最近一次成功实例在有效窗口内时，当前任务才会执行；不满足时实例会进入等待。</p>
    <div v-if="loading" class="task-dependency-loading">正在加载可选任务与已有依赖...</div>
    <template v-else>
      <div v-if="modelValue.length === 0" class="task-dependency-empty">未配置上游依赖，任务将按 Cron 直接执行。</div>
      <div v-for="(item, index) in modelValue" :key="index" class="task-dependency-row">
        <el-select
          :model-value="item.parentJobCode || ''"
          filterable
          placeholder="选择上游任务"
          class="task-dependency-select"
          @update:model-value="value => updateDependency(index, { parentJobCode: value })"
        >
          <el-option v-for="job in optionsFor(index)" :key="job.id" :label="jobLabel(job)" :value="job.id">
            <span class="task-dependency-option-name">{{ job.jobName || job.id }}</span>
            <span class="task-dependency-option-meta">{{ job.type || '-' }} · {{ statusLabel(job.status) }}</span>
          </el-option>
        </el-select>
        <el-input-number
          :model-value="item.lastIntervalMinutes"
          :min="1"
          :max="43200"
          :step="60"
          controls-position="right"
          class="task-dependency-interval"
          @update:model-value="value => updateDependency(index, { lastIntervalMinutes: Number(value) || 1 })"
        />
        <span class="task-dependency-unit">分钟</span>
        <el-button size="small" type="danger" plain @click="removeDependency(index)">删除</el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, type PropType } from 'vue';

import type { IJob } from '@/shared/model/job.model';
import type { ITaskDependencyFormItem } from '@/shared/model/task-dependency.model';

const props = defineProps({
  modelValue: {
    type: Array as PropType<ITaskDependencyFormItem[]>,
    default: () => [],
  },
  jobs: {
    type: Array as PropType<IJob[]>,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
  excludedJobIds: {
    type: Array as PropType<string[]>,
    default: () => [],
  },
});

const emit = defineEmits<{
  'update:modelValue': [value: ITaskDependencyFormItem[]];
}>();

const availableJobs = computed(() => {
  const excluded = new Set(props.excludedJobIds.filter(Boolean));
  return props.jobs.filter(job => job.id && !excluded.has(job.id));
});

const selectedByOthers = (selfIndex: number) => {
  const selected = new Set<string>();
  props.modelValue.forEach((item, index) => {
    if (index !== selfIndex && item.parentJobCode) {
      selected.add(item.parentJobCode);
    }
  });
  return selected;
};

const optionsFor = (index: number) => {
  const selected = selectedByOthers(index);
  return availableJobs.value.filter(job => job.id && !selected.has(job.id));
};

const statusLabel = (status?: string | null) => {
  if (status === 'ONLINE') {
    return '在线';
  }
  if (status === 'OFFLINE') {
    return '离线';
  }
  return status || '未知状态';
};

const jobLabel = (job: IJob) => `${job.jobName || job.id}（${job.type || '-'} · ${statusLabel(job.status)}）`;

const addDependency = () => {
  emit('update:modelValue', [...props.modelValue, { parentJobCode: '', lastIntervalMinutes: 1440 }]);
};

const removeDependency = (index: number) => {
  emit(
    'update:modelValue',
    props.modelValue.filter((_, itemIndex) => itemIndex !== index),
  );
};

const updateDependency = (index: number, patch: Partial<ITaskDependencyFormItem>) => {
  emit(
    'update:modelValue',
    props.modelValue.map((item, itemIndex) => (itemIndex === index ? { ...item, ...patch } : item)),
  );
};
</script>

<style scoped>
.task-dependency-editor {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}

.task-dependency-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.task-dependency-tip {
  margin: 8px 0 12px;
  font-size: 12px;
  color: #909399;
}

.task-dependency-loading,
.task-dependency-empty {
  padding: 12px;
  border-radius: 4px;
  background: #f5f7fa;
  color: #909399;
  font-size: 13px;
}

.task-dependency-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.task-dependency-select {
  width: 340px;
  max-width: 100%;
}

.task-dependency-interval {
  width: 150px;
}

.task-dependency-unit {
  font-size: 13px;
  color: #606266;
}

.task-dependency-option-name {
  margin-right: 8px;
}

.task-dependency-option-meta {
  font-size: 12px;
  color: #909399;
}
</style>
