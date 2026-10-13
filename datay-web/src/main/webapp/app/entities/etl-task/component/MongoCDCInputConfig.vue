<template>
  <el-form name="editForm" label-position="top">
    <div>
      <!-- 数据源选择（仅限 MongoDB） -->
      <el-form-item label="数据源">
        <DataSourceSelector
          data-source-type="MONGODB"
          :datasource-id="formData.sourceId"
          :schema="formData.schema"
          @selected="handleDataSourceSelected"
        />
        <small class="form-text text-muted"> 仅支持 MongoDB 数据源，且必须是副本集或分片集群（Change Streams 依赖） </small>
      </el-form-item>

      <!-- 单集合 -->
      <el-form-item label="集合名">
        <el-input
          id="collection"
          name="collection"
          v-model="formData.collection"
          placeholder="例如：cdc_demo，留空则按下方正则采集多个集合"
        />
        <small class="form-text text-muted"> 采集的单个集合；与“集合名过滤正则”二选一，同时填写时以单个集合为准 </small>
      </el-form-item>

      <!-- 集合过滤正则 -->
      <el-form-item label="集合名过滤正则">
        <el-input
          id="collectionNamePattern"
          name="collectionNamePattern"
          v-model="formData.collectionNamePattern"
          placeholder="例如：order_.* 或 ^cdc_.*"
        />
        <small class="form-text text-muted">
          使用正则过滤集合，为空表示采集数据库下所有集合。例如：order_.* 表示采集 order_ 开头的所有集合
        </small>
      </el-form-item>

      <!-- 后镜像策略 -->
      <el-form-item label="后镜像策略 (fullDocument)">
        <el-select id="fullDocument" name="fullDocument" v-model="formData.fullDocument" style="width: 100%">
          <el-option value="updateLookup" label="updateLookup（推荐，update 返回完整文档）" />
          <el-option value="whenAvailable" label="whenAvailable（有则返回）" />
          <el-option value="required" label="required（必须返回，否则报错）" />
        </el-select>
        <small class="form-text text-muted"> 控制 update 事件是否返回更新后的完整文档，默认 updateLookup </small>
      </el-form-item>

      <!-- 变更流等待时间 -->
      <el-form-item label="变更流等待时间 (maxAwaitTimeMs)">
        <el-input-number
          id="maxAwaitTimeMs"
          name="maxAwaitTimeMs"
          v-model="formData.maxAwaitTimeMs"
          :min="100"
          :controls="false"
          style="width: 100%"
        />
        <small class="form-text text-muted"> 变更流无数据时的阻塞等待毫秒数，默认 1000 </small>
      </el-form-item>

      <!-- 历史全量快照 -->
      <el-form-item label="首次读取历史全量数据">
        <el-switch id="snapshot" name="snapshot" v-model="formData.snapshot" />
        <small class="form-text text-muted">
          启用后，首次运行（无断点）会先全量读取集合数据，再启动增量同步；任务重启有断点时不重复全量
        </small>
      </el-form-item>

      <el-form-item v-if="formData.snapshot" label="快照分批大小">
        <el-input-number
          id="snapshotFetchSize"
          name="snapshotFetchSize"
          v-model="formData.snapshotFetchSize"
          :min="1"
          :controls="false"
          style="width: 100%"
        />
        <small class="form-text text-muted"> 每批下发的记录数，默认 10000 </small>
      </el-form-item>

      <!-- 断点续传 resumeToken -->
      <el-form-item label="起始 resumeToken">
        <el-input
          id="resumeToken"
          name="resumeToken"
          type="textarea"
          :rows="2"
          v-model="formData.resumeToken"
          placeholder='例如：{"_data":"826ACD..."}，为空表示从任务状态或当前位置开始'
        />
        <small class="form-text text-muted"> 指定变更流续传的 resumeToken（可选），一般无需填写，组件会自动从上次保存的位点续传 </small>
      </el-form-item>

      <!-- 起始 operationTime -->
      <el-form-item label="起始 operationTime（epoch 秒）">
        <el-input
          id="startAtOperationTime"
          name="startAtOperationTime"
          v-model="formData.startAtOperationTime"
          placeholder="可选，例如：1710000000"
        />
        <small class="form-text text-muted"> 指定从某个时间点（epoch 秒）开始采集，仅在无 resumeToken 时生效 </small>
      </el-form-item>

      <!-- 启动失败是否忽略 -->
      <el-form-item label="启动失败时忽略错误">
        <el-switch id="ignoreError" name="ignoreError" v-model="formData.ignoreError" />
        <small class="form-text text-muted"> 开启后采集器启动失败仅记录日志、不中断任务，默认开启 </small>
      </el-form-item>
    </div>
  </el-form>
</template>

<script setup>
import { reactive } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';
import DataSourceSelector from '@/components/DataSourceSelector.vue';

const props = defineProps({
  node: { type: Object, default: undefined },
});

const emits = defineEmits(['save']);

const formData = reactive({
  sourceId: props.node?.data?.config?.sourceId || '',
  schema: props.node?.data?.config?.schema || '',
  collection: props.node?.data?.config?.collection || '',
  collectionNamePattern: props.node?.data?.config?.collectionNamePattern || '',
  fullDocument: props.node?.data?.config?.fullDocument || 'updateLookup',
  maxAwaitTimeMs: props.node?.data?.config?.maxAwaitTimeMs || 1000,
  resumeToken: props.node?.data?.config?.resumeToken || '',
  startAtOperationTime: props.node?.data?.config?.startAtOperationTime || '',
  snapshot: props.node?.data?.config?.snapshot || false,
  snapshotFetchSize: props.node?.data?.config?.snapshotFetchSize || 10000,
  ignoreError: props.node?.data?.config?.ignoreError === undefined ? true : props.node?.data?.config?.ignoreError,
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
  emits('save', {
    sourceId: formData.sourceId,
    schema: formData.schema,
    collection: formData.collection,
    collectionNamePattern: formData.collectionNamePattern,
    fullDocument: formData.fullDocument,
    maxAwaitTimeMs: formData.maxAwaitTimeMs,
    resumeToken: formData.resumeToken,
    startAtOperationTime: formData.startAtOperationTime,
    snapshot: formData.snapshot,
    snapshotFetchSize: formData.snapshotFetchSize,
    ignoreError: formData.ignoreError,
  });
};

defineExpose({ saveConfig });
</script>

<style scoped>
.form-text {
  display: block;
  margin-top: 5px;
  color: #909399;
  font-size: 12px;
}
</style>
