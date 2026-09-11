<script setup>
import { ref, reactive, onMounted, inject, computed, watch, markRaw } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { VueFlow, useVueFlow } from '@vue-flow/core';
import { Background } from '@vue-flow/background';
import { MiniMap } from '@vue-flow/minimap';
import { MarkerType } from '@vue-flow/core';
import ETLComponentService from '../etl-component/etl-component.service';
import ETLTaskService from '../etl-task/etl-task.service';
import ETLTaskNode from './etl-task-node.vue';
import { useAlertService } from '@/shared/alert/alert.service';

import '@vue-flow/core/dist/style.css';
import '@vue-flow/core/dist/theme-default.css';
import '@vue-flow/controls/dist/style.css';
import '@vue-flow/minimap/dist/style.css';
import '@vue-flow/node-resizer/dist/style.css';

import CronExpressionSelector from '@/components/CronExpressionSelector.vue';

const {
  onInit,
  onNodeDragStop,
  onConnect,
  addEdges,
  setViewport,
  toObject,
  addNodes,
  onNodeDoubleClick,
  getNodes,
  getEdges,
  screenToFlowPosition,
  removeNodes,
} = useVueFlow();
const etlComponentService = new ETLComponentService();
const eTLTaskService = inject('eTLTaskService', () => new ETLTaskService());
const alertService = inject('alertService', () => useAlertService(), true);

const etlComponents = ref([]);
const eTLTask = ref({});
const configEntity = ref(null);
const vueFlowInstance = ref(null);

const nodes = ref([]);
const edges = ref([]);

const route = useRoute();
const router = useRouter();

const isCreateMode = computed(() => !route.params.eTLTaskId);

const nodeTypes = { etl: markRaw(ETLTaskNode) };

const componentGroupMap = computed(() => {
  const map = {};
  for (const comp of etlComponents.value) {
    map[comp.code] = comp.group;
  }
  return map;
});

const deleteNode = nodeId => {
  removeNodes([nodeId]);
};

const loadComponents = async () => {
  try {
    const response = await etlComponentService.retrieve({ page: 0, size: 1000 });
    etlComponents.value = response.data;
  } catch (error) {
    console.error('获取 ETL 组件数据失败', error);
  }
};

const expandedGroups = reactive({
  '数据输入': true,
  '数据处理': true,
  '数据输出': true,
});

const groupOrder = ['数据输入', '数据输出', '数据处理', '实时输入', '调试组件', 'DuckDB 组件', '其他'];

const componentSearch = ref('');

const groupedComponents = computed(() => {
  const keyword = componentSearch.value.trim().toLowerCase();
  const map = {};
  for (const comp of etlComponents.value) {
    if (
      keyword &&
      !String(comp.name || '')
        .toLowerCase()
        .includes(keyword) &&
      !String(comp.code || '')
        .toLowerCase()
        .includes(keyword)
    ) {
      continue;
    }
    const g = comp.group || '其他';
    if (!map[g]) {
      map[g] = [];
    }
    map[g].push(comp);
  }
  const sortedGroups = Object.keys(map).sort((a, b) => {
    const ia = groupOrder.indexOf(a);
    const ib = groupOrder.indexOf(b);
    if (ia === -1 && ib === -1) return a.localeCompare(b);
    if (ia === -1) return 1;
    if (ib === -1) return -1;
    return ia - ib;
  });
  return sortedGroups.map(g => ({ group: g, components: map[g] }));
});

const toggleGroup = (group) => {
  expandedGroups[group] = !expandedGroups[group];
};

const isGroupCollapsed = (group) => {
  return !expandedGroups[group];
};

const loadTaskData = async () => {
  if (route.params.eTLTaskId) {
    await retrieveETLTask(route.params.eTLTaskId);
  } else {
    eTLTask.value = { taskName: '', taskDesc: '', cron: '' };
    nodes.value = [];
    edges.value = [];
  }
};

const configModules = import.meta.glob('./component/*Config.vue', { eager: true });

const componentConfigMap = {};
for (const [path, module] of Object.entries(configModules)) {
  const filename = path.split('/').pop().replace(/\.vue$/, '');
  componentConfigMap[filename.replace(/Config$/, '')] = module.default;
}

// SqlUnit 组件复用 StreamSqlUnit 的配置界面
componentConfigMap.SqlUnit = componentConfigMap.StreamSqlUnit;

const retrieveETLTask = async eTLTaskId => {
  try {
    const res = await eTLTaskService().find(eTLTaskId);
    res.updateTime = new Date(res.updateTime);
    res.createTime = new Date(res.createTime);
    eTLTask.value = res;

    const convertedNodes = (res.nodes || []).map(node => ({
      id: node.code.toString(),
      type: 'etl',
      data: {
        label: node.label,
        config: node.config ? JSON.parse(node.config) : {},
        type: node.type,
        id: node.id,
        group: componentGroupMap.value[node.type] || '其他',
        onDelete: deleteNode,
      },
      position: { x: parseFloat(node.xAxis), y: parseFloat(node.yAxis) },
    }));

    const convertedEdges = (res.edges || []).map(edge => ({
      id: edge.code.toString(),
      source: edge.source.toString(),
      target: edge.target.toString(),
      markerEnd: MarkerType.ArrowClosed,
    }));

    nodes.value = convertedNodes;
    edges.value = convertedEdges;
  } catch (error) {
    console.error('获取 ETL 任务数据失败', error);
    alertService.showHttpError(error.response);
  }
};

const dark = ref(false);
const showConfigModal = ref(false);
const selectedNode = ref(null);
const selectedConfigComponent = ref(null);
const editingNodeLabel = ref('');
const currentSavedConfig = ref(null);

onMounted(async () => {
  await loadComponents();
  await loadTaskData();
  fitCanvasView();
});

watch(
  () => route.params.eTLTaskId,
  async () => {
    await loadTaskData();
    fitCanvasView();
  },
);

onInit(instance => {
  vueFlowInstance.value = instance;
});

const fitCanvasView = () => {
  if (vueFlowInstance.value) {
    setTimeout(() => vueFlowInstance.value.fitView({ padding: 0.2 }), 100);
  }
};

onNodeDoubleClick(event => {
  selectedNode.value = event.node;
  editingNodeLabel.value = event.node.data?.label || '';
  currentSavedConfig.value = null;
  const componentType = event.node.data.type;
  selectedConfigComponent.value = componentConfigMap[componentType];

  if (!selectedConfigComponent.value) {
    console.warn(`未找到 ${componentType} 对应的配置组件`);
    return;
  }
  configEntity.value.show();
  showConfigModal.value = true;
});

const handleComponentSave = config => {
  currentSavedConfig.value = config;
  saveNodeConfigWithLabel(config, editingNodeLabel.value);
};

const saveNodeConfig = config => {
  const nodeIndex = nodes.value.findIndex(node => node.id === selectedNode.value.id);
  if (nodeIndex !== -1) {
    nodes.value[nodeIndex].data.config = config;
  }
  configEntity.value.hide();
  showConfigModal.value = false;
};

const saveNodeConfigWithLabel = (config, label) => {
  const nodeIndex = nodes.value.findIndex(node => node.id === selectedNode.value.id);
  if (nodeIndex !== -1) {
    nodes.value[nodeIndex].data.config = config;
    if (label && label.trim()) {
      nodes.value[nodeIndex].data.label = label.trim();
    }
  }
  configEntity.value.hide();
  showConfigModal.value = false;
};

onNodeDragStop(({ event, nodes, node }) => {
  console.log('Node Drag Stop', { event, nodes, node });
});

onConnect(connection => {
  addEdges({
    ...connection,
    markerEnd: { type: MarkerType.ArrowClosed },
  });
});

function updatePos() {
  nodes.value = nodes.value.map(node => {
    return {
      ...node,
      position: {
        x: Math.random() * 400,
        y: Math.random() * 400,
      },
    };
  });
}

function logToObject() {
  console.log(toObject());
}

function resetTransform() {
  setViewport({ x: 0, y: 0, zoom: 1 });
}

function toggleDarkMode() {
  dark.value = !dark.value;
}

const closeConfigModal = () => {
  configEntity.value.hide();
  showConfigModal.value = false;
};

const onDragStart = (event, component) => {
  event.dataTransfer.setData('text/plain', JSON.stringify(component));
};

const onDrop = event => {
  event.preventDefault();
  const componentJson = event.dataTransfer.getData('text/plain');
  const component = JSON.parse(componentJson);

  let position = { x: 0, y: 0 };
  if (vueFlowInstance.value && typeof vueFlowInstance.value.screenToFlowCoordinate === 'function') {
    position = vueFlowInstance.value.screenToFlowCoordinate({ x: event.clientX, y: event.clientY });
  } else {
    const canvasContainer = event.currentTarget;
    const rect = canvasContainer.getBoundingClientRect();
    position = {
      x: event.clientX - rect.left,
      y: event.clientY - rect.top,
    };
  }

  addNodes({
    id: `${Date.now()}`,
    type: 'etl',
    data: {
      label: `${component.name}`,
      type: component.code,
      config: {},
      group: component.group || '其他',
      onDelete: deleteNode,
    },
    position,
  });
};

const onDragOver = event => {
  event.preventDefault();
};

const configComponentRef = ref(null);

const saveNodeConfigFromDialog = () => {
  const componentInstance = configComponentRef.value;
  if (componentInstance && typeof componentInstance.saveConfig === 'function') {
    componentInstance.saveConfig();
  } else {
    console.warn('子组件未提供 saveConfig 方法');
  }
  configEntity.value.hide();
};
const flow = ref(null);

const saveTask = async () => {
  if (!eTLTask.value.taskName || !eTLTask.value.taskName.trim()) {
    alertService.showError('请输入任务名称');
    return;
  }
  try {
    const updatedNodes = nodes.value.map(node => ({
      id: node.data.id ? node.data.id : '',
      label: node.data.label,
      code: node.id,
      type: node.data.type,
      xAxis: node.position.x,
      yAxis: node.position.y,
      config: node.data.config ? JSON.stringify(node.data.config) : '',
    }));

    const updatedEdges = edges.value.map(edge => ({
      code: edge.id,
      source: edge.source,
      target: edge.target,
    }));

    eTLTask.value.nodes = updatedNodes;
    eTLTask.value.edges = updatedEdges;

    if (isCreateMode.value) {
      const res = await eTLTaskService().create(eTLTask.value);
      alertService.showSuccess('任务创建成功');
      router.push({ name: 'ETLTaskDesign', params: { eTLTaskId: res.id } });
    } else {
      await eTLTaskService().update(eTLTask.value);
      alertService.showSuccess('任务保存成功');
    }
  } catch (error) {
    console.error('保存任务失败', error);
    alertService.showHttpError(error.response);
  }
};

const cancelTask = () => {
  router.push({ name: 'ETLTask' });
};
</script>

<template>
  <div class="design-container">
    <div class="header">
      <div class="header-left">
        <router-link :to="{ name: 'ETLTask' }" class="back-link">
          <font-awesome-icon icon="arrow-left" />
          <span>返回</span>
        </router-link>
        <h3 class="header-title">任务设计</h3>
      </div>
      <div class="header-form">
        <div class="form-item">
          <label class="field-label">任务名称</label>
          <input type="text" class="form-control" v-model="eTLTask.taskName" placeholder="请输入任务名称" />
        </div>
        <div class="form-item">
          <label class="field-label">任务描述</label>
          <input type="text" class="form-control" v-model="eTLTask.taskDesc" placeholder="请输入任务描述" />
        </div>
        <div class="form-item">
          <label class="field-label">调度设置</label>
          <div class="cron-field">
            <CronExpressionSelector v-model:value="eTLTask.cron" compact />
          </div>
        </div>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="saveTask">
          <font-awesome-icon icon="save" class="mr-1" />
          <span>保存</span>
        </el-button>
      </div>
    </div>
    <div class="main-container">
      <div class="sidebar">
        <div class="sidebar-search">
          <input type="text" class="search-input" v-model="componentSearch" placeholder="搜索组件名称 / code" />
        </div>
        <div v-if="groupedComponents.length === 0 && componentSearch" class="sidebar-empty">无匹配组件</div>
        <div class="sidebar-tree">
          <div v-for="groupData in groupedComponents" :key="groupData.group" class="sidebar-group">
            <div class="sidebar-group-header" @click="toggleGroup(groupData.group)">
              <span class="sidebar-group-arrow" :class="{ collapsed: isGroupCollapsed(groupData.group) }">&#9662;</span>
              <span class="sidebar-group-name">{{ groupData.group }}</span>
            </div>
            <div class="sidebar-group-items" v-show="!isGroupCollapsed(groupData.group)">
              <div
                v-for="component in groupData.components"
                :key="component.code"
                class="draggable-component"
                draggable
                @dragstart="onDragStart($event, component)"
              >
                {{ component.name }}
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
          :class="{ dark }"
          class="basic-flow full-height-vueflow"
          :default-viewport="{ zoom: 1.5 }"
          :min-zoom="0.2"
          :max-zoom="1.2"
        >
          <Background pattern-color="#aaa" :gap="16" />
          <MiniMap />
        </VueFlow>
      </div>
    </div>
    <b-modal ref="configEntity" id="configEntity" class="config-modal" size="lg">
      <template #modal-title>
        <div class="config-modal-title">
          <span class="config-modal-title-label">节点名称: </span>
          <input
            type="text"
            class="config-node-name-input"
            v-model="editingNodeLabel"
            placeholder="请输入节点名称"
            @keyup.enter="saveNodeConfigWithLabel(currentSavedConfig, editingNodeLabel)"
          />
        </div>
      </template>
      <div class="modal-body config-modal-body">
        <component
          :is="selectedConfigComponent"
          :node="selectedNode"
          @save="handleComponentSave"
          @cancel="closeConfigModal"
          ref="configComponentRef"
        />
      </div>
      <template #modal-footer> </template>
    </b-modal>

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
}

.task-name {
  font-weight: normal;
  color: var(--el-text-color-regular, #606266);
  font-size: 14px;
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
  width: 200px;
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
  transition:
    background-color 0.2s,
    border-color 0.2s;
}

.cron-display:hover {
  background-color: var(--el-color-light-7, #d9ecff);
  border-color: var(--el-color-primary, #409eff);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
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
  width: 240px;
  background-color: #f5f7fa;
  padding: 12px;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
}

.sidebar-search {
  margin-bottom: 8px;
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
  padding: 12px 8px;
  font-size: 13px;
  color: #909399;
  text-align: center;
}

.sidebar-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
  padding-bottom: 6px;
  border-bottom: 1px solid #e4e7ed;
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
  padding: 6px 10px;
  cursor: pointer;
  background-color: #f0f2f5;
  user-select: none;
  transition: background-color 0.2s;
}

.sidebar-group-header:hover {
  background-color: #e6e8eb;
}

.sidebar-group-arrow {
  display: inline-block;
  width: 14px;
  font-size: 10px;
  color: #606266;
  transition: transform 0.2s;
}

.sidebar-group-arrow.collapsed {
  transform: rotate(-90deg);
}

.sidebar-group-name {
  flex: 1;
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
  background-color: #fff;
  border: 1px solid #dcdfe6;
  padding: 5px 8px;
  cursor: grab;
  border-radius: 4px;
  font-size: 13px;
  color: #303133;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}

.draggable-component:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
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

.vue-flow__minimap {
  transform: scale(75%);
  transform-origin: bottom right;
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
</style>