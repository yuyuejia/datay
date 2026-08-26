<template>
  <form name="editForm" novalidate>
    <div>
      <!-- 数据源选择 -->
      <div class="form-group">
        <label for="sourceId">数据源</label>
        <DataSourceSelector
          type="source"
          :datasourceId="formData.sourceId"
          :schema="formData.schema"
          @selected="handleDataSourceSelected"
        />
        <small class="form-text text-muted"> 选择要注册到DuckDB的外部数据源 </small>
      </div>

      <!-- 数据源别名 -->
      <div class="form-group">
        <label for="alias">数据源别名</label>
        <input
          type="text"
          class="form-control"
          id="alias"
          name="alias"
          v-model="formData.alias"
          placeholder="请输入数据源在DuckDB中的别名"
          required
        />
        <small class="form-text text-muted"> 数据源在DuckDB中的别名，用于后续SQL查询中引用 </small>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="form-actions">
      <el-button type="primary" @click="saveConfig">保存</el-button>
      <el-button @click="cancelConfig">取消</el-button>
      <!-- <el-button type="info" @click="validateConfig">验证配置</el-button> -->
      <el-button type="success" @click="showHelp">使用帮助</el-button>
    </div>
  </form>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import DataSourceSelector from '@/components/DataSourceSelector.vue';

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save', 'cancel']);

// 配置项定义
const configItems = [
  {
    key: 'sourceId',
    label: '数据源',
    defaultValue: '',
    required: true,
    description: '选择要注册到DuckDB的外部数据源',
    show: true,
    controlType: 'SELECTDATASOURCE',
    parameterType: 'LONG',
  },
  {
    key: 'alias',
    label: '数据源别名',
    defaultValue: '',
    required: true,
    description: '数据源在DuckDB中的别名',
    show: true,
    maxLength: 64,
    controlType: 'INPUT',
    parameterType: 'STRING',
  },
  {
    key: 'autoInstallPlugin',
    label: '自动安装插件',
    defaultValue: true,
    required: false,
    description: '自动安装数据库插件',
    show: false,
    controlType: 'SWITCH',
    parameterType: 'BOOLEAN',
  },
  {
    key: 'connectionTimeout',
    label: '连接超时',
    defaultValue: 0,
    required: false,
    description: '数据源连接超时时间',
    show: false,
    minValue: 0,
    maxValue: 300,
    precision: 0,
    controlType: 'INPUTNUMBER',
    parameterType: 'INTEGER',
  },
  {
    key: 'validateConnection',
    label: '验证连接',
    defaultValue: true,
    required: false,
    description: '验证数据源连接',
    show: false,
    controlType: 'SWITCH',
    parameterType: 'BOOLEAN',
  },
];

const formRef = ref(null);
const formData = reactive({
  sourceId: props.node?.data?.config?.sourceId || '',
  schema: props.node?.data?.config?.schema || '',
  alias: props.node?.data?.config?.alias || '',
  autoInstallPlugin: props.node?.data?.config?.autoInstallPlugin !== false,
  connectionTimeout: props.node?.data?.config?.connectionTimeout || 0,
  validateConnection: props.node?.data?.config?.validateConnection !== false,
});

// 处理数据源选择事件
const handleDataSourceSelected = selectedData => {
  formData.sourceId = selectedData.dataSourceId;
  formData.schema = selectedData.schema;

  // 如果别名未设置，自动生成一个默认别名
  if (!formData.alias) {
    const dbType = getDatabaseTypeFromUrl(selectedData.url);
    if (dbType) {
      formData.alias = `${dbType}_${selectedData.dataSourceId}`;
    }
  }
};

// 从URL中提取数据库类型（用于自动生成别名）
const getDatabaseTypeFromUrl = url => {
  if (!url) return null;

  const urlLower = url.toLowerCase();
  if (urlLower.includes('mysql')) return 'mysql';
  if (urlLower.includes('postgresql') || urlLower.includes('postgres')) return 'postgres';
  if (urlLower.includes('sqlite')) return 'sqlite';
  if (urlLower.includes('sqlserver') || urlLower.includes('mssql')) return 'sqlserver';
  if (urlLower.includes('oracle')) return 'oracle';
  if (urlLower.includes('duckdb')) return 'duckdb';

  return 'external';
};

// 配置验证
const validateConfig = () => {
  if (!formData.sourceId) {
    ElMessage.error('请选择数据源');
    return;
  }

  if (!formData.alias || formData.alias.trim() === '') {
    ElMessage.error('请输入数据源别名');
    return;
  }

  // 检查别名格式（只允许字母、数字、下划线）
  const aliasRegex = /^[a-zA-Z_][a-zA-Z0-9_]*$/;
  if (!aliasRegex.test(formData.alias)) {
    ElMessage.error('别名只能包含字母、数字和下划线，且不能以数字开头');
    return;
  }

  ElMessage.success('配置验证通过');
};

// 显示使用帮助
const showHelp = () => {
  ElMessageBox.alert(
    `
<h4>DuckDB 数据源注册使用帮助</h4>
<p><strong>功能说明：</strong></p>
<ul>
  <li>将外部数据源注册到DuckDB中，使其可以在DuckDB SQL中直接查询</li>
  <li>支持MySQL、PostgreSQL、SQLite、SQL Server、Oracle等数据源</li>
  <li>使用DuckDB的ATTACH机制实现数据源注册</li>
</ul>
<p><strong>使用示例：</strong></p>
<pre>
-- 注册MySQL数据源后，可以在DuckDB中直接查询
SELECT * FROM mysql_db.customers WHERE age > 30;

-- 注册PostgreSQL数据源
SELECT * FROM postgres_db.orders WHERE amount > 1000;
</pre>
<p><strong>注意事项：</strong></p>
<ul>
  <li>别名在DuckDB中必须是唯一的</li>
  <li>首次使用某种数据库类型时，需要安装对应的DuckDB插件</li>
  <li>确保DuckDB有权限访问外部数据源</li>
</ul>
  `,
    'DuckDB 数据源注册帮助',
    {
      dangerouslyUseHTMLString: true,
      confirmButtonText: '确定',
    },
  );
};

// 保存配置
const saveConfig = async () => {
  // 验证必填项
  if (!formData.sourceId) {
    ElMessage.error('请选择数据源');
    return;
  }

  if (!formData.alias || formData.alias.trim() === '') {
    ElMessage.error('请输入数据源别名');
    return;
  }

  // 发送保存事件
  emits('save', formData);
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

.form-actions {
  margin-top: 30px;
  text-align: right;
}

.data-source-info {
  background-color: #f8f9fa;
  padding: 15px;
  border-radius: 4px;
  border-left: 4px solid #007bff;
}

.data-source-info p {
  margin-bottom: 8px;
}

.form-text {
  font-size: 12px;
  color: #6c757d;
}
</style>
