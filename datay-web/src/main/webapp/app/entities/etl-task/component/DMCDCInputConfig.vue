<template>
  <el-form name="editForm" label-position="top">
    <div>
      <!-- 数据源选择（仅限达梦） -->
      <el-form-item label="数据源">
        <DataSourceSelector
          data-source-type="DM"
          :datasource-id="formData.sourceId"
          :schema="formData.schema"
          @selected="handleDataSourceSelected"
        />
        <small class="form-text text-muted"> 仅支持达梦 8 数据源，需开启归档、逻辑日志并创建 DBMS_LOGMNR 系统包 </small>
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
          使用正则表达式过滤表，为空表示采集 schema 下所有表。达梦默认模式名和表名为大写，匹配不区分大小写
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

      <!-- 自动切换归档 -->
      <el-form-item label="自动切换归档">
        <el-switch id="archiveSwitch" name="archiveSwitch" v-model="formData.archiveSwitch" />
        <small class="form-text text-muted">
          达梦 DBMS_LOGMNR 只能挖掘归档日志。开启后组件周期性执行 ALTER SYSTEM ARCHIVE LOG
          CURRENT；关闭后仅消费已有归档，延迟取决于达梦归档频率
        </small>
      </el-form-item>

      <el-form-item v-if="formData.archiveSwitch" label="归档切换间隔（毫秒）">
        <el-input-number
          id="archiveSwitchIntervalMs"
          name="archiveSwitchIntervalMs"
          v-model="formData.archiveSwitchIntervalMs"
          :min="1000"
          :step="1000"
          :controls="false"
          style="width: 100%"
        />
        <small class="form-text text-muted"> 两次归档切换之间的最短间隔，默认 5000 毫秒；过小会产生大量小归档文件 </small>
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

      <!-- 事件元数据 -->
      <el-form-item label="输出事件元数据">
        <el-switch id="includeEventMeta" name="includeEventMeta" v-model="formData.includeEventMeta" />
        <small class="form-text text-muted"> 开启后在每行数据中追加 _event_type（INSERT/UPDATE/DELETE）和 _timestamp（事件时间） </small>
      </el-form-item>

      <el-form-item label="最大重连次数">
        <el-input-number
          id="reconnectMaxAttempts"
          name="reconnectMaxAttempts"
          v-model="formData.reconnectMaxAttempts"
          :min="0"
          :controls="false"
        />
        <small class="form-text text-muted"> 连接异常时自动重连的最大次数，默认 5；0 表示不重连，超过后任务失败 </small>
      </el-form-item>

      <el-form-item label="重连间隔（毫秒）">
        <el-input-number
          id="reconnectIntervalMs"
          name="reconnectIntervalMs"
          v-model="formData.reconnectIntervalMs"
          :min="0"
          :step="500"
          :controls="false"
        />
        <small class="form-text text-muted"> 两次重连之间的等待时间，默认 3000 毫秒 </small>
      </el-form-item>

      <!-- 忽略错误 -->
      <el-form-item label="忽略错误">
        <el-switch id="ignoreError" name="ignoreError" v-model="formData.ignoreError" />
        <small class="form-text text-muted"> 非连接类挖掘错误记录日志而不中断任务；启动校验错误和数据源连接失败始终使任务失败 </small>
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
  archiveSwitch: props.node?.data?.config?.archiveSwitch ?? true,
  archiveSwitchIntervalMs: props.node?.data?.config?.archiveSwitchIntervalMs ?? 5000,
  snapshot: props.node?.data?.config?.snapshot || false,
  snapshotFetchSize: props.node?.data?.config?.snapshotFetchSize || 10000,
  includeEventMeta: props.node?.data?.config?.includeEventMeta ?? false,
  reconnectMaxAttempts: props.node?.data?.config?.reconnectMaxAttempts ?? 5,
  reconnectIntervalMs: props.node?.data?.config?.reconnectIntervalMs ?? 3000,
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

// 显示达梦配置帮助
const showHelp = () => {
  ElMessageBox.alert(
    `
<h4>达梦 CDC（DBMS_LOGMNR）配置帮助</h4>
<p><strong>1. 开启归档与逻辑日志</strong>（SYSDBA 执行）：</p>
<pre>
-- 开启归档（已有归档可跳过）
ALTER DATABASE MOUNT;
ALTER DATABASE ARCHIVELOG;
ALTER DATABASE ADD ARCHIVELOG 'TYPE=LOCAL, DEST=/opt/dmdbms/arch, FILE_SIZE=256, SPACE_LIMIT=1024';
ALTER DATABASE OPEN;

-- 记录逻辑日志（2 = 完整前后镜像，推荐）
SP_SET_PARA_VALUE(1, 'RLOG_APPEND_LOGIC', 2);
SP_SET_PARA_VALUE(1, 'RLOG_IGNORE_TABLE_SET', 1);

-- 创建日志挖掘系统包
SP_CREATE_SYSTEM_PACKAGES(1, 'DBMS_LOGMNR');
</pre>
<p><strong>注意</strong>：修改 RLOG_APPEND_LOGIC 后需要重启达梦实例，新的逻辑日志级别才会生效。</p>
<p><strong>2. 采集账号授权</strong>：</p>
<pre>
GRANT SELECT ANY TABLE TO &lt;采集账号&gt;;
GRANT SELECT ON V$DM_INI TO &lt;采集账号&gt;;
GRANT SELECT ON V$DATABASE TO &lt;采集账号&gt;;
GRANT SELECT ON V$RLOG TO &lt;采集账号&gt;;
GRANT SELECT ON V$ARCHIVED_LOG TO &lt;采集账号&gt;;
GRANT SELECT ON V$DM_ARCH_INI TO &lt;采集账号&gt;;
GRANT SELECT ON V$LOGMNR_CONTENTS TO &lt;采集账号&gt;;
GRANT SELECT ON V$LOGMNR_LOGS TO &lt;采集账号&gt;;
GRANT EXECUTE ON DBMS_LOGMNR TO &lt;采集账号&gt;;
-- 自动切归档需要额外权限（或授予 DBA）
GRANT ALTER DATABASE TO &lt;采集账号&gt;;
</pre>
<p><strong>3. 配置项说明</strong></p>
<ul>
  <li><strong>数据源</strong>：仅可选择达梦 8 数据源</li>
  <li><strong>Schema</strong>：达梦模式名，缺省取数据源默认 Schema；未配置时取用户名大写</li>
  <li><strong>表过滤模式</strong>：正则过滤表名（不区分大小写），留空采集整个 Schema</li>
  <li><strong>起始 SCN</strong>：一般留空，组件自动从上次保存位点续采</li>
  <li><strong>自动切换归档</strong>：默认开启，保证秒级采集；关闭后只消费已有归档</li>
  <li><strong>首次读取历史全量数据</strong>：开启后首次运行先全量后增量</li>
</ul>
<p><strong>4. 常见问题</strong></p>
<ul>
  <li>未开启归档：ARCH_MODE 必须为 Y</li>
  <li>RLOG_APPEND_LOGIC 必须为 1 或 2，推荐 2；0 无法采集，3/4 不记录完整 DML</li>
  <li>DBMS_LOGMNR 包不存在：执行 SP_CREATE_SYSTEM_PACKAGES(1,'DBMS_LOGMNR')</li>
  <li>归档切换权限不足：授予归档切换权限，或关闭“自动切换归档”</li>
  <li>CLOB/BLOB/TEXT/IMAGE/ROWID/二进制等类型暂不支持取值，会被忽略并告警</li>
  <li>不支持 DDL；源表结构变更需手动同步或重跑全量</li>
</ul>
    `,
    '达梦 CDC 配置帮助',
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
