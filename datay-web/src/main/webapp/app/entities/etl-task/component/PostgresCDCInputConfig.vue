<template>
  <el-form name="editForm" label-position="top">
    <div>
      <!-- 数据源选择（仅限 PostgreSQL） -->
      <el-form-item label="数据源">
        <DataSourceSelector
          data-source-type="POSTGRESQL"
          :datasource-id="formData.sourceId"
          :schema="formData.schema"
          @selected="handleDataSourceSelected"
        />
        <small class="form-text text-muted">
          仅支持 PostgreSQL 数据源，需开启 wal_level=logical
        </small>
      </el-form-item>

      <!-- 表过滤模式 -->
      <el-form-item label="表过滤模式">
        <el-input
          id="tableNamePattern"
          name="tableNamePattern"
          v-model="formData.tableNamePattern"
          placeholder="例如：user_.* 或 ^order.*"
        />
        <small class="form-text text-muted">
          使用正则表达式过滤表，为空表示采集 schema 下所有表。例如：user_.*
          表示采集 user_ 开头的所有表
        </small>
      </el-form-item>

      <!-- publication 名称 -->
      <el-form-item label="Publication 名称">
        <el-input
          id="publicationName"
          name="publicationName"
          v-model="formData.publicationName"
          placeholder="留空自动生成，例如：datay_pub_节点ID"
        />
        <small class="form-text text-muted">
          PostgreSQL 发布名称，留空时按组件自动生成；组件会自动创建并维护该
          publication
        </small>
      </el-form-item>

      <!-- replication slot 名称 -->
      <el-form-item label="Replication Slot 名称">
        <el-input
          id="slotName"
          name="slotName"
          v-model="formData.slotName"
          placeholder="留空自动生成，例如：datay_slot_节点ID"
        />
        <small class="form-text text-muted">
          逻辑复制槽名称，留空时按组件自动生成；断点续传依赖该槽位，任务重建后请保持一致
        </small>
      </el-form-item>

      <!-- 起始 LSN -->
      <el-form-item label="起始 LSN">
        <el-input
          id="startLsn"
          name="startLsn"
          v-model="formData.startLsn"
          placeholder="例如：0/16B3748，为空表示从槽位确认位点开始"
        />
        <small class="form-text text-muted">
          指定从哪个 LSN
          开始采集（可选），一般无需填写，组件会自动从上次保存的位点续传
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
    </div>
  </el-form>
</template>

<script setup>
import { reactive } from "vue";
import { defineProps, defineEmits } from "vue";
import { ElMessage } from "element-plus";
import DataSourceSelector from "@/components/DataSourceSelector.vue";

const props = defineProps({
  node: { type: Object, default: undefined },
});

const emits = defineEmits(["save"]);

const formData = reactive({
  sourceId: props.node?.data?.config?.sourceId || "",
  schema: props.node?.data?.config?.schema || "",
  tableNamePattern: props.node?.data?.config?.tableNamePattern || "",
  publicationName: props.node?.data?.config?.publicationName || "",
  slotName: props.node?.data?.config?.slotName || "",
  startLsn: props.node?.data?.config?.startLsn || "",
  snapshot: props.node?.data?.config?.snapshot || false,
  snapshotFetchSize: props.node?.data?.config?.snapshotFetchSize || 10000,
});

const handleDataSourceSelected = (selectedData) => {
  formData.sourceId = selectedData.dataSourceId;
  formData.schema = selectedData.schema;
};

const saveConfig = async () => {
  if (!formData.sourceId) {
    ElMessage.error("请选择数据源");
    return;
  }
  if (!formData.schema) {
    ElMessage.error("请选择 schema");
    return;
  }
  emits("save", formData);
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
