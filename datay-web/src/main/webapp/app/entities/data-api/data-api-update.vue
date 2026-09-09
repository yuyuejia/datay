<template>
  <div class="data-api-update-container">
    <div class="page-header">
      <h3>{{ isEdit ? '编辑数据服务' : '新建数据服务' }}</h3>
      <div>
        <el-button @click="previousState()">取消</el-button>
        <el-button type="primary" @click="save()" :loading="isSaving">保存</el-button>
      </div>
    </div>

    <el-form :model="dataApi" label-width="110px" class="data-api-form">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="服务名称" required>
            <b-form-input v-model="dataApi.name" placeholder="例如：订单查询服务" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="服务编码" required>
            <b-form-input v-model="dataApi.code" placeholder="调用地址中的唯一标识，如 order_query" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="数据来源" required>
            <select
              class="form-control"
              v-model="dataApi.sourceType"
              style="width: 100%"
              @change="onSourceTypeChange"
            >
              <option v-for="opt in sourceTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态" required>
            <select class="form-control" v-model="dataApi.status" style="width: 100%">
              <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="描述">
        <textarea
          class="form-control"
          v-model="dataApi.description"
          :rows="2"
          placeholder="服务用途、返回说明等（选填）"
        ></textarea>
      </el-form-item>

      <el-row :gutter="20">
        <el-col :span="24">
          <el-form-item label="数据源" required>
            <select
              class="form-control"
              :value="dataApi.dataSourceId || ''"
              style="width: 100%"
              @change="onDataSourceChange"
            >
              <option value="" disabled>请选择数据源</option>
              <option v-for="ds in dataSources" :key="ds.id" :value="ds.id">
                {{ ds.name }}（{{ ds.type }}）
              </option>
            </select>
          </el-form-item>
        </el-col>
      </el-row>

      <div v-if="dataApi.sourceType === SOURCE_TYPE_TABLE" class="source-block">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="Schema" required>
              <select
                class="form-control"
                :value="dataApi.schemaName || ''"
                :disabled="!dataApi.dataSourceId"
                style="width: 100%"
                @change="onSchemaChange"
              >
                <option value="" disabled>{{ loadingSchemas ? '加载中...' : '请选择 Schema' }}</option>
                <option v-for="schema in schemas" :key="schema" :value="schema">{{ schema }}</option>
              </select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="数据表" required>
              <select
                class="form-control"
                :value="dataApi.tableName || ''"
                :disabled="!dataApi.schemaName"
                style="width: 100%"
                @change="onTableChange"
              >
                <option value="" disabled>{{ loadingTables ? '加载中...' : '请选择数据表' }}</option>
                <option v-for="table in tables" :key="table" :value="table">{{ table }}</option>
              </select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <div class="table-field-count">
              <span v-if="loadingColumns">正在加载字段...</span>
              <span v-else-if="columns.length > 0">共 {{ columns.length }} 个字段</span>
              <span v-else-if="dataApi.tableName">该表暂无字段信息</span>
              <span v-else>选择数据表后可查看字段</span>
            </div>
          </el-col>
        </el-row>

        <div v-if="columns.length > 0" class="filter-hint">
          <div class="filter-hint-title">
            <font-awesome-icon icon="exclamation-circle" class="mr-1"></font-awesome-icon>
            查询参数（调用时传入与字段同名的参数即按等值过滤；pageNum / pageSize 用于分页）
          </div>
          <div class="filter-tags">
            <el-tag v-for="col in columns" :key="col.name" size="small" type="info" class="filter-tag" :title="col.type">
              {{ col.name }}
            </el-tag>
          </div>
        </div>
      </div>

      <div v-else class="source-block">
        <el-form-item label="自定义 SQL" required>
          <textarea
            class="form-control code-area"
            v-model="dataApi.sqlText"
            :rows="10"
            placeholder="只读 SQL，例如：SELECT * FROM orders WHERE status = ${status}"
          ></textarea>
        </el-form-item>
        <div class="filter-hint">
          <font-awesome-icon icon="exclamation-circle" class="mr-1"></font-awesome-icon>
          <span>
            SQL 中可使用 <code>$&#123;param&#125;</code> 占位符，调用时同名参数将作为预编译参数安全绑定；服务仅允许 SELECT 等只读语句。
          </span>
        </div>
      </div>
    </el-form>
  </div>
</template>

<script lang="ts" src="./data-api-update.component.ts"></script>

<style scoped>
.data-api-update-container {
  padding: 20px;
  background: #fff;
  border-radius: 4px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.page-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.source-block {
  background: #f5f7fa;
  border-radius: 4px;
  padding: 12px 16px 4px;
  margin-bottom: 8px;
}

.table-field-count {
  line-height: 38px;
  color: #909399;
  font-size: 13px;
}

.filter-hint {
  margin: 0 0 12px;
  padding: 10px 12px;
  background: #ecf5ff;
  border-radius: 4px;
  color: #606266;
  font-size: 13px;
  line-height: 22px;
}

.filter-hint-title {
  margin-bottom: 8px;
}

.filter-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.filter-tag {
  margin-right: 0;
}

.code-area {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}
</style>
