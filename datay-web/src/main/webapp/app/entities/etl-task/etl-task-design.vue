<script setup>
import { ref, reactive, onMounted, inject, provide, computed, watch, markRaw } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { VueFlow, useVueFlow } from '@vue-flow/core';
import { Background } from '@vue-flow/background';
import { MiniMap } from '@vue-flow/minimap';
import { MarkerType } from '@vue-flow/core';
import ETLComponentService from '../etl-component/etl-component.service';
import ETLTaskService from '../etl-task/etl-task.service';
import ETLTaskNode from './etl-task-node.vue';
import DynamicParameterHelp from './DynamicParameterHelp.vue';
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

let skipNextCanvasFit = false;

const DEBUG_ROW_LIMIT = 100;
const debugResults = ref({});
const debugRunning = ref(false);
// 各节点调试获取到的上游表清单缓存，避免每次打开配置界面都自动执行调试
const upstreamTablesCache = new Map();
const debugError = ref('');
const debugElapsed = ref(0);
const activeConfigTab = ref('config');

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

const deleteNode = (nodeId) => {
  removeNodes([nodeId]);
};

const loadComponents = async () => {
  try {
    etlComponents.value = await etlComponentService.catalog();
  } catch (error) {
    console.error('获取 ETL 组件数据失败', error);
  }
};

const expandedGroups = reactive({
  数据输入: true,
  数据处理: true,
  数据输出: true,
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
  return sortedGroups.map((g) => ({ group: g, components: map[g] }));
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
  const filename = path
    .split('/')
    .pop()
    .replace(/\.vue$/, '');
  componentConfigMap[filename.replace(/Config$/, '')] = module.default;
}

// SqlUnit 组件复用 StreamSqlUnit 的配置界面
componentConfigMap.SqlUnit = componentConfigMap.StreamSqlUnit;

const retrieveETLTask = async (eTLTaskId) => {
  try {
    const res = await eTLTaskService().find(eTLTaskId);
    res.updateTime = new Date(res.updateTime);
    res.createTime = new Date(res.createTime);
    eTLTask.value = res;

    const convertedNodes = (res.nodes || []).map((node) => ({
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

    const convertedEdges = (res.edges || []).map((edge) => ({
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
    if (skipNextCanvasFit) {
      skipNextCanvasFit = false;
      return;
    }
    fitCanvasView();
  },
);

onInit((instance) => {
  vueFlowInstance.value = instance;
});

const fitCanvasView = () => {
  if (vueFlowInstance.value) {
    setTimeout(() => vueFlowInstance.value.fitView({ padding: 0.2 }), 100);
  }
};

onNodeDoubleClick((event) => {
  selectedNode.value = event.node;
  editingNodeLabel.value = event.node.data?.label || '';
  currentSavedConfig.value = null;
  activeConfigTab.value = 'config';
  debugError.value = '';
  const componentType = event.node.data.type;
  selectedConfigComponent.value = componentConfigMap[componentType];

  if (!selectedConfigComponent.value) {
    console.warn(`未找到 ${componentType} 对应的配置组件`);
    return;
  }
  configEntity.value.show();
  showConfigModal.value = true;
});

const isApplyingConfig = ref(false);
let appliedConfigForDebug = false;

const applyConfigToSelectedNode = (config) => {
  const nodeIndex = nodes.value.findIndex((node) => node.id === selectedNode.value.id);
  if (nodeIndex !== -1) {
    nodes.value[nodeIndex].data.config = config;
    if (editingNodeLabel.value && editingNodeLabel.value.trim()) {
      nodes.value[nodeIndex].data.label = editingNodeLabel.value.trim();
    }
  }
};

const handleComponentSave = (config) => {
  currentSavedConfig.value = config;
  applyConfigToSelectedNode(config);
  if (isApplyingConfig.value) {
    appliedConfigForDebug = true;
    return;
  }
  configEntity.value.hide();
  showConfigModal.value = false;
};

const saveNodeConfig = (config) => {
  const nodeIndex = nodes.value.findIndex((node) => node.id === selectedNode.value.id);
  if (nodeIndex !== -1) {
    nodes.value[nodeIndex].data.config = config;
  }
  configEntity.value.hide();
  showConfigModal.value = false;
};

const saveNodeConfigWithLabel = (config, label) => {
  const nodeIndex = nodes.value.findIndex((node) => node.id === selectedNode.value.id);
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

onConnect((connection) => {
  addEdges({
    ...connection,
    markerEnd: { type: MarkerType.ArrowClosed },
  });
});

function updatePos() {
  nodes.value = nodes.value.map((node) => {
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

const onDrop = (event) => {
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

const onDragOver = (event) => {
  event.preventDefault();
};

const configComponentRef = ref(null);

const handleConfigSave = async () => {
  const componentInstance = configComponentRef.value;
  if (componentInstance && typeof componentInstance.saveConfig === 'function') {
    await componentInstance.saveConfig();
  } else {
    console.warn('子组件未提供 saveConfig 方法');
  }
};
const flow = ref(null);

const buildTaskPayload = () => {
  const updatedNodes = nodes.value.map((node) => ({
    id: node.data.id ? node.data.id : '',
    label: node.data.label,
    code: node.id,
    type: node.data.type,
    xAxis: node.position.x,
    yAxis: node.position.y,
    config: node.data.config ? JSON.stringify(node.data.config) : '',
  }));

  const updatedEdges = edges.value.map((edge) => ({
    code: edge.id,
    source: edge.source,
    target: edge.target,
  }));

  return { ...eTLTask.value, nodes: updatedNodes, edges: updatedEdges };
};

const runDebug = async (targetNodeId) => {
  if (debugRunning.value) {
    return;
  }
  debugRunning.value = true;
  debugError.value = '';
  try {
    const payload = buildTaskPayload();
    const res = await eTLTaskService().debug(payload, DEBUG_ROW_LIMIT, targetNodeId || null);
    if (res.error) {
      // 调试失败时保留上一次成功的结果，仅展示错误信息
      debugError.value = res.error;
      return;
    }
    debugElapsed.value = res.elapsedMs || 0;
    debugResults.value = res.nodes || {};
    debugError.value = '';
  } catch (error) {
    console.error('调试运行失败', error);
    debugError.value = error?.response?.data?.message || error?.message || '调试运行失败';
  } finally {
    debugRunning.value = false;
  }
};

/**
 * 从调试结果中收集上游组件输出的表清单（含字段）。
 * 物化到 DuckDB 的组件从 tables 中取表名与字段，流式组件从 _table / _tableMetadata 属性与采样列中解析。
 */
const parseMetadataTableName = (meta) => {
  const text = String(meta).trim();
  const bracketIndex = text.indexOf(' [');
  let head = bracketIndex >= 0 ? text.substring(0, bracketIndex) : text;
  const parenIndex = head.indexOf(' (');
  if (parenIndex >= 0) {
    head = head.substring(0, parenIndex);
  }
  return head.includes('.') ? head.substring(head.lastIndexOf('.') + 1) : head;
};

/**
 * 从 _tableMetadata 属性字符串中解析字段名与类型。格式：schema.table [dbType] (col:type, col:type, ...)
 * 类型可能含括号与逗号（如 DECIMAL(10,2)），按括号嵌套层级切分。
 */
const parseMetadataColumns = (meta) => {
  const text = String(meta);
  const open = text.indexOf('(');
  const close = text.lastIndexOf(')');
  if (open < 0 || close <= open) {
    return [];
  }
  const inner = text.substring(open + 1, close);
  const parts = [];
  let depth = 0;
  let current = '';
  for (const char of inner) {
    if (char === '(') {
      depth++;
    } else if (char === ')') {
      depth--;
    }
    if (char === ',' && depth === 0) {
      parts.push(current);
      current = '';
    } else {
      current += char;
    }
  }
  parts.push(current);
  return parts
    .map((part) => {
      const trimmed = part.trim();
      const colon = trimmed.indexOf(':');
      if (colon < 0) {
        return { name: trimmed, type: '' };
      }
      return { name: trimmed.substring(0, colon).trim(), type: trimmed.substring(colon + 1).trim() };
    })
    .filter((item) => item.name);
};

const collectUpstreamTables = (debugMap) => {
  // name -> Map<字段名, 字段类型>
  const tableMap = new Map();
  const ensureTable = (name) => {
    const key = (name || '').toString().trim();
    if (!key) {
      return null;
    }
    if (!tableMap.has(key)) {
      tableMap.set(key, new Map());
    }
    return tableMap.get(key);
  };
  const putColumn = (columns, name, type) => {
    if (!columns || !name) {
      return;
    }
    if (!columns.has(name) || (!columns.get(name) && type)) {
      columns.set(name, type || '');
    }
  };

  Object.values(debugMap || {}).forEach((result) => {
    if (!result) {
      return;
    }
    const tables = result.tables || [];
    if (tables.length > 0) {
      tables.forEach((table) => {
        if (!table || !table.tableName) {
          return;
        }
        const columns = ensureTable(table.tableName);
        if (!columns) {
          return;
        }
        const names = table.columns || [];
        const types = table.columnTypes || [];
        names.forEach((column, index) => putColumn(columns, column, types[index]));
      });
      return;
    }
    const attributes = result.attributes || {};
    let tableName = attributes['_table'] ? String(attributes['_table']).trim() : '';
    if (!tableName && attributes['_tableMetadata']) {
      tableName = parseMetadataTableName(attributes['_tableMetadata']);
    }
    const columns = ensureTable(tableName);
    if (!columns) {
      return;
    }
    let fields = attributes['_tableMetadata'] ? parseMetadataColumns(attributes['_tableMetadata']) : [];
    if (fields.length === 0) {
      fields = (result.columns || []).map((column) => ({ name: column, type: '' }));
    }
    fields.forEach((field) => putColumn(columns, field.name, field.type));
  });

  return Array.from(tableMap.entries())
    .map(([name, columnMap]) => {
      const columns = Array.from(columnMap.keys()).sort();
      const columnTypes = {};
      columns.forEach((column) => {
        columnTypes[column] = columnMap.get(column) || '';
      });
      return { name, columns, columnTypes };
    })
    .sort((a, b) => a.name.localeCompare(b.name));
};

/**
 * 调试当前节点的直接上游组件，返回上游输出的表清单。
 * 供下游组件（如 Join）配置时以下拉方式选择表名。
 */
const debugUpstreamTables = async (nodeId, force = false) => {
  if (!nodeId) {
    return { tables: [], error: '节点信息为空' };
  }
  // 已有字段信息时直接返回缓存，不再自动执行调试；force=true 时忽略缓存重新获取
  if (!force && upstreamTablesCache.has(nodeId)) {
    return { tables: upstreamTablesCache.get(nodeId), error: '' };
  }
  if (debugRunning.value) {
    return { tables: [], error: '正在调试中，请稍候再试' };
  }
  const upstreamIds = edges.value.filter((edge) => edge.target === nodeId).map((edge) => edge.source);
  if (upstreamIds.length === 0) {
    return { tables: [], error: '当前节点没有上游组件，无法获取表清单' };
  }
  debugRunning.value = true;
  const merged = {};
  const errors = [];
  try {
    for (const upstreamId of upstreamIds) {
      try {
        const payload = buildTaskPayload();
        const res = await eTLTaskService().debug(payload, DEBUG_ROW_LIMIT, upstreamId);
        if (res && res.error) {
          errors.push(res.error);
        }
        if (res && res.nodes) {
          Object.assign(merged, res.nodes);
        }
      } catch (error) {
        errors.push(error?.response?.data?.message || error?.message || '调试失败');
      }
    }
    debugResults.value = { ...debugResults.value, ...merged };
    const tables = collectUpstreamTables(merged);
    if (tables.length) {
      upstreamTablesCache.set(nodeId, tables);
    }
    return { tables, error: tables.length === 0 && errors.length > 0 ? errors.join('; ') : '' };
  } finally {
    debugRunning.value = false;
  }
};

/** 传递给大模型的调试采样上限，避免提示词过长。 */
const UPSTREAM_SAMPLE_ROW_LIMIT = 10;
const UPSTREAM_SAMPLE_CELL_LIMIT = 200;
const UPSTREAM_SAMPLE_TABLE_LIMIT = 3;
const UPSTREAM_CONTEXT_CHAR_LIMIT = 20000;

const normalizeSampleCell = (value) => {
  if (value === null || value === undefined) {
    return null;
  }
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value);
    } catch {
      return String(value);
    }
  }
  const text = String(value);
  return text.length > UPSTREAM_SAMPLE_CELL_LIMIT ? `${text.slice(0, UPSTREAM_SAMPLE_CELL_LIMIT)}…` : text;
};

const buildUpstreamSample = (upstreamId, node, result) => {
  const sample = { node: node?.data?.label || upstreamId };
  const attributes = result.attributes || {};
  const attrKeys = Object.keys(attributes);
  if (attrKeys.length) {
    sample.attributes = {};
    attrKeys.slice(0, UPSTREAM_SAMPLE_ROW_LIMIT).forEach((key) => {
      sample.attributes[key] = normalizeSampleCell(attributes[key]);
    });
  }
  if (Array.isArray(result.tables) && result.tables.length) {
    sample.tables = result.tables.slice(0, UPSTREAM_SAMPLE_TABLE_LIMIT).map((table) => ({
      tableName: table.tableName,
      columns: (table.columns || []).map((name, index) => ({ name, type: (table.columnTypes || [])[index] || '' })),
      rows: (table.rows || []).slice(0, UPSTREAM_SAMPLE_ROW_LIMIT).map((row) => (row || []).map(normalizeSampleCell)),
      truncated: !!table.truncated,
    }));
  } else if (Array.isArray(result.columns) && result.columns.length) {
    sample.columns = result.columns.map((name) => ({ name, type: '' }));
    sample.rows = (result.rows || []).slice(0, UPSTREAM_SAMPLE_ROW_LIMIT).map((row) => (row || []).map(normalizeSampleCell));
    sample.truncated = !!result.truncated;
  }
  return sample;
};

/**
 * 整体超出长度上限时，退化为只保留字段名与类型，去掉样例行，避免提示词过大。
 */
const trimUpstreamContext = (upstream) => {
  if (JSON.stringify(upstream).length <= UPSTREAM_CONTEXT_CHAR_LIMIT) {
    return upstream;
  }
  return upstream.map((entry) => {
    const trimmed = { ...entry };
    if (Array.isArray(trimmed.tables)) {
      trimmed.tables = trimmed.tables.map((table) => {
        const schemaOnly = { ...table };
        delete schemaOnly.rows;
        return schemaOnly;
      });
    }
    delete trimmed.rows;
    return trimmed;
  });
};

/**
 * 获取当前节点直接上游组件的调试采样数据，供下游组件（如 Java 脚本）交给大模型参考。
 * 无有效采样时返回空数组；force=true 时忽略缓存重新调试。
 */
const debugUpstreamData = async (nodeId, force = false) => {
  if (!nodeId) {
    return { upstream: [], error: '节点信息为空' };
  }
  const res = await debugUpstreamTables(nodeId, force);
  const upstreamIds = edges.value.filter((edge) => edge.target === nodeId).map((edge) => edge.source);
  const upstream = [];
  upstreamIds.forEach((upstreamId) => {
    const snapshot = debugResults.value[upstreamId];
    if (!snapshot) {
      return;
    }
    const node = nodes.value.find((n) => n.id === upstreamId);
    upstream.push(buildUpstreamSample(upstreamId, node, snapshot));
  });
  return { upstream: trimUpstreamContext(upstream), error: res.error || '' };
};

provide('etlTaskDesignContext', {
  debugUpstreamTables,
  debugUpstreamData,
  debugRunning,
});

const applyConfigAndRunDebug = async () => {
  if (debugRunning.value) {
    return;
  }
  const nodeId = selectedNode.value && selectedNode.value.id;
  const instance = configComponentRef.value;
  if (instance && typeof instance.saveConfig === 'function') {
    isApplyingConfig.value = true;
    appliedConfigForDebug = false;
    try {
      await instance.saveConfig();
    } catch (error) {
      console.error('应用节点配置失败', error);
      return;
    } finally {
      isApplyingConfig.value = false;
    }
    if (!appliedConfigForDebug) {
      // 配置校验未通过，保留当前调试结果
      return;
    }
  }
  await runDebug(nodeId);
};

const previewBlocks = computed(() => {
  if (!selectedNode.value) {
    return [];
  }
  const nodeId = selectedNode.value.id;
  const blocks = [];
  const upstreamIds = edges.value.filter((edge) => edge.target === nodeId).map((edge) => edge.source);
  for (const upstreamId of upstreamIds) {
    const node = nodes.value.find((n) => n.id === upstreamId);
    blocks.push({
      key: `upstream-${upstreamId}`,
      title: `上游 · ${node?.data?.label || upstreamId}`,
      result: debugResults.value[upstreamId],
    });
  }
  blocks.push({
    key: `self-${nodeId}`,
    title: `本组件输出 · ${selectedNode.value.data?.label || nodeId}`,
    result: debugResults.value[nodeId],
  });
  return blocks;
});

const hasDebugResults = computed(() => Object.keys(debugResults.value || {}).length > 0);

const hasAttributes = (result) => !!result && result.attributes && Object.keys(result.attributes).length > 0;

const formatPreviewCell = (value) => {
  if (value === null || value === undefined) {
    return '';
  }
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value);
    } catch (e) {
      return String(value);
    }
  }
  return String(value);
};

const saveTask = async () => {
  if (!eTLTask.value.taskName || !eTLTask.value.taskName.trim()) {
    alertService.showError('请输入任务名称');
    return;
  }
  try {
    const payload = buildTaskPayload();
    eTLTask.value.nodes = payload.nodes;
    eTLTask.value.edges = payload.edges;

    if (isCreateMode.value) {
      const res = await eTLTaskService().create(eTLTask.value);
      alertService.showSuccess('任务创建成功');
      skipNextCanvasFit = true;
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
        <DynamicParameterHelp />
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
                :title="component.desc"
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
      <div class="config-modal-body">
        <div class="config-tab-header">
          <div class="config-tab-item" :class="{ active: activeConfigTab === 'config' }" @click="activeConfigTab = 'config'">配置</div>
          <div class="config-tab-item" :class="{ active: activeConfigTab === 'debug' }" @click="activeConfigTab = 'debug'">调试</div>
        </div>
        <div v-show="activeConfigTab === 'config'" class="config-tab-panel">
          <component :is="selectedConfigComponent" :node="selectedNode" @save="handleComponentSave" ref="configComponentRef" />
        </div>
        <div v-show="activeConfigTab === 'debug'" class="config-tab-panel">
          <div class="debug-tab-toolbar">
            <div class="debug-tab-hint">
              运行调试将使用当前配置（无需先保存）；默认调试行数为 100 行（不可设置），调试运行时不写入目标库
            </div>
            <div class="debug-tab-actions">
              <el-button type="primary" size="small" :loading="debugRunning" @click="applyConfigAndRunDebug"> 运行调试 </el-button>
            </div>
          </div>
          <div class="debug-tab-status">
            <span v-if="debugRunning" class="debug-preview-running">运行中...</span>
            <span v-else-if="debugElapsed > 0">上次运行耗时 {{ debugElapsed }} ms</span>
          </div>
          <el-alert v-if="debugError" :title="debugError" type="error" :closable="false" show-icon class="debug-preview-error" />
          <div v-if="debugError && hasDebugResults" class="debug-tab-hint">以下为上一次成功调试的数据</div>
          <div v-for="block in previewBlocks" :key="block.key" class="debug-preview-block">
            <div class="debug-preview-block-title">
              <span>{{ block.title }}</span>
              <span v-if="block.result" class="debug-preview-count">
                {{ (block.result.rows || []).length }} 行{{ block.result.truncated ? '（已截断）' : '' }}
              </span>
            </div>
            <template v-if="block.result">
              <div v-if="hasAttributes(block.result)" class="debug-preview-attrs">
                <div class="debug-preview-section-label">关键属性</div>
                <div class="debug-preview-attr-list">
                  <div v-for="(value, key) in block.result.attributes" :key="key" class="debug-preview-attr-item">
                    <span class="debug-preview-attr-key" :title="key">{{ key }}</span>
                    <span class="debug-preview-attr-value" :title="value">{{ value }}</span>
                  </div>
                </div>
              </div>
              <template v-if="block.result.tables && block.result.tables.length">
                <div class="debug-preview-section-label">DuckDB 表元数据</div>
                <div v-for="tbl in block.result.tables" :key="tbl.tableName" class="debug-preview-table-block">
                  <div class="debug-preview-meta-item">
                    <span class="debug-preview-meta-label">表名</span>
                    <code class="debug-preview-meta-table">{{ tbl.tableName }}</code>
                    <span class="debug-preview-count"> {{ (tbl.rows || []).length }} 行{{ tbl.truncated ? '（已截断）' : '' }} </span>
                  </div>
                  <div v-if="tbl.columns && tbl.columns.length" class="debug-preview-table-wrap">
                    <table class="debug-preview-table">
                      <thead>
                        <tr>
                          <th v-for="(col, cidx) in tbl.columns" :key="col">
                            <span>{{ col }}</span>
                            <small v-if="tbl.columnTypes && tbl.columnTypes[cidx]" class="debug-preview-col-type">
                              {{ tbl.columnTypes[cidx] }}
                            </small>
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="(row, ridx) in tbl.rows" :key="ridx">
                          <td v-for="(cell, cidx) in row" :key="cidx">{{ formatPreviewCell(cell) }}</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              </template>
              <template v-else>
                <div class="debug-preview-section-label">数据</div>
                <div v-if="block.result.columns && block.result.columns.length" class="debug-preview-table-wrap">
                  <table class="debug-preview-table">
                    <thead>
                      <tr>
                        <th v-for="col in block.result.columns" :key="col">{{ col }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(row, ridx) in block.result.rows" :key="ridx">
                        <td v-for="(cell, cidx) in row" :key="cidx">{{ formatPreviewCell(cell) }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <div v-else class="debug-preview-empty">暂无数据</div>
              </template>
            </template>
            <div v-else class="debug-preview-empty">暂无数据，请点击上方按钮运行调试</div>
          </div>
        </div>
      </div>
      <template #modal-footer>
        <el-button type="primary" @click="handleConfigSave">保存</el-button>
        <el-button @click="closeConfigModal">取消</el-button>
      </template>
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

.debug-tab-hint {
  flex: 1;
  min-width: 260px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.config-modal-body {
  max-height: 72vh;
  overflow-y: auto;
}

.config-modal-body :deep(.form-actions) {
  padding-right: 16px;
}

.config-tab-header {
  display: flex;
  align-items: center;
  gap: 4px;
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
  margin-bottom: 12px;
}

.config-tab-item {
  padding: 6px 16px;
  font-size: 14px;
  color: var(--el-text-color-regular, #606266);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition:
    color 0.2s,
    border-color 0.2s;
}

.config-tab-item:hover {
  color: var(--el-color-primary, #409eff);
}

.config-tab-item.active {
  color: var(--el-color-primary, #409eff);
  border-bottom-color: var(--el-color-primary, #409eff);
  font-weight: 600;
}

.config-tab-panel {
  padding-top: 4px;
}

.debug-tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.debug-tab-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.debug-tab-status {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  margin-bottom: 10px;
}

.debug-preview-running {
  color: var(--el-color-primary, #409eff);
}

.debug-preview-error {
  margin-bottom: 10px;
}

.debug-preview-error :deep(.el-alert__title) {
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.5;
}

.debug-preview-block {
  margin-bottom: 12px;
}

.debug-preview-block-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular, #606266);
  margin-bottom: 4px;
}

.debug-preview-count {
  font-weight: normal;
  color: var(--el-text-color-secondary, #909399);
}

.debug-preview-section-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-secondary, #909399);
  margin: 6px 0 4px;
}

.debug-preview-attr-list {
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: 4px;
  padding: 4px 8px;
  margin-bottom: 6px;
  max-height: 140px;
  overflow: auto;
}

.debug-preview-attr-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12px;
  line-height: 1.6;
}

.debug-preview-attr-key {
  flex-shrink: 0;
  width: 140px;
  color: var(--el-color-primary, #409eff);
  word-break: break-all;
}

.debug-preview-attr-value {
  flex: 1;
  color: var(--el-text-color-regular, #606266);
  word-break: break-all;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.debug-preview-table-wrap {
  max-height: 220px;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: 4px;
}

.debug-preview-meta {
  margin-bottom: 6px;
}

.debug-preview-table-block {
  margin-bottom: 10px;
}

.debug-preview-meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  line-height: 1.6;
}

.debug-preview-meta-label {
  flex-shrink: 0;
  color: var(--el-text-color-secondary, #909399);
}

.debug-preview-meta-table {
  color: var(--el-color-primary, #409eff);
  background-color: #f5f7fa;
  padding: 1px 6px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
}

.debug-preview-col-type {
  display: block;
  font-weight: normal;
  color: var(--el-text-color-secondary, #909399);
  font-size: 10px;
  margin-top: 1px;
}

.debug-preview-table {
  border-collapse: collapse;
  width: 100%;
  font-size: 12px;
  white-space: nowrap;
}

.debug-preview-table th,
.debug-preview-table td {
  border-right: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
  padding: 4px 8px;
  text-align: left;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.debug-preview-table th {
  position: sticky;
  top: 0;
  background-color: #f5f7fa;
  color: var(--el-text-color-primary, #303133);
  z-index: 1;
}

.debug-preview-empty {
  padding: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  background-color: #fafafa;
  border-radius: 4px;
}
</style>
