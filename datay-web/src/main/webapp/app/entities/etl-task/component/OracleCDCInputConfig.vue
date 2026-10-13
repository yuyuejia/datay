<template>
  <el-form name="editForm" label-position="top">
    <div>
      <!-- 数据源选择（仅限 Oracle） -->
      <el-form-item label="数据源">
        <DataSourceSelector
          data-source-type="ORACLE"
          :datasource-id="formData.sourceId"
          :schema="formData.schema"
          @selected="handleDataSourceSelected"
        />
        <small class="form-text text-muted"> 仅支持 Oracle 数据源，需开启归档模式（ARCHIVELOG）与补充日志 </small>
        <div class="form-actions">
          <el-button type="success" plain size="small" @click="showHelp">配置帮助</el-button>
        </div>
      </el-form-item>

      <!-- 表过滤模式 -->
      <el-form-item label="表过滤模式">
        <el-input
          id="tableNamePattern"
          name="tableNamePattern"
          v-model="formData.tableNamePattern"
          placeholder="例如：CDC_.* 或 ^ORDER.*（不区分大小写）"
        />
        <small class="form-text text-muted">
          使用正则表达式过滤表，为空表示采集 schema 下所有表。Oracle 表名默认大写，匹配不区分大小写
        </small>
      </el-form-item>

      <!-- 起始 SCN -->
      <el-form-item label="起始 SCN">
        <el-input-number
          id="startScn"
          name="startScn"
          v-model="formData.startScn"
          :min="0"
          :controls="false"
          style="width: 100%"
          placeholder="留空表示从上次位点或当前 SCN 开始"
        />
        <small class="form-text text-muted"> 指定从哪个 SCN 开始采集（可选），一般无需填写，组件会自动从上次保存的位点续采 </small>
      </el-form-item>

      <!-- 轮询间隔 -->
      <el-form-item label="轮询间隔（毫秒）">
        <el-input-number
          id="pollIntervalMs"
          name="pollIntervalMs"
          v-model="formData.pollIntervalMs"
          :min="100"
          :controls="false"
          style="width: 100%"
        />
        <small class="form-text text-muted"> 组件周期性挖掘新日志的间隔，默认 1000 毫秒 </small>
      </el-form-item>

      <!-- 历史全量快照 -->
      <el-form-item label="首次读取历史全量数据">
        <el-switch id="snapshot" name="snapshot" v-model="formData.snapshot" />
        <small class="form-text text-muted">
          启用后，首次运行（无断点）会先全量读取 schema 下匹配表的数据，再启动增量同步；任务重启有断点时不重复全量
        </small>
      </el-form-item>

      <el-form-item v-if="formData.snapshot" label="快照分批大小">
        <el-input-number id="snapshotFetchSize" name="snapshotFetchSize" v-model="formData.snapshotFetchSize" :min="1" :controls="false" />
        <small class="form-text text-muted"> 每批下发的记录数，默认 10000 </small>
      </el-form-item>

      <!-- 忽略错误 -->
      <el-form-item label="忽略错误">
        <el-switch id="ignoreError" name="ignoreError" v-model="formData.ignoreError" />
        <small class="form-text text-muted"> 采集启动或运行出错时记录日志而不中断任务 </small>
      </el-form-item>
    </div>
  </el-form>
</template>

<script setup>
import { reactive } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import DataSourceSelector from '@/components/DataSourceSelector.vue';

const props = defineProps({
  node: { type: Object, default: undefined },
});

const emits = defineEmits(['save']);

const formData = reactive({
  sourceId: props.node?.data?.config?.sourceId || '',
  schema: props.node?.data?.config?.schema || '',
  tableNamePattern: props.node?.data?.config?.tableNamePattern || '',
  startScn: props.node?.data?.config?.startScn ?? undefined,
  pollIntervalMs: props.node?.data?.config?.pollIntervalMs || 1000,
  snapshot: props.node?.data?.config?.snapshot || false,
  snapshotFetchSize: props.node?.data?.config?.snapshotFetchSize || 10000,
  ignoreError: props.node?.data?.config?.ignoreError ?? true,
});

const handleDataSourceSelected = selectedData => {
  formData.sourceId = selectedData.dataSourceId;
  formData.schema = selectedData.schema;
};

const saveConfig = async () => {
  if (!formData.sourceId) {
    ElMessage.error('请选择数据源');
    return;
  }
  if (!formData.schema) {
    ElMessage.error('请选择 schema');
    return;
  }
  emits('save', formData);
};

// 显示 Oracle 配置帮助
const showHelp = () => {
  ElMessageBox.alert(
    `
<h4>Oracle CDC（LogMiner）配置帮助</h4>
<p><strong>1. 前置要求</strong>（在源库以 SYSDBA 执行）：</p>
<pre>
-- 开启归档模式（需重启到 MOUNT 状态）
SHUTDOWN IMMEDIATE;
STARTUP MOUNT;
ALTER DATABASE ARCHIVELOG;
ALTER DATABASE OPEN;

-- 开启补充日志（ALL COLUMNS 用于补全 UPDATE 未变更列）
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA;
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;
</pre>
<p><strong>2. 采集账号授权</strong>（在目标 PDB / 非 CDB 库中执行）：</p>
<pre>
GRANT LOGMINING TO &lt;采集账号&gt;;
GRANT SELECT ANY TRANSACTION TO &lt;采集账号&gt;;
GRANT EXECUTE ON SYS.DBMS_LOGMNR TO &lt;采集账号&gt;;
GRANT EXECUTE ON SYS.DBMS_LOGMNR_D TO &lt;采集账号&gt;;
GRANT SELECT ON SYS.V_$LOGMNR_CONTENTS TO &lt;采集账号&gt;;
GRANT SELECT ON SYS.V_$LOGFILE TO &lt;采集账号&gt;;
</pre>
<p><strong>3. 配置项说明</strong></p>
<ul>
  <li><strong>数据源</strong>：仅可选择 Oracle 数据源，且需具备上述权限</li>
  <li><strong>Schema</strong>：Oracle 中即用户，缺省取数据源默认 Schema</li>
  <li><strong>表过滤模式</strong>：正则过滤表名（不区分大小写），留空采集整个 Schema</li>
  <li><strong>起始 SCN</strong>：一般留空，组件自动从上次保存位点续采</li>
  <li><strong>轮询间隔</strong>：周期性挖掘新日志的间隔，默认 1000 毫秒</li>
  <li><strong>首次读取历史全量数据</strong>：开启后首次运行先全量后增量</li>
</ul>
<p><strong>4. 常见问题</strong></p>
<ul>
  <li>未开启归档模式：报错提示 ARCHIVELOG，按上文第 1 步处理</li>
  <li>权限不足：确认 LOGMINING 角色与 SELECT ANY TRANSACTION 权限</li>
  <li>PDB 中 ORA-65040：组件已自动识别 PDB 并跳过 ADD_LOGFILE</li>
  <li>CLOB/BLOB/LONG/ROWID 等类型暂不支持取值，会被忽略并告警</li>
  <li>不支持 DDL：源表结构变更需手动同步或重跑全量</li>
</ul>
    `,
    'Oracle CDC 配置帮助',
    { dangerouslyUseHTMLString: true, confirmButtonText: '知道了' },
  );
};

defineExpose({ saveConfig });
</script>

<style scoped>
.form-actions {
  margin-top: 8px;
}

.form-text {
  display: block;
  margin-top: 5px;
  color: #909399;
  font-size: 12px;
}
</style>
