<template>
  <el-form name="editForm" label-position="top">
    <el-form-item label="数据源">
      <DataSourceSelector
        type="source"
        :datasourceId="formData.sourceId"
        :schema="formData.schema"
        dataSourceType="MONGODB"
        @selected="handleDataSourceSelected"
      />
      <small class="form-text text-muted"> 选择 MongoDB 数据源，Schema 即目标数据库 </small>
    </el-form-item>
    <el-form-item label="集合名">
      <el-input id="collection" name="collection" v-model="formData.collection" placeholder="例如：users" />
      <small class="form-text text-muted"> 要读取的 MongoDB 集合名 </small>
    </el-form-item>
    <el-form-item label="过滤条件 (filter, JSON)">
      <el-input
        id="filter"
        name="filter"
        type="textarea"
        :rows="3"
        v-model="formData.filter"
        placeholder='例如：{"status":"active","age":{"$gt":18}}'
      />
      <small class="form-text text-muted"> 可选，MongoDB 查询过滤条件，遵循 find 的 filter 语法 </small>
    </el-form-item>
    <el-form-item label="投影 (projection, JSON)">
      <el-input
        id="projection"
        name="projection"
        type="textarea"
        :rows="2"
        v-model="formData.projection"
        placeholder='例如：{"name":1,"age":1}'
      />
      <small class="form-text text-muted"> 可选，选择返回字段，1 表示包含、0 表示排除 </small>
    </el-form-item>
    <el-form-item label="排序 (sort, JSON)">
      <el-input id="sort" name="sort" v-model="formData.sort" placeholder='例如：{"createdAt":-1}' />
      <small class="form-text text-muted"> 可选，排序规则，1 升序、-1 降序 </small>
    </el-form-item>
    <el-form-item label="读取上限 (limit)">
      <el-input id="limit" name="limit" v-model="formData.limit" placeholder="可选，0 或留空表示不限制" />
    </el-form-item>
    <el-form-item label="增量字段">
      <el-input id="incrColumn" name="incrColumn" v-model="formData.incrColumn" placeholder="可选，数值类型字段，如 id" />
      <small class="form-text text-muted"> 填写后按该数值字段增量同步（{$gt: 上次最大值}） </small>
    </el-form-item>
    <el-form-item label="批大小 (batchSize)">
      <el-input id="batchSize" name="batchSize" v-model="formData.batchSize" placeholder="默认 1000" />
    </el-form-item>
  </el-form>
</template>

<script setup>
import { reactive } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';
import DataSourceSelector from '@/components/DataSourceSelector.vue';

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save']);

const formData = reactive({
  sourceId: props.node?.data?.config?.sourceId,
  schema: props.node?.data?.config?.schema || '',
  collection: props.node?.data?.config?.collection || '',
  filter: props.node?.data?.config?.filter || '',
  projection: props.node?.data?.config?.projection || '',
  sort: props.node?.data?.config?.sort || '',
  limit: props.node?.data?.config?.limit || '',
  incrColumn: props.node?.data?.config?.incrColumn || '',
  batchSize: props.node?.data?.config?.batchSize || '1000',
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
  if (!formData.collection) {
    ElMessage.error('请填写集合名');
    return;
  }
  emits('save', {
    sourceId: formData.sourceId,
    schema: formData.schema,
    collection: formData.collection,
    filter: formData.filter,
    projection: formData.projection,
    sort: formData.sort,
    limit: formData.limit,
    incrColumn: formData.incrColumn,
    batchSize: formData.batchSize,
  });
};

defineExpose({ saveConfig });
</script>

<style scoped>
.form-text {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
</style>
