<script setup>
import { ref, onMounted, inject, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { VueFlow, useVueFlow } from '@vue-flow/core';
import { Background } from '@vue-flow/background';
import { MiniMap } from '@vue-flow/minimap';
import { MarkerType } from '@vue-flow/core';
import ETLComponentService from '../etl-component/etl-component.service';
import ETLTaskService from '../etl-task/etl-task.service';
import { useAlertService } from '@/shared/alert/alert.service';

import '@vue-flow/core/dist/style.css';
import '@vue-flow/core/dist/theme-default.css';
import '@vue-flow/controls/dist/style.css';
import '@vue-flow/minimap/dist/style.css';
import '@vue-flow/node-resizer/dist/style.css';

import DuckDBSqlConfig from './component/DuckDBSqlConfig.vue';
import InputNodeConfig from './component/InputNodeConfig.vue';
import OutputNodeConfig from './component/OutputNodeConfig.vue';
import SqlUnitConfig from './component/SqlUnitConfig.vue';
import DuckDBWriteConfig from './component/DuckDBWriteConfig.vue';
import DuckDBRegisterConfig from './component/DuckDBRegisterConfig.vue';
import JavaScriptComponentConfig from './component/JavaScriptComponentConfig.vue';
import GenerateFlowFileConfig from './component/GenerateFlowFileConfig.vue';
import LogFlowFileConfig from './component/LogFlowFileConfig.vue';
import MysqlBinlogInputConfig from './component/MysqlBinlogInputConfig.vue';
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

const loadComponents = async () => {
  try {
    const response = await etlComponentService.retrieve();
    etlComponents.value = response.data;
  } catch (error) {
    console.error('获取 ETL 组件数据失败', error);
  }
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

const componentConfigMap = {
  StreamJdbcInput: InputNodeConfig,
  StreamJdbcOutput: OutputNodeConfig,
  StreamSqlUnit: SqlUnitConfig,
  DuckDBSql: DuckDBSqlConfig,
  DuckDBWrite: DuckDBWriteConfig,
  DuckDBRegister: DuckDBRegisterConfig,
  JavaScriptComponent: JavaScriptComponentConfig,
  GenerateFlowFile: GenerateFlowFileConfig,
  LogFlowFile: LogFlowFileConfig,
  MySQLBinlogInput: MysqlBinlogInputConfig,
};

const retrieveETLTask = async eTLTaskId => {
  try {
    const res = await eTLTaskService().find(eTLTaskId);
    res.updateTime = new Date(res.updateTime);
    res.createTime = new Date(res.createTime);
    eTLTask.value = res;

    const convertedNodes = (res.nodes || []).map(node => ({
      id: node.code.toString(),
      data: { label: node.label, config: JSON.parse(node.config), type: node.type, id: node.id },
      position: { x: parseFloat(node.xAxis), y: parseFloat(node.yAxis) },
      class: 'light',
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

const showScheduleModal = ref(false);
const scheduleEntity = ref(null);
const cronExpression = ref('');

const openScheduleModal = () => {
  cronExpression.value = eTLTask.value.cron || '';
  showScheduleModal.value = true;
  scheduleEntity.value?.show?.();
};

const saveSchedule = () => {
  eTLTask.value.cron = cronExpression.value;
  showScheduleModal.value = false;
  scheduleEntity.value?.hide?.();
};

const cancelSchedule = () => {
  showScheduleModal.value = false;
  scheduleEntity.value?.hide?.();
};

onMounted(async () => {
  await loadComponents();
  await loadTaskData();
});

watch(
  () => route.params.eTLTaskId,
  async () => {
    await loadTaskData();
  },
);

onInit(instance => {
  vueFlowInstance.value = instance;
  instance.fitView();
});

onNodeDoubleClick(event => {
  selectedNode.value = event.node;
  const componentType = event.node.data.type;
  selectedConfigComponent.value = componentConfigMap[componentType];

  if (!selectedConfigComponent.value) {
    console.warn(`未找到 ${componentType} 对应的配置组件`);
    return;
  }
  configEntity.value.show();
  showConfigModal.value = true;
});

const saveNodeConfig = config => {
  const nodeIndex = nodes.value.findIndex(node => node.id === selectedNode.value.id);
  if (nodeIndex !== -1) {
    nodes.value[nodeIndex].data.config = config;
  }
  configEntity.value.hide();
  showConfigModal.value = false;
};

onNodeDragStop(({ event, nodes, node }) => {
  console.log('Node Drag Stop', { event, nodes, node });
});

onConnect(connection => {
  addEdges(connection);
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
    data: { label: `${component.name}`, type: component.code, config: {} },
    position,
    class: 'light',
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
          <label class="field-label">调度表达式</label>
          <span class="form-control cron-display" @click="openScheduleModal" :title="eTLTask.cron || '点击设置调度'">
            {{ eTLTask.cron || '未设置' }}
          </span>
        </div>
      </div>
      <div class="header-actions">
        <button type="button" @click="openScheduleModal" class="btn btn-secondary">调度设置</button>
        <button type="button" @click="saveTask" class="btn btn-primary">保存</button>
        <button type="button" @click="cancelTask" class="btn btn-secondary">返回</button>
      </div>
    </div>
    <div class="main-container">
      <div class="sidebar">
        <div class="sidebar-title">ETL 组件</div>
        <div
          v-for="component in etlComponents"
          :key="component.code"
          class="draggable-component"
          draggable
          @dragstart="onDragStart($event, component)"
        >
          {{ component.name }}
        </div>
      </div>
      <div class="canvas-container" @drop="onDrop" @dragover="onDragOver">
        <VueFlow
          ref="flow"
          v-model:nodes="nodes"
          v-model:edges="edges"
          :class="{ dark }"
          class="basic-flow full-height-vueflow"
          :default-viewport="{ zoom: 1.5 }"
          :min-zoom="0.2"
          :max-zoom="4"
        >
          <Background pattern-color="#aaa" :gap="16" />
          <MiniMap />
        </VueFlow>
      </div>
    </div>
    <b-modal ref="configEntity" id="configEntity">
      <template #modal-title>
        <span data-cy="eTLTaskDeleteDialogHeading">配置节点</span>
      </template>
      <div class="modal-body">
        <component
          :is="selectedConfigComponent"
          :node="selectedNode"
          @save="saveNodeConfig"
          @cancel="closeConfigModal"
          ref="configComponentRef"
        />
      </div>
      <template #modal-footer> </template>
    </b-modal>

    <b-modal ref="scheduleEntity" id="scheduleEntity">
      <template #modal-title>
        <span>调度设置</span>
      </template>
      <div class="modal-body">
        <CronExpressionSelector v-model:value="cronExpression" />
        <div v-if="cronExpression" class="cron-preview">
          <span class="cron-preview-label">当前表达式：</span>
          <code class="cron-preview-value">{{ cronExpression }}</code>
        </div>
      </div>
      <template #modal-footer>
        <button type="button" class="btn btn-secondary" @click="cancelSchedule">取消</button>
        <button type="button" class="btn btn-primary" @click="saveSchedule">确认</button>
      </template>
    </b-modal>
  </div>
</template>

<style scoped>
.design-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.header-form {
  display: flex;
  gap: 16px;
  flex: 1;
  align-items: center;
}

.form-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 6px;
}

.field-label {
  font-size: 13px;
  color: #606266;
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
  color: #606266;
  background-color: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.form-control:focus {
  border-color: #409eff;
}

.cron-display {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #409eff;
  background-color: #ecf5ff;
  border-color: #d9ecff;
  transition:
    background-color 0.2s,
    border-color 0.2s;
}

.cron-display:hover {
  background-color: #d9ecff;
  border-color: #409eff;
}

.header-actions {
  display: flex;
  gap: 8px;
  margin-left: 16px;
}

.main-container {
  display: flex;
  flex-direction: row;
  flex: 1;
  overflow: hidden;
}

.sidebar {
  width: 200px;
  background-color: #f5f7fa;
  padding: 12px;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
}

.sidebar-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
  padding-bottom: 6px;
  border-bottom: 1px solid #e4e7ed;
}

.draggable-component {
  background-color: #fff;
  border: 1px solid #dcdfe6;
  padding: 6px 10px;
  margin-bottom: 4px;
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
