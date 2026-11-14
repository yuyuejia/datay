<template>
  <el-form name="editForm" label-position="top">
    <div>
      <!-- 数据源选择（仅限 SQL Server） -->
      <el-form-item label="数据源">
        <DataSourceSelector
          data-source-type="SQLSERVER"
          :datasource-id="formData.sourceId"
          :schema="formData.schema"
          @selected="handleDataSourceSelected"
        />
        <small class="form-text text-muted"> 仅支持 SQL Server 数据源，需开启数据库与表的 CDC，并确保 SQL Server Agent 正在运行 </small>
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
          使用正则表达式过滤表，为空表示采集 schema 下所有已开启 CDC 的表。SQL Server 默认 schema 为 dbo
        </small>
      </el-form-item>

      <!-- 起始 LSN -->
      <el-form-item label="起始 LSN">
        <el-input
          id="startLsn"
          name="startLsn"
          v-model="formData.startLsn"
          placeholder="例如：0x0000002A0000003C0004；留空表示从上次位点或当前位置开始"
        />
        <small class="form-text text-muted"> 指定从哪个 LSN 之后开始采集（可选），一般无需填写，组件会自动从上次保存的位点续采 </small>
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
        <small class="form-text text-muted">
          组件查询 change table 的间隔，默认 1000 毫秒；实际延迟还受 SQL Server Agent CDC 捕获作业影响
        </small>
      </el-form-item>

      <!-- 自动开启 CDC -->
      <el-form-item label="自动开启 CDC">
        <el-switch id="autoEnableCdc" name="autoEnableCdc" v-model="formData.autoEnableCdc" />
        <small class="form-text text-muted">
          开启后，组件会检查数据库和目标表，未开启 CDC 时自动执行 sp_cdc_enable_db / sp_cdc_enable_table；需要 db_owner 权限，生产环境建议由
          DBA 预先配置
        </small>
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
        <small class="form-text text-muted"> 非连接类采集错误记录日志而不中断任务；启动校验错误和数据源连接失败始终使任务失败 </small>
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
  startLsn: props.node?.data?.config?.startLsn || '',
  pollIntervalMs: props.node?.data?.config?.pollIntervalMs || 1000,
  autoEnableCdc: props.node?.data?.config?.autoEnableCdc ?? false,
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
    formData.schema = 'dbo';
  }
  emits('save', formData);
};

// 显示 SQL Server CDC 配置帮助
const showHelp = () => {
  ElMessageBox.alert(
    `
<h4>SQL Server CDC 配置帮助</h4>
<p><strong>1. 开启数据库 CDC</strong>（db_owner 或 sysadmin 执行）：</p>
<pre>
USE [your_database];
EXEC sys.sp_cdc_enable_db;

-- 校验
SELECT name, is_cdc_enabled FROM sys.databases WHERE name = 'your_database';
</pre>
<p><strong>2. 确保 SQL Server Agent 正在运行</strong></p>
<pre>
-- CDC 捕获作业依赖 Agent；Express 版没有 Agent，不支持 CDC
-- 查看捕获作业
EXEC sys.sp_cdc_help_jobs;

-- 启动捕获作业
EXEC sys.sp_cdc_start_job @job_type = N'capture';
</pre>
<p><strong>3. 为表开启 CDC</strong>（默认捕获所有列）：</p>
<pre>
EXEC sys.sp_cdc_enable_table
    @source_schema = N'dbo',
    @source_name   = N'orders',
    @role_name     = NULL,
    @supports_net_changes = 0;

-- 校验
SELECT s.name AS schema_name, t.name AS table_name, t.is_tracked_by_cdc
FROM sys.tables t
JOIN sys.schemas s ON s.schema_id = t.schema_id
WHERE s.name = N'dbo';
</pre>
<p><strong>4. 采集账号授权</strong>：</p>
<pre>
GRANT SELECT ON [dbo].[orders] TO [datay];
GRANT SELECT ON SCHEMA::cdc TO [datay];
-- 自动开启 CDC 需要 db_owner
ALTER ROLE db_owner ADD MEMBER [datay];
</pre>
<p><strong>5. 配置项说明</strong></p>
<ul>
  <li><strong>数据源</strong>：仅可选择 SQL Server 数据源</li>
  <li><strong>Schema</strong>：默认 dbo，可在数据源选择器中切换</li>
  <li><strong>表过滤模式</strong>：正则过滤表名（不区分大小写），留空采集 schema 下所有已开启 CDC 的表</li>
  <li><strong>起始 LSN</strong>：一般留空，组件自动从上次保存位点续采</li>
  <li><strong>自动开启 CDC</strong>：默认关闭；开启后需 db_owner 权限，组件会修改源库 CDC 配置</li>
  <li><strong>首次读取历史全量数据</strong>：开启后首次运行先全量后增量</li>
</ul>
<p><strong>6. 常见问题</strong></p>
<ul>
  <li>数据库未开启 CDC：执行 EXEC sys.sp_cdc_enable_db</li>
  <li>表未开启 CDC：执行 sys.sp_cdc_enable_table，或打开“自动开启 CDC”</li>
  <li>SQL Server Agent 未运行：CDC 捕获作业不会执行，change table 不会有数据</li>
  <li>SQL Server Express：没有 Agent，不支持 CDC</li>
  <li>LSN 区间错误 313：通常是位点早于 CDC 清理边界，组件会自动从最小 LSN 继续并告警</li>
  <li>默认保留 3 天变更：长时间停机可能丢失变更，请调整 CDC 清理作业或及时恢复任务</li>
  <li>不支持 DDL：源表结构变更需要重建 capture instance 或重跑全量</li>
  <li>text/ntext/image 在 UPDATE 前镜像中为 NULL；计算列不采集</li>
</ul>
    `,
    'SQL Server CDC 配置帮助',
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
