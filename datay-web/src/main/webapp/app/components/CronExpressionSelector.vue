<template>
  <div class="input-group">
    <!-- 输入框，显示cron表达式，点击可打开模态框 -->
    <input type="text" class="form-control" v-model="cronExpression" @input="confirmInput($event.target.value)" />
    <!-- 按钮，点击可打开模态框 -->
    <button type="button" class="btn btn-outline-secondary" @click="showModal = true">常用</button>

    <b-modal v-model="showModal" id="cronExpressionModal" title="设置Cron表达式" size="lg">
      <div class="modal-body">
        <!-- <vue3-cron-plus 
          v-model:value="tempCronExpression"
          :i18n="i18n"
          :hide-year="hideYear"
          :hide-second="hideSecond"
          @change="onCronChange"
        /> -->

        <!-- 显示当前cron表达式的含义 -->
        <!-- <div class="cron-description mt-3 p-2 bg-light rounded">
          <small class="text-muted">表达式含义: {{ cronDescription }}</small>
        </div> -->

        <!-- 常用预设 -->
        <div class="preset-crons mt-3">
          <h6>常用预设:</h6>
          <div class="btn-group btn-group-sm" role="group">
            <button type="button" class="btn btn-outline-primary" @click="setPreset('0 0 * * * ?')">每小时</button>
            <button type="button" class="btn btn-outline-primary" @click="setPreset('0 0 12 * * ?')">每天中午12点</button>
            <button type="button" class="btn btn-outline-primary" @click="setPreset('0 0 9 * * MON-FRI')">工作日9点</button>
            <button type="button" class="btn btn-outline-primary" @click="setPreset('0 0 0 1 * ?')">每月1号</button>
            <button type="button" class="btn btn-outline-primary" @click="setPreset('0 0 0 ? * MON')">每周一</button>
          </div>
        </div>
      </div>

      <template #modal-footer>
        <div>
          <button type="button" class="btn btn-secondary" @click="cancelSelection">取消</button>
          <button type="button" class="btn btn-primary" @click="confirmSelection" :disabled="!isValidCron">确认</button>
        </div>
      </template>
    </b-modal>
  </div>
</template>

<script lang="ts" setup>
import { ref, computed, watch } from 'vue';
// import { Vue3CronPlus } from 'vue3-cron-plus';
import 'vue3-cron-plus/dist/index.css';

const props = defineProps<{
  value?: string;
  hideYear?: boolean;
  hideSecond?: boolean;
  hideMonth?: boolean;
  hideDay?: boolean;
  hideWeek?: boolean;
  hideHour?: boolean;
  hideMinute?: boolean;
}>();

const emit = defineEmits(['update:value', 'change']);

const showModal = ref(false);
const cronExpression = ref(props.value || '');
const tempCronExpression = ref(props.value || '');
const cronDescription = ref('');

// 验证cron表达式是否有效
const isValidCron = computed(() => {
  return tempCronExpression.value && tempCronExpression.value.trim().length > 0;
});

// 监听props.value的变化
watch(
  () => props.value,
  newValue => {
    if (newValue !== cronExpression.value) {
      cronExpression.value = newValue || '';
      tempCronExpression.value = newValue || '';
      updateCronDescription();
    }
  },
);

// 监听临时cron表达式的变化
watch(tempCronExpression, () => {
  updateCronDescription();
});

// 更新cron表达式描述
const updateCronDescription = () => {
  // 这里可以集成更复杂的cron表达式解析逻辑
  // 目前使用简单的描述
  if (!tempCronExpression.value) {
    cronDescription.value = '未设置';
    return;
  }

  // 简单的cron表达式解析（可以根据需要扩展）
  const parts = tempCronExpression.value.split(' ');
  if (parts.length >= 6) {
    const [second, minute, hour, day, month, week] = parts;
    cronDescription.value = `在${hour}时${minute}分${second}秒执行`;
  } else {
    cronDescription.value = '自定义表达式';
  }
};

// cron表达式变化回调
const onCronChange = (value: string) => {
  tempCronExpression.value = value;
};

// 设置预设表达式
const setPreset = (expression: string) => {
  tempCronExpression.value = expression;
  confirmSelection();
};

// 确认选择
const confirmSelection = () => {
  cronExpression.value = tempCronExpression.value;
  emit('update:value', cronExpression.value);
  emit('change', cronExpression.value);
  showModal.value = false;
};

const confirmInput = (value: string) => {
  if (value) {
    cronExpression.value = value;
    emit('change', cronExpression.value);
  }
};

// 取消选择
const cancelSelection = () => {
  tempCronExpression.value = cronExpression.value;
  showModal.value = false;
};

// 初始化时更新描述
updateCronDescription();
</script>

<style scoped>
.input-group {
  margin-bottom: 1rem;
}

.cron-description {
  font-size: 0.9em;
}

.preset-crons h6 {
  font-size: 0.9em;
  margin-bottom: 0.5rem;
}

.preset-crons .btn-group {
  flex-wrap: wrap;
}

.preset-crons .btn {
  margin: 2px;
  font-size: 0.8em;
}
</style>
