<template>
  <div class="data-api-update-container">
    <div class="page-header">
      <h3>{{ isEdit ? '编辑数据服务' : '新建数据服务' }}</h3>
      <div>
        <el-button @click="previousState()">取消</el-button>
        <el-button type="success" plain @click="openDebug()">
          <font-awesome-icon icon="play" class="mr-1"></font-awesome-icon>
          <span>接口调试</span>
        </el-button>
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
        <el-col :span="24">
          <el-form-item label="数据来源" required>
            <el-radio-group v-model="dataApi.sourceType" class="source-type-group" @change="onSourceTypeChange">
              <el-radio-button v-for="opt in sourceTypeOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="状态" required>
            <el-select v-model="dataApi.status" style="width: 100%">
              <el-option v-for="opt in statusOptions" :key="opt.value" :value="opt.value" :label="opt.label" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="所属目录">
            <el-tree-select
              v-model="dataApi.directoryId"
              :data="directoryTreeOptions"
              :props="{ label: 'label', children: 'children' }"
              node-key="id"
              check-strictly
              clearable
              placeholder="未分类"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="描述">
        <el-input type="textarea" v-model="dataApi.description" :rows="2" placeholder="服务用途、返回说明等（选填）" />
      </el-form-item>

      <el-row :gutter="20" v-if="dataApi.sourceType !== SOURCE_TYPE_API && dataApi.sourceType !== SOURCE_TYPE_DATASET">
        <el-col :span="24">
          <el-form-item label="数据源" required>
            <el-select
              :model-value="dataApi.dataSourceId || ''"
              style="width: 100%"
              @change="onDataSourceChange"
              placeholder="请选择数据源"
            >
              <el-option v-for="ds in dataSources" :key="ds.id" :value="ds.id" :label="`${ds.name}（${ds.type}）`" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <div v-if="dataApi.sourceType === SOURCE_TYPE_DATASET" class="source-block">
        <el-form-item label="分析数据集" required>
          <el-select v-model="dataApi.datasetId" style="width: 100%" filterable placeholder="请选择要发布的分析数据集">
            <el-option
              v-for="dataset in datasets"
              :key="dataset.id"
              :value="dataset.id"
              :label="`${dataset.name}（${dataset.type === 'SQL' ? 'SQL 数据集' : '指标数据集'}）`"
            />
          </el-select>
        </el-form-item>
        <div v-if="selectedDataset" class="filter-hint">
          <div class="filter-hint-title">
            <font-awesome-icon icon="exclamation-circle" class="mr-1"></font-awesome-icon>
            {{ selectedDataset.type === 'SQL' ? 'SQL 数据集' : '指标数据集' }}
            <span v-if="selectedDataset.description"> · {{ selectedDataset.description }}</span>
          </div>
          <span v-if="selectedDataset.type === 'SQL'">
            SQL 中声明的 <code>$&#123;param&#125;</code> 参数需要在调用时全部传入；另支持 pageNum / pageSize 分页。
          </span>
          <span v-else>
            按数据集保存的指标、维度与时间范围执行；调用时可传 start / end 覆盖时间范围，另支持 pageNum / pageSize 分页。
          </span>
        </div>
      </div>

      <div v-if="dataApi.sourceType === SOURCE_TYPE_TABLE" class="source-block">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="Schema" required>
              <el-select
                :model-value="dataApi.schemaName || ''"
                :disabled="!dataApi.dataSourceId"
                style="width: 100%"
                @change="onSchemaChange"
                :placeholder="loadingSchemas ? '加载中...' : '请选择 Schema'"
              >
                <el-option v-for="schema in schemas" :key="schema" :value="schema" :label="schema" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="数据表" required>
              <el-select
                :model-value="dataApi.tableName || ''"
                :disabled="!dataApi.schemaName"
                style="width: 100%"
                @change="onTableChange"
                :placeholder="loadingTables ? '加载中...' : '请选择数据表'"
              >
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
          <el-input
            type="textarea"
            class="code-area"
            v-model="dataApi.sqlText"
            :rows="10"
            placeholder="只读 SQL，例如：SELECT * FROM orders WHERE status = ${status}"
          />
        </el-form-item>
        <div class="filter-hint">
          <font-awesome-icon icon="exclamation-circle" class="mr-1"></font-awesome-icon>
          <span>
            SQL 中可使用 <code>$&#123;param&#125;</code> 占位符，调用时同名参数将作为预编译参数安全绑定，未传参数时按 SQL NULL
            处理；服务仅允许 SELECT 等只读语句。
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
            <el-input
              type="textarea"
              class="code-area"
              v-model="curlText"
              :rows="4"
              placeholder="例如: curl -X GET 'https://api.example.com/orders?status=1' -H 'Authorization: Bearer xxx'"
            />
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
          <el-input
            type="textarea"
            class="code-area"
            v-model="apiHeadersText"
            :rows="3"
            placeholder='JSON 格式，如 {"Authorization": "Bearer ${token}"}'
          />
        </el-form-item>

        <el-form-item label="请求体" v-if="hasApiBody">
          <el-input
            type="textarea"
            class="code-area"
            v-model="apiConfig.body"
            :rows="4"
            placeholder='JSON 格式，如 {"status": "${status}"}'
          />
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
            <span class="pre-process-title">前置处理</span>
            <el-radio-group v-model="preProcessMode" size="small">
              <el-radio-button value="none">不启用</el-radio-button>
              <el-radio-button value="http">接口获取 Token</el-radio-button>
              <el-radio-button value="script">JavaScript 脚本</el-radio-button>
            </el-radio-group>
          </div>

          <div v-if="preProcessMode === 'http'" class="pre-process-body">
            <div class="curl-import">
              <el-button size="small" @click="showPreCurlImport = !showPreCurlImport">
                {{ showPreCurlImport ? '收起 cURL 导入' : '从 cURL 导入' }}
              </el-button>
              <span class="curl-import-tip">粘贴获取 Token 的 cURL 命令，自动填充前置请求地址、方法、请求头和请求体</span>
              <div v-if="showPreCurlImport" class="curl-import-panel">
                <el-input
                  type="textarea"
                  class="code-area"
                  v-model="preCurlText"
                  :rows="4"
                  placeholder="例如: curl -X POST 'https://auth.example.com/token' -H 'Content-Type: application/json' -d '{&quot;appId&quot;:&quot;xxx&quot;}'"
                />
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
              <el-input
                type="textarea"
                class="code-area"
                v-model="preHeadersText"
                :rows="3"
                placeholder='JSON 格式，如 {"Content-Type": "application/json"}'
              />
            </el-form-item>

            <el-form-item label="前置请求体">
              <el-input
                type="textarea"
                class="code-area"
                v-model="apiConfig.preProcess.body"
                :rows="3"
                placeholder='JSON 格式，如 {"appId": "xxx", "secret": "yyy"}'
              />
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

          <div v-else-if="preProcessMode === 'script'" class="pre-process-body">
            <div class="script-header">
              <span class="script-hint">脚本返回的对象会合并到调用参数中，供请求地址、请求头、请求体使用。</span>
              <el-button size="small" @click="fillScriptExample">填入示例</el-button>
            </div>

            <el-form-item label="处理脚本">
              <el-input
                type="textarea"
                class="code-area"
                v-model="apiConfig.preProcess.script"
                :rows="12"
                placeholder="return { token: 'Bearer ' + params.appId };"
              />
            </el-form-item>

            <div class="filter-hint">
              <div class="filter-hint-title">
                <font-awesome-icon icon="exclamation-circle" class="mr-1"></font-awesome-icon>
                脚本入参 <code>params</code> 为调用方传入的参数；返回对象中的键值会合并到参数上下文，同名时覆盖原参数。
              </div>
              <div>
                内置工具：<code>utils.md5</code>、<code>utils.sha1</code>、<code>utils.sha256</code>、
                <code>utils.hmacSha256</code>、<code>utils.base64Encode</code>、<code>utils.base64Decode</code>、
                <code>utils.urlEncode</code>、<code>utils.uuid</code>、<code>utils.now</code>、 <code>utils.formatDate</code>。
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-form>

    <el-drawer v-model="debugVisible" title="接口调试" size="72%" destroy-on-close>
      <div class="debug-body" v-loading="debugLoading">
        <el-alert
          v-if="dataApi.sourceType === SOURCE_TYPE_API"
          type="warning"
          :closable="false"
          show-icon
          title="调试会真实请求上游接口，POST / PUT / PATCH / DELETE 可能产生数据变更，请确认目标环境。"
          class="debug-alert"
        />
        <el-alert
          v-else
          type="info"
          :closable="false"
          show-icon
          title="调试会真实执行查询，请避免在生产库执行大范围查询。"
          class="debug-alert"
        />

        <div class="debug-section-header">
          <div>
            <span class="debug-section-title">调试参数</span>
            <span class="debug-section-hint">已识别 {{ debugDetectedNames.length }} 个可用参数，可修改或新增自定义参数</span>
          </div>
          <el-button size="small" @click="addDebugParam()">
            <font-awesome-icon icon="plus" class="mr-1"></font-awesome-icon>
            <span>新增参数</span>
          </el-button>
        </div>

        <el-table :data="debugParams" border size="small" class="debug-params-table">
          <el-table-column label="参数名" width="260">
            <template #default="{ row }">
              <el-select
                v-model="row.name"
                filterable
                allow-create
                default-first-option
                clearable
                placeholder="选择或输入参数名"
                style="width: 100%"
              >
                <el-option v-for="name in debugDetectedNames" :key="name" :label="name" :value="name" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="参数值">
            <template #default="{ row }">
              <el-input v-model="row.value" clearable placeholder="请输入调试值" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeDebugParam($index)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无参数，可直接发送请求或点击「新增参数」" />
          </template>
        </el-table>

        <div class="debug-actions">
          <el-button type="primary" :loading="debugLoading" @click="sendDebug()">
            <font-awesome-icon icon="play" class="mr-1"></font-awesome-icon>
            <span>发送调试请求</span>
          </el-button>
        </div>

        <el-alert v-if="debugError" :title="debugError" type="error" show-icon :closable="false" class="debug-error" />

        <div v-if="debugResult" class="debug-result">
          <div class="debug-summary">
            <el-tag :type="debugResult.code === 0 ? 'success' : 'danger'">code: {{ debugResult.code }}</el-tag>
            <span>耗时 {{ debugResult.durationMs ?? 0 }} ms</span>
            <span v-if="debugResult.code === 0 && debugResult.total !== undefined && debugResult.total !== null">
              共 {{ debugResult.total }} 条
            </span>
            <span v-if="debugResult.code === 0 && debugResult.pageNum">
              第 {{ debugResult.pageNum }} 页 / 每页 {{ debugResult.pageSize }} 条
            </span>
          </div>

          <el-tabs v-model="debugActiveTab">
            <el-tab-pane label="返回数据" name="data">
              <el-table v-if="debugHasTable" :data="debugRows" border stripe size="small" max-height="420" class="debug-result-table">
                <el-table-column
                  v-for="col in debugColumns"
                  :key="col"
                  :label="debugColumnLabel(col)"
                  min-width="120"
                  show-overflow-tooltip
                >
                  <template #default="{ row }">{{ formatDebugCell(row[col]) }}</template>
                </el-table-column>
              </el-table>
              <pre v-else-if="debugJson" class="debug-json">{{ debugJson }}</pre>
              <el-empty v-else description="暂无返回数据" />
            </el-tab-pane>

            <el-tab-pane v-if="debugShowRawBody" label="原始响应体" name="raw">
              <pre class="debug-json">{{ debugRawBody }}</pre>
            </el-tab-pane>

            <el-tab-pane v-if="dataApi.sourceType === SOURCE_TYPE_API" label="上游响应" name="upstream">
              <template v-if="debugUpstream">
                <div class="debug-summary">
                  <el-tag :type="(debugUpstream.status || 0) >= 200 && (debugUpstream.status || 0) < 300 ? 'success' : 'danger'">
                    HTTP {{ debugUpstream.status }}
                  </el-tag>
                  <span>耗时 {{ debugUpstream.durationMs ?? 0 }} ms</span>
                </div>
                <div class="debug-section-title">响应头</div>
                <el-table :data="debugUpstreamHeaders" border size="small" max-height="180">
                  <el-table-column prop="name" label="名称" width="220" />
                  <el-table-column prop="value" label="值" show-overflow-tooltip />
                  <template #empty>
                    <el-empty description="无响应头" />
                  </template>
                </el-table>
                <div class="debug-section-title">响应体</div>
                <pre class="debug-json">{{ debugUpstreamBody }}</pre>
              </template>
              <el-empty v-else description="本次调试没有上游响应" />
            </el-tab-pane>

            <el-tab-pane v-if="debugPreProcess" label="前置处理" name="preProcess">
              <pre class="debug-json">{{ debugPreProcessJson }}</pre>
            </el-tab-pane>
          </el-tabs>
        </div>
        <el-empty v-else-if="!debugLoading" description="填写参数后点击「发送调试请求」" />
      </div>
    </el-drawer>
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

.source-type-group {
  display: flex;
  flex-wrap: wrap;
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
  gap: 12px;
}

.pre-process-title {
  font-weight: 500;
  color: #303133;
}

.script-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.script-hint {
  color: #909399;
  font-size: 12px;
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

.debug-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.debug-alert {
  margin-bottom: 4px;
}

.debug-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.debug-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.debug-section-hint {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}

.debug-params-table {
  width: 100%;
}

.debug-actions {
  display: flex;
  justify-content: flex-end;
}

.debug-error {
  margin-top: 4px;
}

.debug-result {
  border-top: 1px solid #e4e7ed;
  padding-top: 12px;
}

.debug-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  color: #606266;
  font-size: 13px;
}

.debug-json {
  max-height: 420px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  color: #303133;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.debug-result-table {
  width: 100%;
}

.debug-result .debug-section-title {
  margin: 12px 0 8px;
}
</style>
