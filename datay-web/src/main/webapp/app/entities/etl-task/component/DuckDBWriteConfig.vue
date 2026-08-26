<template>
  <form name="editForm" novalidate>
    <div>
      <!-- 目标表配置 -->
      <div class="form-group">
        <label for="table">目标表名</label>
        <input type="text" class="form-control" id="table" name="table" v-model="formData.table" placeholder="请输入DuckDB表名" required />
        <small class="form-text text-muted"> 数据将写入到此DuckDB表中，如果表不存在会自动创建 </small>
      </div>

      <!-- 写入策略 -->
      <div class="form-group">
        <label for="model">写入策略</label>
        <select class="form-control" v-model="formData.model">
          <option v-for="item in writeModeOptions" :key="item.value" :value="item.value">
            {{ item.name }}
          </option>
        </select>
        <small class="form-text text-muted"> 选择数据写入DuckDB的方式 </small>
      </div>

      <!-- 批量处理配置 -->
      <div class="form-group">
        <label for="fetchSize">批量处理大小</label>
        <input
          type="number"
          class="form-control"
          id="fetchSize"
          name="fetchSize"
          v-model.number="formData.fetchSize"
          min="1000"
          max="50000"
          step="1000"
        />
        <small class="form-text text-muted"> 每次批量写入DuckDB的记录数，默认值：5000 </small>
      </div>

      <!-- 高级配置 -->
      <div class="form-group">
        <el-collapse>
          <el-collapse-item title="高级配置">
            <!-- 表创建选项 -->
            <div class="form-group">
              <el-checkbox v-model="formData.autoCreateTable"> 自动创建表 </el-checkbox>
              <small class="form-text text-muted"> 如果目标表不存在，根据上游数据自动创建表结构 </small>
            </div>

            <!-- 表存在处理 -->
            <div class="form-group" v-if="formData.autoCreateTable">
              <label for="tableExistsAction">表存在时的处理</label>
              <select class="form-control" v-model="formData.tableExistsAction">
                <option value="skip">跳过（不创建）</option>
                <option value="drop">删除并重建</option>
                <option value="append">追加数据</option>
              </select>
              <small class="form-text text-muted"> 当目标表已存在时的处理方式 </small>
            </div>

            <!-- 性能优化选项 -->
            <div class="form-group">
              <el-checkbox v-model="formData.optimizePerformance"> 性能优化 </el-checkbox>
              <small class="form-text text-muted"> 启用DuckDB特有的性能优化选项 </small>
            </div>

            <!-- 内存限制 -->
            <div class="form-group" v-if="formData.optimizePerformance">
              <label for="memoryLimit">内存限制(MB)</label>
              <input
                type="number"
                class="form-control"
                id="memoryLimit"
                name="memoryLimit"
                v-model.number="formData.memoryLimit"
                min="0"
                max="8192"
                step="128"
              />
              <small class="form-text text-muted"> 设置DuckDB写入操作的内存限制，0表示使用默认值 </small>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="form-actions">
      <el-button type="primary" @click="saveConfig">保存</el-button>
      <el-button @click="cancelConfig">取消</el-button>
      <el-button type="info" @click="validateConfig">验证配置</el-button>
    </div>
  </form>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save', 'cancel']);

// 写入策略选项
const writeModeOptions = [
  {
    name: '覆盖写入（OVERWRITE）',
    value: 'overwrite',
    description: '清空目标表后写入新数据',
  },
  {
    name: '追加写入（INSERT）',
    value: 'append',
    description: '将数据追加到目标表中',
  },
  {
    name: '更新写入（UPDATE）',
    value: 'update',
    description: '根据主键更新目标表中的数据',
  },
];

// 配置项定义
const configItems = [
  {
    key: 'table',
    label: '目标表名',
    defaultValue: '',
    required: true,
    description: '数据写入的DuckDB表名',
    show: true,
    maxLength: 64,
    controlType: 'INPUT',
    parameterType: 'STRING',
  },
  {
    key: 'model',
    label: '写入策略',
    defaultValue: 'append',
    required: true,
    description: '数据写入DuckDB的方式',
    show: true,
    controlType: 'SELECT',
    parameterType: 'STRING',
  },
  {
    key: 'fetchSize',
    label: '批量处理大小',
    defaultValue: 5000,
    required: true,
    description: '每次批量写入的记录数',
    show: true,
    minValue: 1000,
    maxValue: 50000,
    precision: 0,
    controlType: 'INPUTNUMBER',
    parameterType: 'INTEGER',
  },
  {
    key: 'autoCreateTable',
    label: '自动创建表',
    defaultValue: true,
    required: false,
    description: '如果表不存在则自动创建',
    show: false,
    controlType: 'SWITCH',
    parameterType: 'BOOLEAN',
  },
  {
    key: 'tableExistsAction',
    label: '表存在处理',
    defaultValue: 'skip',
    required: false,
    description: '当目标表已存在时的处理方式',
    show: false,
    controlType: 'SELECT',
    parameterType: 'STRING',
  },
];

const formRef = ref(null);
const formData = reactive({
  table: props.node?.data?.config?.table || '',
  model: props.node?.data?.config?.model || 'overwrite',
  fetchSize: props.node?.data?.config?.fetchSize || 5000,
  autoCreateTable: props.node?.data?.config?.autoCreateTable !== false,
  tableExistsAction: props.node?.data?.config?.tableExistsAction || 'skip',
  optimizePerformance: props.node?.data?.config?.optimizePerformance || false,
  memoryLimit: props.node?.data?.config?.memoryLimit || 0,
});

// 获取写入策略名称
const getWriteModeName = mode => {
  const option = writeModeOptions.find(opt => opt.value === mode);
  return option ? option.name : '未知策略';
};

// 配置验证
const validateConfig = () => {
  if (!formData.table.trim()) {
    ElMessage.warning('请输入目标表名');
    return;
  }

  if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(formData.table)) {
    ElMessage.warning('表名只能包含字母、数字和下划线，且不能以数字开头');
    return;
  }

  if (formData.fetchSize < 1000 || formData.fetchSize > 50000) {
    ElMessage.warning('批量处理大小应在1000-50000之间');
    return;
  }

  if (formData.memoryLimit < 0 || formData.memoryLimit > 8192) {
    ElMessage.warning('内存限制应在0-8192MB之间');
    return;
  }

  ElMessage.success('配置验证通过');
};

// 保存配置
const saveConfig = async () => {
  const table = formData.table.trim();

  if (!table) {
    ElMessage.error('请输入目标表名');
    return;
  }

  if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(table)) {
    ElMessage.error('表名只能包含字母、数字和下划线，且不能以数字开头');
    return;
  }

  if (formData.fetchSize < 1000 || formData.fetchSize > 50000) {
    ElMessage.error('批量处理大小应在1000-50000之间');
    return;
  }

  if (formData.memoryLimit < 0 || formData.memoryLimit > 8192) {
    ElMessage.error('内存限制应在0-8192MB之间');
    return;
  }

  // 准备保存的数据
  const saveData = {
    table: table,
    model: formData.model,
    fetchSize: formData.fetchSize,
    autoCreateTable: formData.autoCreateTable,
    tableExistsAction: formData.tableExistsAction,
    optimizePerformance: formData.optimizePerformance,
    memoryLimit: formData.memoryLimit,
  };

  emits('save', saveData);
};

// 取消配置
const cancelConfig = () => {
  emits('cancel');
};
</script>

<style scoped>
.form-group {
  margin-bottom: 20px;
}

.form-control {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
}

.form-control:focus {
  border-color: #409eff;
  outline: none;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.form-text {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}

.form-actions {
  margin-top: 30px;
  text-align: right;
}

.el-collapse {
  border: 1px solid #ebeef5;
  border-radius: 4px;
}

.el-checkbox {
  margin-right: 10px;
}
</style>
