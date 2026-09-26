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
            <el-input v-model="dataApi.name" placeholder="例如：订单查询服务" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="服务编码" required>
            <el-input v-model="dataApi.code" placeholder="调用地址中的唯一标识，如 order_query" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="数据来源" required>
            <el-select v-model="dataApi.sourceType" style="width: 100%" @change="onSourceTypeChange">
            <el-option v-for="opt in sourceTypeOptions" :key="opt.value" :value="opt.value" :label="opt.label" />
          </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态" required>
            <el-select v-model="dataApi.status" style="width: 100%">
            <el-option v-for="opt in statusOptions" :key="opt.value" :value="opt.value" :label="opt.label" />
          </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="描述">
        <el-input type="textarea" v-model="dataApi.description" :rows="2" placeholder="服务用途、返回说明等（选填）" />
      </el-form-item>

      <el-row :gutter="20" v-if="dataApi.sourceType !== SOURCE_TYPE_API">
        <el-col :span="24">
          <el-form-item label="数据源" required>
            <el-select :model-value="dataApi.dataSourceId || ''" style="width: 100%" @change="onDataSourceChange" placeholder="请选择数据源">
            <el-option v-for="ds in dataSources" :key="ds.id" :value="ds.id" :label="`${ds.name}（${ds.type}）`" />
          </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <div v-if="dataApi.sourceType === SOURCE_TYPE_TABLE" class="source-block">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="Schema" required>
              <el-select :model-value="dataApi.schemaName || ''" :disabled="!dataApi.dataSourceId" style="width: 100%" @change="onSchemaChange" :placeholder="loadingSchemas ? '加载中...' : '请选择 Schema'">
            <el-option v-for="schema in schemas" :key="schema" :value="schema" :label="schema" />
          </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="数据表" required>
              <el-select :model-value="dataApi.tableName || ''" :disabled="!dataApi.schemaName" style="width: 100%" @change="onTableChange" :placeholder="loadingTables ? '加载中...' : '请选择数据表'">
            <el-option v-for="table in tables" :key="table" :value="table" :label="table" />
          </el-select>
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

      <div v-else-if="dataApi.sourceType === SOURCE_TYPE_SQL" class="source-block">
        <el-form-item label="自定义 SQL" required>
          <el-input type="textarea" class="code-area" v-model="dataApi.sqlText" :rows="10" placeholder="只读 SQL，例如：SELECT * FROM orders WHERE status = ${status}" />
        </el-form-item>
        <div class="filter-hint">
          <font-awesome-icon icon="exclamation-circle" class="mr-1"></font-awesome-icon>
          <span>
            SQL 中可使用 <code>$&#123;param&#125;</code> 占位符，调用时同名参数将作为预编译参数安全绑定；服务仅允许 SELECT 等只读语句。
          </span>
        </div>
      </div>

      <div v-else-if="dataApi.sourceType === SOURCE_TYPE_API" class="source-block">
        <div class="curl-import">
          <el-button size="small" @click="showCurlImport = !showCurlImport">
            {{ showCurlImport ? '收起 cURL 导入' : '从 cURL 导入' }}
          </el-button>
          <span class="curl-import-tip">粘贴调用该已有 API 的 cURL 命令，自动填充请求地址、方法、请求头和请求体</span>
          <div v-if="showCurlImport" class="curl-import-panel">
            <el-input type="textarea" class="code-area" v-model="curlText" :rows="4" placeholder="例如: curl -X GET 'https://api.example.com/orders?status=1' -H 'Authorization: Bearer xxx'" />
            <div class="curl-import-actions">
              <el-button type="primary" size="small" @click="applyCurl('main')">解析并填充</el-button>
              <el-button size="small" @click="clearCurlImport">取消</el-button>
            </div>
          </div>
        </div>

        <el-row :gutter="20">
          <el-col :span="18">
            <el-form-item label="请求地址" required>
              <el-input v-model="apiConfig.url" placeholder="https://api.example.com/orders" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="请求方法" required>
              <el-select v-model="apiConfig.method" style="width: 100%">
            <el-option v-for="method in httpMethods" :key="method" :value="method" :label="method" />
          </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="超时(ms)">
              <el-input-number :controls="false" v-model="apiConfig.timeout" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="SSL 校验">
              <el-checkbox v-model="apiConfig.sslVerify">启用 SSL 证书校验</el-checkbox>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="请求头">
          <el-input type="textarea" class="code-area" v-model="apiHeadersText" :rows="3" placeholder='JSON 格式，如 {"Authorization": "Bearer ${token}"}' />
        </el-form-item>

        <el-form-item label="请求体" v-if="hasApiBody">
          <el-input type="textarea" class="code-area" v-model="apiConfig.body" :rows="4" placeholder='JSON 格式，如 {"status": "${status}"}' />
        </el-form-item>

        <el-form-item label="数据提取路径">
          <el-input v-model="apiConfig.jsonPath" placeholder="如 $.data.list（留空则返回完整响应）" />
          <div class="filter-hint">
            <font-awesome-icon icon="exclamation-circle" class="mr-1"></font-awesome-icon>
            使用 JSONPath 从响应中提取数据，仅将提取结果返回给调用方；留空时返回完整响应。
          </div>
        </el-form-item>

        <div class="pre-process">
          <div class="pre-process-header">
            <el-checkbox v-model="apiConfig.preProcess.enabled">启用前置处理（先调用接口获取 Token）</el-checkbox>
          </div>
          <div v-if="apiConfig.preProcess.enabled" class="pre-process-body">
            <div class="curl-import">
              <el-button size="small" @click="showPreCurlImport = !showPreCurlImport">
                {{ showPreCurlImport ? '收起 cURL 导入' : '从 cURL 导入' }}
              </el-button>
              <span class="curl-import-tip">粘贴获取 Token 的 cURL 命令，自动填充前置请求地址、方法、请求头和请求体</span>
              <div v-if="showPreCurlImport" class="curl-import-panel">
                <el-input type="textarea" class="code-area" v-model="preCurlText" :rows="4" placeholder="例如: curl -X POST 'https://auth.example.com/token' -H 'Content-Type: application/json' -d '{&quot;appId&quot;:&quot;xxx&quot;}'" />
                <div class="curl-import-actions">
                  <el-button type="primary" size="small" @click="applyCurl('pre')">解析并填充</el-button>
                  <el-button size="small" @click="clearPreCurlImport">取消</el-button>
                </div>
              </div>
            </div>

            <el-row :gutter="20">
              <el-col :span="18">
                <el-form-item label="前置地址">
                  <el-input v-model="apiConfig.preProcess.url" placeholder="https://auth.example.com/token" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="请求方法">
                  <el-select v-model="apiConfig.preProcess.method" style="width: 100%">
            <el-option v-for="method in httpMethods" :key="method" :value="method" :label="method" />
          </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="前置请求头">
              <el-input type="textarea" class="code-area" v-model="preHeadersText" :rows="3" placeholder='JSON 格式，如 {"Content-Type": "application/json"}' />
            </el-form-item>

            <el-form-item label="前置请求体">
              <el-input type="textarea" class="code-area" v-model="apiConfig.preProcess.body" :rows="3" placeholder='JSON 格式，如 {"appId": "xxx", "secret": "yyy"}' />
            </el-form-item>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="Token 路径">
                  <el-input v-model="apiConfig.preProcess.tokenPath" placeholder="如 $.data.accessToken" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="缓存秒数">
                  <el-input-number :controls="false" v-model="apiConfig.preProcess.cacheTtlSeconds" />
                </el-form-item>
              </el-col>
            </el-row>

            <div class="filter-hint">
              <font-awesome-icon icon="exclamation-circle" class="mr-1"></font-awesome-icon>
              主请求地址、请求头、请求体中均可使用 <code>$&#123;param&#125;</code> 占位符引用调用参数，使用
              <code>$&#123;token&#125;</code> 引用前置接口获取的 Token；Token 路径留空时直接使用响应文本。
            </div>
          </div>
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

.curl-import {
  margin-bottom: 16px;
  padding: 12px;
  border: 1px dashed #c0c4cc;
  border-radius: 4px;
  background-color: #fafafa;
}

.curl-import-tip {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}

.curl-import-panel {
  margin-top: 10px;
}

.curl-import-actions {
  margin-top: 8px;
  text-align: right;
}

.pre-process {
  margin: 4px 0 16px;
  padding: 12px 16px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fff;
}

.pre-process-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.inline-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 14px;
  font-weight: normal;
  color: #606266;
  cursor: pointer;
}

.inline-toggle input[type='checkbox'] {
  width: 16px;
  height: 16px;
  margin: 0;
  cursor: pointer;
}

.pre-process-body {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #e4e7ed;
}
</style>
