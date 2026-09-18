<template>
  <form name="editForm" novalidate>
    <!-- SQL输入区域 -->
    <div class="form-group">
      <label for="sql">SQL语句</label>
      <textarea
        class="form-control"
        id="sql"
        name="sql"
        v-model="formData.sql"
        :rows="8"
        :maxlength="5000"
        placeholder="请输入SQL查询语句，例如：SELECT * FROM table_name WHERE condition"
      ></textarea>
      <small class="form-text text-muted"> 支持标准SQL语法，可以包含SELECT、JOIN、WHERE等操作 </small>
    </div>

    <!-- 参数配置区域 -->
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
      <small class="form-text text-muted"> 每次从数据库读取的记录数，建议值：5000 </small>
    </div>

    <!-- 操作按钮 -->
    <div class="form-actions">
      <el-button type="primary" @click="saveConfig">保存</el-button>
      <el-button @click="cancelConfig">取消</el-button>
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

// 配置项定义
const configItems = [
  {
    key: 'sql',
    label: 'SQL语句',
    defaultValue: '',
    required: true,
    description: '输入SQL查询语句进行数据处理',
    show: true,
    maxLength: 5000,
    rows: 8,
    controlType: 'TEXTAREA',
    parameterType: 'STRING',
  },
  {
    key: 'fetchSize',
    label: '批量处理大小',
    defaultValue: 5000,
    required: true,
    description: '每次从数据库读取的记录数',
    show: true,
    minValue: 1000,
    maxValue: 50000,
    precision: 0,
    controlType: 'INPUTNUMBER',
    parameterType: 'INTEGER',
  },
];

const formRef = ref(null);
const formData = reactive({
  sql: props.node?.data?.config?.sql || '',
  fetchSize: props.node?.data?.config?.fetchSize || 5000,
  timeout: props.node?.data?.config?.timeout || 0,
  allowModifySchema: props.node?.data?.config?.allowModifySchema || false,
});

// SQL语法验证
const validateSql = () => {
  const sql = formData.sql.trim();

  if (!sql) {
    ElMessage.warning('请输入SQL语句');
    return;
  }

  // 基础SQL语法检查
  const sqlUpper = sql.toUpperCase();

  if (!sqlUpper.includes('SELECT')) {
    ElMessage.warning('SQL语句应该包含SELECT关键字');
    return;
  }

  if (sqlUpper.includes('DROP') || sqlUpper.includes('DELETE') || sqlUpper.includes('UPDATE')) {
    ElMessage.warning('当前仅支持SELECT查询语句，不支持数据修改操作');
    return;
  }

  ElMessage.success('SQL语法验证通过');
};

// 保存配置
const saveConfig = async () => {
  const sql = formData.sql.trim();

  if (!sql) {
    ElMessage.error('请输入SQL语句');
    return;
  }

  if (formData.fetchSize < 1000 || formData.fetchSize > 50000) {
    ElMessage.error('批量处理大小应在1000-50000之间');
    return;
  }

  emits('save', formData);
};

// 取消配置
const cancelConfig = () => {
  emits('cancel');
};

defineExpose({ saveConfig });
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

textarea {
  resize: vertical;
  min-height: 120px;
}
</style>