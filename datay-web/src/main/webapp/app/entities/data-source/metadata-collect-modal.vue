<template>
  <app-modal v-model="showModal" title="元数据采集" size="lg" :close-on-backdrop="false" @hidden="onHidden">
    <div class="modal-body metadata-collect-body">
      <div class="source-heading">
        <div class="source-title">
          <font-awesome-icon icon="database" class="source-icon" />
          <span>{{ dataSource?.name || '数据源' }}</span>
        </div>
        <span class="source-url">{{ dataSource?.url || '-' }}</span>
      </div>

      <el-form :model="form" label-width="110px" @submit.prevent>
        <el-form-item label="任务名称">
          <el-input v-model="form.taskName" maxlength="255" placeholder="默认使用“数据源名称 + 元数据采集”" />
        </el-form-item>

        <el-form-item label="采集 Schema">
          <el-select
            v-model="form.schemaNames"
            multiple
            filterable
            allow-create
            default-first-option
            clearable
            :loading="schemaLoading"
            placeholder="不选表示使用数据源默认 Schema"
            style="width: 100%"
          >
            <el-option v-for="schema in schemaOptions" :key="schema" :value="schema" :label="schema" />
          </el-select>
          <div class="form-tip">不选择时使用数据源配置的默认 Schema；默认 Schema 为空时采集全部非系统 Schema。</div>
        </el-form-item>

        <el-form-item label="定期采集">
          <el-switch v-model="form.enabled" />
          <span class="form-tip">开启后按 Cron 表达式定期采集表与字段信息</span>
        </el-form-item>

        <el-form-item v-if="form.enabled" label="调度周期">
          <CronExpressionSelector v-model:value="form.cron" />
        </el-form-item>

        <div v-if="form.lastRun" class="last-run-card">
          <div class="last-run-header">
            <span class="last-run-title">最近执行</span>
            <el-tag :type="statusTagType(form.lastRun.status)" size="small">{{ statusLabel(form.lastRun.status) }}</el-tag>
          </div>
          <div class="last-run-meta">
            <span><span class="meta-label">实例</span>{{ form.lastRun.instanceCode || form.lastRun.id || '-' }}</span>
            <span><span class="meta-label">时间</span>{{ formatTime(form.lastRun.startTime || form.lastRun.createTime) || '-' }}</span>
          </div>
          <div v-if="form.lastRun.jobMessage" class="last-run-message">{{ form.lastRun.jobMessage }}</div>
          <div class="last-run-actions">
            <el-button size="small" :disabled="!form.lastRun.tablesDownloadUrl" @click="openUrl(form.lastRun.tablesDownloadUrl)">
              下载 tables.csv
            </el-button>
            <el-button size="small" :disabled="!form.lastRun.columnsDownloadUrl" @click="openUrl(form.lastRun.columnsDownloadUrl)">
              下载 columns.csv
            </el-button>
          </div>
        </div>
        <div v-else class="empty-run">暂无执行记录</div>
      </el-form>
    </div>

    <template #modal-footer>
      <div class="modal-footer-actions">
        <el-button @click="hide">取消</el-button>
        <el-button :loading="isLoading" @click="refresh">刷新</el-button>
        <el-button :loading="isSaving" @click="save">保存配置</el-button>
        <el-button type="primary" :loading="isRunning" @click="runNow">立即执行</el-button>
      </div>
    </template>
  </app-modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';

import CronExpressionSelector from '@/components/CronExpressionSelector.vue';
import DataSourceService from '@/entities/data-source/data-source.service';
import MetadataCollectTaskService, { type IMetadataCollectRun, type IMetadataCollectTask } from './metadata-collect';

const props = defineProps<{
  show: boolean;
  dataSource: any;
}>();

const emit = defineEmits(['update:show', 'saved']);

const dataSourceService = new DataSourceService();
const service = new MetadataCollectTaskService();

const showModal = computed({
  get: () => props.show,
  set: value => emit('update:show', value),
});

const form = reactive<IMetadataCollectTask>({
  dataSourceId: '',
  dataSourceName: '',
  taskName: '',
  schemaNames: [],
  cron: '0 0 2 * * ?',
  enabled: false,
});

const schemaOptions = ref<string[]>([]);
const schemaLoading = ref(false);
const isLoading = ref(false);
const isSaving = ref(false);
const isRunning = ref(false);

watch(
  () => props.show,
  visible => {
    if (visible) {
      refresh();
    }
  },
  { immediate: true },
);

const reset = () => {
  Object.assign(form, {
    id: undefined,
    dataSourceId: props.dataSource?.id,
    dataSourceName: props.dataSource?.name,
    taskName: `${props.dataSource?.name || ''} 元数据采集`.trim(),
    schemaNames: props.dataSource?.schemaName ? [props.dataSource.schemaName] : [],
    cron: '0 0 2 * * ?',
    enabled: false,
    jobId: undefined,
    status: undefined,
    lastStatus: undefined,
    lastRunTime: undefined,
    lastRun: undefined,
  });
};

const refresh = async () => {
  if (!props.dataSource?.id) {
    return;
  }
  isLoading.value = true;
  try {
    reset();
    await loadSchemas();
    const config = await service.getByDataSource(props.dataSource.id);
    if (config) {
      Object.assign(form, config);
      if (!form.schemaNames) {
        form.schemaNames = [];
      }
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.detail || e?.response?.data?.message || e?.message || '加载元数据采集配置失败');
  } finally {
    isLoading.value = false;
  }
};

const loadSchemas = async () => {
  schemaLoading.value = true;
  try {
    const res = await dataSourceService.getSchemas(props.dataSource.id);
    schemaOptions.value = res.data || [];
  } catch {
    schemaOptions.value = [];
  } finally {
    schemaLoading.value = false;
  }
};

const save = async (): Promise<IMetadataCollectTask | null> => {
  if (!props.dataSource?.id) {
    ElMessage.error('数据源不能为空');
    return null;
  }
  if (form.enabled && !form.cron?.trim()) {
    ElMessage.warning('启用定期采集时请设置 Cron 表达式');
    return null;
  }
  isSaving.value = true;
  try {
    const saved = await service.save({
      ...form,
      dataSourceId: props.dataSource.id,
      dataSourceName: props.dataSource.name,
    });
    Object.assign(form, saved);
    if (!form.schemaNames) {
      form.schemaNames = [];
    }
    ElMessage.success('元数据采集配置已保存');
    emit('saved');
    return saved;
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.detail || e?.response?.data?.message || e?.message || '保存失败');
    return null;
  } finally {
    isSaving.value = false;
  }
};

const runNow = async () => {
  isRunning.value = true;
  try {
    const saved = await save();
    if (!saved?.id) {
      return;
    }
    const run = await service.execute(saved.id);
    form.lastRun = run as IMetadataCollectRun;
    ElMessage.success('元数据采集任务已提交');
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.detail || e?.response?.data?.message || e?.message || '执行失败');
  } finally {
    isRunning.value = false;
  }
};

const hide = () => {
  showModal.value = false;
};

const onHidden = () => {
  emit('update:show', false);
};

const openUrl = (url?: string) => {
  if (url) {
    window.open(url, '_blank');
  }
};

const statusTagType = (status?: string) => {
  switch ((status || '').toUpperCase()) {
    case 'SUCCESSFUL':
      return 'success';
    case 'FAILED':
    case 'TIMEOUT':
      return 'danger';
    case 'RUNNING':
    case 'APPENDING':
    case 'WAITING':
      return 'warning';
    default:
      return 'info';
  }
};

const statusLabel = (status?: string) => {
  switch ((status || '').toUpperCase()) {
    case 'SUCCESSFUL':
      return '成功';
    case 'FAILED':
      return '失败';
    case 'TIMEOUT':
      return '超时';
    case 'RUNNING':
      return '运行中';
    case 'APPENDING':
      return '等待执行';
    case 'WAITING':
      return '等待中';
    default:
      return status || '-';
  }
};

const formatTime = (value?: string) => {
  if (!value) {
    return '';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(
    date.getSeconds(),
  )}`;
};
</script>

<style scoped>
.metadata-collect-body {
  min-height: 320px;
}

.source-heading {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 18px;
  padding: 12px 14px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #f8fafc;
}

.source-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #1f2937;
}

.source-icon {
  color: #409eff;
}

.source-url {
  overflow: hidden;
  color: #6b7280;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.form-tip {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.form-tip code {
  padding: 1px 4px;
  border-radius: 3px;
  background: #f2f3f5;
  color: #409eff;
}

.last-run-card,
.empty-run {
  margin-top: 18px;
  padding: 12px 14px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
}

.empty-run {
  color: #909399;
  font-size: 13px;
  text-align: center;
}

.last-run-header,
.last-run-meta,
.last-run-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.last-run-header {
  justify-content: space-between;
  margin-bottom: 8px;
}

.last-run-title {
  font-weight: 600;
  color: #303133;
}

.last-run-meta {
  color: #606266;
  font-size: 13px;
}

.meta-label {
  margin-right: 4px;
  color: #909399;
}

.last-run-message {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #f5f7fa;
  color: #606266;
  font-size: 12px;
  white-space: pre-wrap;
}

.last-run-actions {
  margin-top: 10px;
}

.modal-footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
