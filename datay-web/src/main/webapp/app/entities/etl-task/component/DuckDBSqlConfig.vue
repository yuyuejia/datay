<template>
  <form name="editForm" novalidate>
    <div>
      <!-- DuckDB SQL输入区域 -->
      <div class="form-group">
        <label for="sql">DuckDB SQL语句</label>
        <textarea
          class="form-control"
          id="sql"
          name="sql"
          v-model="formData.sql"
          :rows="8"
          :maxlength="5000"
          placeholder="请输入DuckDB SQL查询语句，例如：SELECT * FROM read_parquet('file.parquet') WHERE condition"
        ></textarea>
        <small class="form-text text-muted"> 支持DuckDB特有的SQL语法，可以包含read_parquet、read_csv等DuckDB特有函数 </small>
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
        <small class="form-text text-muted"> 每次从DuckDB读取的记录数，默认值：5000 </small>
      </div>

      <!-- 输出表名配置 -->
      <div class="form-group">
        <label for="outputTable">输出表名</label>
        <input
          type="text"
          class="form-control"
          id="outputTable"
          name="outputTable"
          v-model="formData.outputTable"
          placeholder="duckdb_query_result"
        />
        <small class="form-text text-muted"> 查询结果的输出表名，默认为：duckdb_query_result </small>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="form-actions">
      <el-button type="primary" @click="saveConfig">保存</el-button>
      <el-button @click="cancelConfig">取消</el-button>
      <el-button type="info" @click="validateDuckDBSql">验证SQL</el-button>
      <el-button type="success" @click="showDuckDBHelp">DuckDB帮助</el-button>
    </div>
  </form>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save', 'cancel']);

// 配置项定义
const configItems = [
  {
    key: 'sql',
    label: 'DuckDB SQL语句',
    defaultValue: '',
    required: true,
    description: '输入DuckDB SQL查询语句进行数据处理',
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
    description: '每次从DuckDB读取的记录数',
    show: true,
    minValue: 1000,
    maxValue: 50000,
    precision: 0,
    controlType: 'INPUTNUMBER',
    parameterType: 'INTEGER',
  },
  {
    key: 'outputTable',
    label: '输出表名',
    defaultValue: 'duckdb_query_result',
    required: false,
    description: '查询结果的输出表名',
    show: true,
    maxLength: 64,
    controlType: 'INPUT',
    parameterType: 'STRING',
  },
];

const formRef = ref(null);
const formData = reactive({
  sql: props.node?.data?.config?.sql || '',
  fetchSize: props.node?.data?.config?.fetchSize || 5000,
  outputTable: props.node?.data?.config?.outputTable || 'duckdb_query_result',
  timeout: props.node?.data?.config?.timeout || 0,
  memoryLimit: props.node?.data?.config?.memoryLimit || 0,
  threads: props.node?.data?.config?.threads || 0,
});

// DuckDB SQL语法验证
const validateDuckDBSql = () => {
  const sql = formData.sql.trim();

  if (!sql) {
    ElMessage.warning('请输入DuckDB SQL语句');
    return;
  }

  // 基础SQL语法检查
  const sqlUpper = sql.toUpperCase();

  if (!sqlUpper.includes('SELECT')) {
    ElMessage.warning('SQL语句应该包含SELECT关键字');
    return;
  }

  // 检查DuckDB特有函数
  const duckdbFunctions = ['READ_PARQUET', 'READ_CSV', 'READ_JSON', 'FROM', 'SHOW'];
  const hasDuckDBFunction = duckdbFunctions.some(func => sqlUpper.includes(func));

  if (!hasDuckDBFunction) {
    ElMessage.warning('建议使用DuckDB特有函数如read_parquet()、read_csv()等');
  }

  // 防止危险操作
  const dangerousKeywords = ['DROP', 'DELETE', 'UPDATE', 'ALTER', 'CREATE TABLE'];
  const hasDangerous = dangerousKeywords.some(keyword => sqlUpper.includes(keyword));

  if (hasDangerous) {
    ElMessage.warning('当前仅支持SELECT查询语句，不支持数据定义和修改操作');
    return;
  }

  ElMessage.success('DuckDB SQL语法验证通过');
};

// 显示DuckDB帮助信息
const showDuckDBHelp = () => {
  ElMessageBox.alert(
    `
<h4>DuckDB SQL 使用帮助</h4>
<p><strong>常用函数：</strong></p>
<ul>
  <li><code>read_parquet('file.parquet')</code> - 读取Parquet文件</li>
  <li><code>read_csv('file.csv')</code> - 读取CSV文件</li>
  <li><code>read_json('file.json')</code> - 读取JSON文件</li>
  <li><code>FROM 'file.parquet'</code> - 直接读取文件</li>
</ul>
<p><strong>示例查询：</strong></p>
<pre>
SELECT * FROM read_parquet('data.parquet') 
WHERE date > '2023-01-01'
LIMIT 1000;

SELECT column1, COUNT(*) 
FROM read_csv('data.csv') 
GROUP BY column1;
</pre>
<p><strong>注意事项：</strong></p>
<ul>
  <li>支持标准SQL语法</li>
  <li>可以使用DuckDB特有的分析函数</li>
  <li>支持窗口函数和复杂聚合</li>
</ul>
  `,
    'DuckDB SQL 帮助',
    {
      dangerouslyUseHTMLString: true,
      confirmButtonText: '确定',
    },
  );
};

// 保存配置
const saveConfig = async () => {
  const sql = formData.sql.trim();

  if (!sql) {
    ElMessage.error('请输入DuckDB SQL语句');
    return;
  }

  if (formData.fetchSize < 1000 || formData.fetchSize > 50000) {
    ElMessage.error('批量处理大小应在1000-50000之间');
    return;
  }

  if (formData.outputTable && !/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(formData.outputTable)) {
    ElMessage.error('输出表名只能包含字母、数字和下划线，且不能以数字开头');
    return;
  }

  if (formData.timeout < 0 || formData.timeout > 3600) {
    ElMessage.error('超时时间应在0-3600秒之间');
    return;
  }

  if (formData.memoryLimit < 0 || formData.memoryLimit > 8192) {
    ElMessage.error('内存限制应在0-8192MB之间');
    return;
  }

  if (formData.threads < 0 || formData.threads > 16) {
    ElMessage.error('线程数应在1-16之间');
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
  font-family: 'Courier New', monospace;
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
  font-family: 'Courier New', monospace;
}

.help-content {
  font-size: 14px;
  line-height: 1.6;
}

.help-content pre {
  background-color: #f5f5f5;
  padding: 10px;
  border-radius: 4px;
  overflow-x: auto;
  font-size: 12px;
}

.help-content code {
  background-color: #f5f5f5;
  padding: 2px 4px;
  border-radius: 2px;
  font-family: 'Courier New', monospace;
}
</style>