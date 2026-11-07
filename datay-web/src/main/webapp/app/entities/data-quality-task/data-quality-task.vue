<template>
  <div>
    <div class="page-toolbar" :class="{ 'page-toolbar-embedded': props.embedded }">
      <h2 v-if="!props.embedded" id="page-heading" class="d-flex align-items-center flex-nowrap flex-wrap-nowrap mb-0">
        <span id="data-quality-task-heading">质量检核</span>
      </h2>
      <div class="d-flex align-items-center">
        <el-input
          v-model="search"
          class="mr-2"
          style="width: 240px"
          placeholder="按任务名称 / 描述 / 表名搜索"
          clearable
          @keyup.enter="retrieve"
          @clear="retrieve"
        />
        <el-button class="mr-2" type="info" @click="retrieve" :disabled="isFetching">
          <font-awesome-icon icon="sync" :spin="isFetching"></font-awesome-icon>
          <span>刷新</span>
        </el-button>
        <router-link :to="{ name: 'DataQualityTaskCreate' }" custom v-slot="{ navigate }">
          <el-button type="primary" @click="navigate">
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>新建检核任务</span>
          </el-button>
        </router-link>
      </div>
    </div>

    <div class="alert alert-warning" v-if="!isFetching && tasks.length === 0">未找到数据质量检核任务</div>

    <div v-if="tasks.length > 0" class="list-page-scroll">
      <el-table :data="tasks" style="width: 100%">
        <el-table-column prop="taskName" label="任务名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="检核对象" min-width="260">
          <template #default="scope">
            <div class="object-cell">
              <div>{{ scope.row.schemaName ? scope.row.schemaName + '.' : '' }}{{ scope.row.tableName }}</div>
              <div class="object-sub">{{ dataSourceLabel(scope.row.dataSourceId) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="规则数" width="90">
          <template #default="scope">{{ scope.row.rules?.length || 0 }}</template>
        </el-table-column>
        <el-table-column label="最近状态" width="110">
          <template #default="scope">
            <span :class="'status-badge status-' + ((scope.row.lastStatus || scope.row.status || '').toLowerCase() || '')">
              {{ statusText(scope.row.lastStatus || scope.row.status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="检核结果" width="120">
          <template #default="scope">
            <el-tag v-if="scope.row.lastResult?.overallStatus" :type="overallTagType(scope.row.lastResult.overallStatus)" effect="plain">
              {{ overallText(scope.row.lastResult.overallStatus) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="最近执行" width="170">
          <template #default="scope">{{ formatDate(scope.row.lastRunTime || scope.row.lastResult?.createTime) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="300">
          <template #default="scope">
            <el-button type="primary" size="small" @click="goEdit(scope.row)">编辑</el-button>
            <el-button
              type="success"
              size="small"
              :loading="executingId === scope.row.id"
              :disabled="isTaskRunning(scope.row)"
              @click="executeTask(scope.row)"
            >
              立即执行
            </el-button>
            <el-button type="info" plain size="small" @click="openResults(scope.row)">结果</el-button>
            <el-button type="danger" plain size="small" @click="deleteTask(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="total > size" class="d-flex justify-content-end mt-3">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>

    <el-dialog v-model="resultVisible" :title="`检核结果 - ${activeTask?.taskName || ''}`" width="86%" destroy-on-close>
      <div v-if="activeResult" class="result-summary">
        <div class="result-toolbar">
          <div class="result-heading">
            <span class="result-title">质量汇总</span>
            <el-tag v-if="activeResult.overallStatus" :type="overallTagType(activeResult.overallStatus)" effect="dark">
              {{ overallText(activeResult.overallStatus) }}
            </el-tag>
          </div>
          <div class="result-actions">
            <el-button type="primary" :disabled="!activeResult.id" @click="downloadSummary(activeResult)">下载汇总文件</el-button>
            <el-button :disabled="!activeResult.jobId || !activeResult.instanceCode" @click="openLog(activeResult)">查看日志</el-button>
          </div>
        </div>

        <div class="result-meta">
          <span class="meta-item"><span class="meta-label">执行实例</span>{{ activeResult.instanceCode || activeResult.id }}</span>
          <span class="meta-item">
            <span class="meta-label">状态</span>
            <span :class="'status-badge status-' + ((activeResult.status || '').toLowerCase() || '')">{{
              statusText(activeResult.status)
            }}</span>
          </span>
          <span class="meta-item"
            ><span class="meta-label">执行时间</span>{{ formatDate(activeResult.startTime || activeResult.createTime) || '-' }}</span
          >
          <span class="meta-item"
            ><span class="meta-label">检核表</span>{{ activeResult.schemaName ? activeResult.schemaName + '.' : ''
            }}{{ activeResult.tableName }}</span
          >
        </div>

        <div class="summary-grid">
          <div class="summary-card">
            <div class="summary-label">规则总数</div>
            <div class="summary-value">{{ activeResult.totalRules ?? '-' }}</div>
          </div>
          <div class="summary-card summary-card-success">
            <div class="summary-label">通过</div>
            <div class="summary-value">{{ activeResult.passRules ?? '-' }}</div>
          </div>
          <div class="summary-card summary-card-warning">
            <div class="summary-label">告警</div>
            <div class="summary-value">{{ activeResult.warnRules ?? '-' }}</div>
          </div>
          <div class="summary-card" :class="(activeResult.failRules || 0) > 0 ? 'summary-card-danger' : 'summary-card-success'">
            <div class="summary-label">失败</div>
            <div class="summary-value">{{ activeResult.failRules ?? '-' }}</div>
          </div>
          <div class="summary-card">
            <div class="summary-label">检核行数</div>
            <div class="summary-value">{{ activeResult.checkedRows ?? '-' }}</div>
          </div>
        </div>

        <el-alert v-if="activeResult.jobMessage" type="error" :closable="false" :title="activeResult.jobMessage" class="mt-3" />

        <el-table :data="activeResult.rules || []" style="width: 100%" class="mt-3">
          <el-table-column prop="ruleName" label="规则" min-width="180" show-overflow-tooltip />
          <el-table-column label="级别" width="90">
            <template #default="scope">{{ scope.row.ruleLevel === 'TABLE' ? '表级' : '字段级' }}</template>
          </el-table-column>
          <el-table-column prop="columnName" label="字段" min-width="120" show-overflow-tooltip />
          <el-table-column label="无效行 / 检核行" width="150">
            <template #default="scope">{{ scope.row.invalidRows ?? '-' }} / {{ scope.row.checkedRows ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="scope">
              <el-tag :type="ruleTagType(scope.row.status)" effect="plain">{{ ruleStatusText(scope.row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="220" show-overflow-tooltip />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="scope">
              <el-button link type="primary" :disabled="!scope.row.detailUrl" @click="downloadDetail(activeResult, scope.row)"
                >下载明细</el-button
              >
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div v-else class="text-muted">暂无执行结果</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import DataQualityTaskService, {
  type IDataQualityResult,
  type IDataQualityRuleResult,
  type IDataQualityTask,
} from './data-quality-task.service';
import DataSourceService from '@/entities/data-source/data-source.service';

defineOptions({ name: 'DataQualityTask' });

const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

const STATUS_LABELS: Record<string, string> = {
  APPENDING: '排队中',
  RUNNING: '运行中',
  SUCCESSFUL: '成功',
  FAILED: '失败',
  INTERRUPTED: '已停止',
  TIMEOUT: '超时',
  OFFLINE: '离线',
  ONLINE: '在线',
};

const router = useRouter();
const service = new DataQualityTaskService();
const dataSourceService = new DataSourceService();

const tasks = ref<IDataQualityTask[]>([]);
const search = ref('');
const page = ref(1);
const size = ref(20);
const total = ref(0);
const isFetching = ref(false);
const executingId = ref<string | null>(null);
const dataSourceNames = ref<Record<string, string>>({});

const resultVisible = ref(false);
const activeTask = ref<IDataQualityTask | null>(null);
const activeResult = ref<IDataQualityResult | null>(null);
let pollTimer: number | null = null;

const statusText = (status?: string | null) => (status ? (STATUS_LABELS[status] ?? status) : '-');
const overallText = (status?: string | null) =>
  status === 'PASS' ? '通过' : status === 'WARN' ? '告警' : status === 'FAIL' ? '不通过' : '-';
const overallTagType = (status?: string | null) =>
  status === 'PASS' ? 'success' : status === 'WARN' ? 'warning' : status === 'FAIL' ? 'danger' : 'info';
const ruleStatusText = (status?: string | null) =>
  status === 'PASS' ? '通过' : status === 'WARN' ? '告警' : status === 'FAIL' ? '失败' : '-';
const ruleTagType = (status?: string | null) =>
  status === 'PASS' ? 'success' : status === 'WARN' ? 'warning' : status === 'FAIL' ? 'danger' : 'info';

const formatDate = (value?: string | number | null): string => {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(
    date.getSeconds(),
  )}`;
};

const dataSourceLabel = (id?: string | null) => (id ? dataSourceNames.value[id] || id : '-');
const isTaskRunning = (task: IDataQualityTask) => task.lastStatus === 'APPENDING' || task.lastStatus === 'RUNNING';

const loadDataSources = async () => {
  try {
    const res = await dataSourceService.retrieve({ page: 0, size: 2000, sort: ['id,asc'] });
    const names: Record<string, string> = {};
    (res.data || []).forEach((item: any) => {
      if (item.id) names[item.id] = item.name || item.id;
    });
    dataSourceNames.value = names;
  } catch {
    // ignore
  }
};

const retrieve = async () => {
  isFetching.value = true;
  try {
    const res = await service.retrieve({
      page: page.value - 1,
      size: size.value,
      sort: ['updateTime,desc'],
      search: search.value || undefined,
    });
    tasks.value = res.data || [];
    total.value = Number(res.headers['x-total-count'] || 0);
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.detail || e?.message || '加载数据质量任务失败');
  } finally {
    isFetching.value = false;
  }
};

const onPageChange = (value: number) => {
  page.value = value;
  retrieve();
};

const onSizeChange = (value: number) => {
  size.value = value;
  page.value = 1;
  retrieve();
};

const goEdit = (task: IDataQualityTask) => {
  router.push({ name: 'DataQualityTaskEdit', params: { id: task.id } });
};

const stopPolling = () => {
  if (pollTimer != null) {
    window.clearInterval(pollTimer);
    pollTimer = null;
  }
};

const pollResult = (resultId: string) => {
  stopPolling();
  pollTimer = window.setInterval(async () => {
    try {
      const result = await service.getResult(resultId);
      activeResult.value = result;
      if (result.status && result.status !== 'APPENDING' && result.status !== 'RUNNING') {
        stopPolling();
        retrieve();
      }
    } catch {
      stopPolling();
    }
  }, 2000);
};

const executeTask = async (task: IDataQualityTask) => {
  if (!task.id) return;
  try {
    await ElMessageBox.confirm(`确定立即执行数据质量任务「${task.taskName}」吗？`, '立即执行', { type: 'warning' });
  } catch {
    return;
  }
  executingId.value = task.id;
  try {
    const result = await service.execute(task.id);
    activeTask.value = task;
    activeResult.value = result;
    resultVisible.value = true;
    if (result.status === 'APPENDING' || result.status === 'RUNNING') {
      pollResult(result.id!);
    }
    ElMessage.success('任务已提交');
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.detail || e?.message || '执行失败');
  } finally {
    executingId.value = null;
  }
};

const openResults = async (task: IDataQualityTask) => {
  activeTask.value = task;
  try {
    if (task.lastResult?.id) {
      activeResult.value = await service.getResult(task.lastResult.id);
    } else {
      const res = await service.getResults(task.id!, 0, 1);
      activeResult.value = res.data?.[0] || null;
      if (activeResult.value?.id) {
        activeResult.value = await service.getResult(activeResult.value.id);
      }
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.detail || e?.message || '加载结果失败');
    activeResult.value = null;
  }
  resultVisible.value = true;
};

const downloadSummary = async (result: IDataQualityResult) => {
  const url = service.getDownloadUrl(result);
  if (!url) {
    ElMessage.warning('结果文件不存在');
    return;
  }
  await service.downloadFile(url, `quality_summary_${result.instanceCode || result.id}.csv`);
};

const downloadDetail = async (result: IDataQualityResult, rule: IDataQualityRuleResult) => {
  const url = service.getDetailUrl(result, rule);
  if (!url) {
    ElMessage.warning('明细文件不存在');
    return;
  }
  await service.downloadFile(url, `${rule.ruleKey || 'rule'}_detail.csv`);
};

const openLog = (result: IDataQualityResult) => {
  if (!result.jobId || !result.instanceCode) return;
  router.push({ name: 'JobInstance', query: { jobId: result.jobId, instanceCode: result.instanceCode } });
};

const deleteTask = async (task: IDataQualityTask) => {
  if (!task.id) return;
  try {
    await ElMessageBox.confirm(`确定删除数据质量任务「${task.taskName}」吗？`, '删除任务', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await service.delete(task.id);
    ElMessage.success('删除成功');
    retrieve();
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.detail || e?.message || '删除失败');
  }
};

onMounted(async () => {
  await loadDataSources();
  await retrieve();
});

onUnmounted(stopPolling);
</script>

<style scoped>
.page-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.page-toolbar-embedded {
  justify-content: flex-end;
  margin-bottom: 12px;
}

.object-cell {
  line-height: 1.5;
}

.object-sub {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.result-summary {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.result-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.result-heading {
  display: flex;
  align-items: center;
  gap: 10px;
}

.result-title {
  font-size: 16px;
  font-weight: 600;
}

.result-actions {
  display: flex;
  gap: 8px;
}

.result-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.meta-label {
  color: var(--el-text-color-secondary);
  margin-right: 6px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 12px;
}

.summary-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 14px;
  background: var(--el-bg-color);
}

.summary-card-success {
  background: var(--el-color-success-light-9);
}

.summary-card-warning {
  background: var(--el-color-warning-light-9);
}

.summary-card-danger {
  background: var(--el-color-danger-light-9);
}

.summary-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-bottom: 8px;
}

.summary-value {
  font-size: 24px;
  font-weight: 600;
}
</style>
