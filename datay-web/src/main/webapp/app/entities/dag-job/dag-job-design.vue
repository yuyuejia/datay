<script setup>
import { computed, markRaw, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Background } from "@vue-flow/background";
import { Controls } from "@vue-flow/controls";
import { MiniMap } from "@vue-flow/minimap";
import { MarkerType, VueFlow, useVueFlow } from "@vue-flow/core";
import { useAlertService } from "@/shared/alert/alert.service";
import CronExpressionSelector from "@/components/CronExpressionSelector.vue";
import DagJobService from "./dag-job.service";
import JobNode from "./dag-job-node.vue";

import "@vue-flow/core/dist/style.css";
import "@vue-flow/core/dist/theme-default.css";
import "@vue-flow/controls/dist/style.css";
import "@vue-flow/minimap/dist/style.css";
import "@vue-flow/node-resizer/dist/style.css";

const {
  onInit,
  onConnect,
  onNodeDoubleClick,
  addEdges,
  addNodes,
  removeNodes,
} = useVueFlow();

const dagJobService = new DagJobService();
const alertService = useAlertService();

const route = useRoute();
const router = useRouter();

const isEditMode = computed(() => !!route.params.jobId);

const dagJob = ref({ jobName: "", cron: "" });
const nodes = ref([]);
const edges = ref([]);
const vueFlowInstance = ref(null);
const candidates = ref([]);
const searchKeyword = ref("");
const isCandidateLoading = ref(false);

const nodeTypes = { job: markRaw(JobNode) };

const typeOrder = ["ETL", "SQL", "SHELL", "SPARK", "FLINK", "DEMO"];
const typeLabels = {
  ETL: "ETL 集成任务",
  SQL: "SQL 任务",
  SHELL: "Shell 脚本任务",
  SPARK: "Spark 任务",
  FLINK: "Flink 任务",
  DEMO: "演示任务",
};

const candidateMap = computed(() => {
  const map = {};
  for (const job of candidates.value) {
    map[String(job.id)] = job;
  }
  return map;
});

const groupedCandidates = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase();
  const map = {};
  for (const job of candidates.value) {
    if (
      keyword &&
      !String(job.jobName || "")
        .toLowerCase()
        .includes(keyword) &&
      !String(job.id).toLowerCase().includes(keyword)
    ) {
      continue;
    }
    const g = job.type || "OTHER";
    if (!map[g]) {
      map[g] = [];
    }
    map[g].push(job);
  }
  const sortedGroups = Object.keys(map).sort((a, b) => {
    const ia = typeOrder.indexOf(a);
    const ib = typeOrder.indexOf(b);
    if (ia === -1 && ib === -1) return a.localeCompare(b);
    if (ia === -1) return 1;
    if (ib === -1) return -1;
    return ia - ib;
  });
  return sortedGroups.map((group) => ({
    type: group,
    label: typeLabels[group] || `${group} 任务`,
    jobs: map[group],
  }));
});

const loadCandidates = async () => {
  isCandidateLoading.value = true;
  try {
    const data = await dagJobService.retrieveCandidates();
    candidates.value = data || [];
  } catch (error) {
    console.error("获取可选任务失败", error);
    alertService.showHttpError(error.response);
  } finally {
    isCandidateLoading.value = false;
  }
};

const buildNodeFromJob = (job, position, layout) => ({
  id: String(job.id),
  type: "job",
  position: position || {
    x: Math.round(Math.random() * 400),
    y: Math.round(Math.random() * 300),
  },
  data: {
    jobId: String(job.id),
    label: layout && layout.label ? layout.label : job.jobName,
    jobType: job.type || "JOB",
    status: job.status || "OFFLINE",
    cron: job.cron || "",
    onDelete: deleteNode,
  },
});

const parseContextToGraph = (jobContext) => {
  if (!jobContext) {
    return;
  }
  let flow = null;
  try {
    flow = typeof jobContext === "string" ? JSON.parse(jobContext) : jobContext;
  } catch (error) {
    console.error("解析编排内容失败", error);
    alertService.showError("编排内容解析失败，可能已被其他方式修改");
    return;
  }
  const layoutMap = {};
  for (const item of flow.nodeLayout || []) {
    layoutMap[String(item.jobId)] = item;
  }
  const nodeList = (flow.jobs || []).map((jobRef) => {
    const layout = layoutMap[String(jobRef.id)];
    const candidate = candidateMap.value[String(jobRef.id)] || {
      id: jobRef.id,
    };
    const proxy = { ...candidate, id: jobRef.id };
    return buildNodeFromJob(
      proxy,
      layout
        ? { x: Number(layout.xAxis) || 0, y: Number(layout.yAxis) || 0 }
        : null,
      layout,
    );
  });
  const edgeList = (flow.jobDepends || [])
    .map((dep) => ({
      id: `${dep.parentJobCode}->${dep.childJobCode}`,
      source: String(dep.parentJobCode),
      target: String(dep.childJobCode),
      markerEnd: { type: MarkerType.ArrowClosed },
    }))
    .filter(
      (edge) =>
        nodeList.some((node) => node.id === edge.source) &&
        nodeList.some((node) => node.id === edge.target) &&
        edge.source !== edge.target,
    );
  nodes.value = nodeList;
  edges.value = edgeList;
};

const loadDagJob = async () => {
  if (!route.params.jobId) {
    dagJob.value = { jobName: "", cron: "" };
    nodes.value = [];
    edges.value = [];
    return;
  }
  try {
    const res = await dagJobService.find(route.params.jobId);
    dagJob.value = res;
    parseContextToGraph(res.jobContext);
  } catch (error) {
    console.error("获取编排任务失败", error);
    alertService.showHttpError(error.response);
  }
};

onMounted(async () => {
  await loadCandidates();
  await loadDagJob();
  if (vueFlowInstance.value) {
    setTimeout(() => vueFlowInstance.value.fitView({ padding: 0.2 }), 100);
  }
});

watch(
  () => route.params.jobId,
  async () => {
    await loadDagJob();
  },
);

onInit((instance) => {
  vueFlowInstance.value = instance;
});

const onDragStart = (event, job) => {
  event.dataTransfer.setData("text/plain", JSON.stringify(job));
  event.dataTransfer.effectAllowed = "move";
};

const onDragOver = (event) => {
  event.preventDefault();
  event.dataTransfer.dropEffect = "move";
};

const onDrop = (event) => {
  event.preventDefault();
  const jobJson = event.dataTransfer.getData("text/plain");
  if (!jobJson) {
    return;
  }
  let job = null;
  try {
    job = JSON.parse(jobJson);
  } catch (error) {
    console.error("拖拽数据解析失败", error);
    return;
  }
  if (nodes.value.some((node) => node.id === String(job.id))) {
    alertService.showError(`任务「${job.jobName}」已在画布中`);
    return;
  }
  let position = { x: 0, y: 0 };
  if (
    vueFlowInstance.value &&
    typeof vueFlowInstance.value.screenToFlowCoordinate === "function"
  ) {
    position = vueFlowInstance.value.screenToFlowCoordinate({
      x: event.clientX,
      y: event.clientY,
    });
  } else {
    const rect = event.currentTarget.getBoundingClientRect();
    position = { x: event.clientX - rect.left, y: event.clientY - rect.top };
  }
  addNodes(buildNodeFromJob(job, position));
};

const hasCycle = (nodeList, edgeList) => {
  const adj = {};
  const indegree = {};
  for (const node of nodeList) {
    adj[node.id] = [];
    indegree[node.id] = 0;
  }
  for (const edge of edgeList) {
    if (!adj[edge.source] || !adj[edge.target]) {
      continue;
    }
    adj[edge.source].push(edge.target);
    indegree[edge.target]++;
  }
  const queue = nodeList
    .filter((node) => indegree[node.id] === 0)
    .map((node) => node.id);
  let visited = 0;
  while (queue.length) {
    const current = queue.shift();
    visited++;
    for (const next of adj[current] || []) {
      indegree[next]--;
      if (indegree[next] === 0) {
        queue.push(next);
      }
    }
  }
  return visited < nodeList.length;
};

onConnect((connection) => {
  if (
    !connection.source ||
    !connection.target ||
    connection.source === connection.target
  ) {
    return;
  }
  if (
    edges.value.some(
      (edge) =>
        edge.source === connection.source && edge.target === connection.target,
    )
  ) {
    alertService.showError("该依赖关系已存在");
    return;
  }
  const tentative = [
    ...edges.value,
    { source: connection.source, target: connection.target },
  ];
  if (hasCycle(nodes.value, tentative)) {
    alertService.showError("连线会形成环，DAG 不允许存在循环依赖");
    return;
  }
  addEdges({
    ...connection,
    id: `${connection.source}->${connection.target}`,
    markerEnd: { type: MarkerType.ArrowClosed },
  });
});

const deleteNode = (nodeId) => {
  removeNodes([nodeId]);
};

onNodeDoubleClick((event) => {
  showJobDetail(event.node);
});

const detailEntity = ref(null);
const detailJob = ref(null);
const isDetailLoading = ref(false);

const showJobDetail = async (node) => {
  const jobId = node.data && node.data.jobId;
  if (!jobId) {
    return;
  }
  isDetailLoading.value = true;
  detailJob.value = null;
  try {
    const res = await dagJobService.find(jobId);
    detailJob.value = res;
  } catch (error) {
    console.error("获取任务详情失败", error);
    alertService.showHttpError(error.response);
  } finally {
    isDetailLoading.value = false;
    detailEntity.value.show();
  }
};

const closeDetail = () => {
  detailEntity.value.hide();
  detailJob.value = null;
};

const buildJobContext = () => {
  const jobs = nodes.value.map((node) => ({ id: String(node.id) }));
  const jobDepends = edges.value.map((edge) => ({
    parentJobCode: String(edge.source),
    childJobCode: String(edge.target),
    lastInterval: 0,
  }));
  const nodeLayout = nodes.value.map((node) => ({
    jobId: String(node.id),
    label: node.data.label,
    xAxis: node.position.x,
    yAxis: node.position.y,
  }));
  return JSON.stringify({ jobs, jobDepends, nodeLayout });
};

const saveDagJob = async () => {
  if (!dagJob.value.jobName || !dagJob.value.jobName.trim()) {
    alertService.showError("请输入编排名称");
    return;
  }
  if (nodes.value.length === 0) {
    alertService.showError("请至少拖入一个任务节点");
    return;
  }
  if (hasCycle(nodes.value, edges.value)) {
    alertService.showError("当前流程存在环，DAG 不允许循环依赖");
    return;
  }
  const jobContext = buildJobContext();
  try {
    if (isEditMode.value) {
      const entity = {
        ...dagJob.value,
        jobName: dagJob.value.jobName.trim(),
        cron: dagJob.value.cron || "",
        type: "DAG",
        jobContext,
        updateTime: new Date(),
      };
      await dagJobService.update(entity);
      alertService.showSuccess("编排保存成功");
    } else {
      const entity = {
        jobName: dagJob.value.jobName.trim(),
        jobGroup: "datafusion",
        type: "DAG",
        cron: dagJob.value.cron || "",
        status: "OFFLINE",
        jobContext,
        createTime: new Date(),
        updateTime: new Date(),
      };
      const res = await dagJobService.create(entity);
      alertService.showSuccess("编排创建成功");
      router.push({ name: "DagJobDesign", params: { jobId: res.id } });
    }
  } catch (error) {
    console.error("保存编排失败", error);
    alertService.showHttpError(error.response);
  }
};

</script>

<template>
  <div class="design-container">
    <div class="header">
      <div class="header-left">
        <router-link :to="{ name: 'DagJob' }" class="back-link">
          <font-awesome-icon icon="arrow-left" />
          <span>返回</span>
        </router-link>
        <h3 class="header-title">任务编排设计</h3>
      </div>
      <div class="header-form">
        <div class="form-item">
          <label class="field-label">编排名称</label>
          <input
            type="text"
            class="form-control"
            v-model="dagJob.jobName"
            placeholder="请输入编排名称"
          />
        </div>
        <div class="form-item">
          <label class="field-label">调度设置</label>
          <div class="cron-field">
            <CronExpressionSelector v-model:value="dagJob.cron" compact />
          </div>
        </div>
      </div>
      <div class="header-actions">
        <span class="design-tip"
          >从左侧拖入任务并连线即构成依赖（A → B 表示 A 完成后执行 B）</span
        >
        <el-button type="primary" @click="saveDagJob">
          <font-awesome-icon icon="save" class="mr-1" />
          <span>保存</span>
        </el-button>
      </div>
    </div>
    <div class="main-container">
      <div class="sidebar">
        <div class="sidebar-search">
          <input
            type="text"
            class="search-input"
            v-model="searchKeyword"
            placeholder="搜索任务名称 / ID"
          />
        </div>
        <div v-if="isCandidateLoading" class="text-center text-muted p-2">
          <font-awesome-icon icon="spinner" spin></font-awesome-icon>
          <span>加载任务...</span>
        </div>
        <div v-else-if="groupedCandidates.length === 0" class="sidebar-empty">
          {{
            searchKeyword
              ? "无匹配任务"
              : "暂无可编排任务，请先在「任务定义」中创建任务"
          }}
        </div>
        <div v-else class="sidebar-tree">
          <div
            v-for="group in groupedCandidates"
            :key="group.type"
            class="sidebar-group"
          >
            <div class="sidebar-group-header">
              <span class="sidebar-group-name">{{ group.label }}</span>
              <span class="sidebar-group-count">{{ group.jobs.length }}</span>
            </div>
            <div class="sidebar-group-items">
              <div
                v-for="job in group.jobs"
                :key="job.id"
                class="draggable-component"
                :title="`${job.jobName} (#${job.id}) · ${job.cron || '无调度'}`"
                :draggable="true"
                @dragstart="onDragStart($event, job)"
              >
                {{ job.jobName }}
                <span class="job-id">#{{ job.id }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="canvas-container" @drop="onDrop" @dragover="onDragOver">
        <VueFlow
          ref="flow"
          v-model:nodes="nodes"
          v-model:edges="edges"
          :node-types="nodeTypes"
          :default-viewport="{ zoom: 0.9 }"
          :min-zoom="0.2"
          :max-zoom="1.2"
          :delete-key-code="['Backspace', 'Delete']"
          class="basic-flow full-height-vueflow"
        >
          <Background pattern-color="#aaa" :gap="16" />
          <MiniMap />
          <Controls />
        </VueFlow>
        <div v-if="nodes.length === 0" class="canvas-empty-tip">
          从左侧拖拽任务到此处，拖拽任务间连线设置执行依赖
        </div>
      </div>
    </div>

    <app-modal ref="detailEntity" id="detailEntity" size="lg" scrollable>
      <template #modal-title>
        <span>任务详情 - {{ detailJob?.jobName }}</span>
      </template>
      <div class="modal-body">
        <div v-if="isDetailLoading" class="text-center">
          <font-awesome-icon icon="spinner" spin></font-awesome-icon>
          <span>加载中...</span>
        </div>
        <template v-else-if="detailJob">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="ID">{{
              detailJob.id
            }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{
              detailJob.type
            }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{
              detailJob.status || "OFFLINE"
            }}</el-descriptions-item>
            <el-descriptions-item label="Cron">{{
              detailJob.cron || "未设置"
            }}</el-descriptions-item>
          </el-descriptions>
          <div class="detail-context">
            <label class="detail-context-label">任务配置：</label>
            <pre class="detail-context-pre">{{
              detailJob.jobContext || "无"
            }}</pre>
          </div>
        </template>
      </div>
      <template #modal-footer>
        <el-button @click="closeDetail">
          关闭
        </el-button>
      </template>
    </app-modal>
  </div>
</template>

<style scoped>
.design-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 60px);
  background: var(--el-bg-color, #fff);
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
  background: var(--el-bg-color, #fff);
  flex-shrink: 0;
  gap: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-link {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--el-text-color-regular, #606266);
  text-decoration: none;
  cursor: pointer;
  font-size: 14px;
}

.back-link:hover {
  color: var(--el-color-primary, #409eff);
}

.header-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
  white-space: nowrap;
}

.header-form {
  display: flex;
  gap: 16px;
  flex: 1;
  align-items: center;
  justify-content: center;
}

.form-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 6px;
}

.field-label {
  font-size: 13px;
  color: var(--el-text-color-regular, #606266);
  white-space: nowrap;
  font-weight: 500;
  height: 28px;
  line-height: 28px;
  margin: 0;
}

.form-control {
  width: 220px;
  height: 28px;
  padding: 0 8px;
  font-size: 13px;
  line-height: 28px;
  color: var(--el-text-color-regular, #606266);
  background-color: var(--el-bg-color, #fff);
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 4px;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.form-control:focus {
  border-color: var(--el-color-primary, #409eff);
}

.cron-field {
  width: 320px;
}

.cron-display {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--el-color-primary, #409eff);
  background-color: var(--el-color-light-9, #ecf5ff);
  border-color: var(--el-color-light-5, #d9ecff);
}

.cron-display:hover {
  background-color: var(--el-color-light-7, #d9ecff);
  border-color: var(--el-color-primary, #409eff);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.design-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  white-space: nowrap;
}

.mr-1 {
  margin-right: 4px;
}

.main-container {
  display: flex;
  flex-direction: row;
  flex: 1;
  overflow: hidden;
}

.sidebar {
  width: 260px;
  background-color: #f5f7fa;
  padding: 12px;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sidebar-search .search-input {
  width: 100%;
  height: 30px;
  padding: 0 8px;
  font-size: 13px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  outline: none;
  box-sizing: border-box;
}

.sidebar-search .search-input:focus {
  border-color: #409eff;
}

.sidebar-empty {
  padding: 16px 8px;
  font-size: 13px;
  color: #909399;
  text-align: center;
}

.sidebar-tree {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sidebar-group {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background-color: #fff;
  overflow: hidden;
}

.sidebar-group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  background-color: #f0f2f5;
  user-select: none;
}

.sidebar-group-name {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.sidebar-group-count {
  font-size: 11px;
  color: #909399;
  background-color: #dcdfe6;
  padding: 1px 6px;
  border-radius: 10px;
}

.sidebar-group-items {
  padding: 6px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.draggable-component {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #fff;
  border: 1px solid #dcdfe6;
  padding: 5px 8px;
  cursor: grab;
  border-radius: 4px;
  font-size: 13px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

.draggable-component:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}

.job-id {
  font-size: 11px;
  color: #909399;
}

.canvas-container {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.full-height-vueflow {
  width: 100%;
  height: 100%;
}

.canvas-empty-tip {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #c0c4cc;
  font-size: 14px;
  pointer-events: none;
  user-select: none;
  background: rgba(255, 255, 255, 0.7);
  padding: 12px 20px;
  border-radius: 6px;
  border: 1px dashed #dcdfe6;
}

.cron-preview {
  margin-top: 12px;
  padding: 8px 12px;
  background-color: #f5f7fa;
  border-radius: 4px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.cron-preview-label {
  color: #606266;
}

.cron-preview-value {
  color: #409eff;
  font-family: Menlo, Monaco, Consolas, monospace;
}

.detail-context {
  margin-top: 12px;
}

.detail-context-label {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}

.detail-context-pre {
  margin-top: 6px;
  max-height: 260px;
  overflow: auto;
  background-color: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 10px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>