<template>
  <div class="data-model-container">
    <div class="data-model-layout">
      <div class="tree-panel">
        <div class="tree-header">
          <h5>模型目录</h5>
          <div class="tree-actions">
            <el-button
              type="primary"
              size="small"
              @click="showAddDirectoryDialog(null)"
            >
              <font-awesome-icon icon="plus" /> 新增目录
            </el-button>
          </div>
        </div>
        <div class="tree-body">
          <el-tree
            ref="treeRef"
            :data="treeData"
            :props="treeProps"
            node-key="id"
            default-expand-all
            highlight-current
            :expand-on-click-node="true"
            @node-click="handleNodeClick"
          >
            <template #default="{ node, data }">
              <span class="custom-tree-node">
                <span v-if="data.type === 'directory'">
                  <font-awesome-icon
                    icon="folder"
                    class="tree-icon folder-icon"
                  />
                  <span>{{ node.label }}</span>
                </span>
                <span v-else>
                  <font-awesome-icon
                    icon="table"
                    class="tree-icon model-icon"
                  />
                  <span>{{ node.label }}</span>
                </span>
                <span class="tree-node-actions">
                  <el-button
                    v-if="data.type === 'directory'"
                    link
                    type="primary"
                    size="small"
                    @click.stop="showAddDirectoryDialog(data.id)"
                  >
                    <font-awesome-icon icon="plus" />
                  </el-button>
                  <el-button
                    v-if="data.type === 'directory'"
                    link
                    type="primary"
                    size="small"
                    @click.stop="showAddModelDialog(data.id)"
                  >
                    <font-awesome-icon icon="table" />
                  </el-button>
                  <el-button
                    link
                    type="danger"
                    size="small"
                    @click.stop="handleDeleteNode(data)"
                  >
                    <font-awesome-icon icon="trash" />
                  </el-button>
                </span>
              </span>
            </template>
          </el-tree>
        </div>
      </div>
      <div class="detail-panel">
        <div v-if="!selectedModel" class="empty-state">
          <font-awesome-icon icon="arrow-left" class="empty-icon" />
          <p>请从左侧目录树选择一个数据模型查看详情</p>
        </div>
        <div v-else class="model-detail">
          <div class="detail-header">
            <h4>{{ selectedModel.name }}</h4>
            <div class="detail-actions">
              <el-button type="warning" size="small" @click="openMaterializeDialog">
                <font-awesome-icon icon="database" /> 物化
              </el-button>
              <router-link
                :to="{
                  name: 'DataModelEdit',
                  params: { dataModelId: selectedModel.id },
                }"
                custom
                v-slot="{ navigate }"
              >
                <el-button type="primary" size="small" @click="navigate">
                  <font-awesome-icon icon="pencil-alt" /> 编辑
                </el-button>
              </router-link>
              <el-button
                type="danger"
                size="small"
                @click="prepareRemove(selectedModel)"
              >
                <font-awesome-icon icon="trash" /> 删除
              </el-button>
            </div>
          </div>
          <div class="detail-info">
            <el-form
              :model="selectedModel"
              label-width="100px"
              class="model-detail-form"
            >
              <el-row :gutter="20">
                <el-col :span="12">
                  <el-form-item label="模型名称">
                    <span class="detail-value">{{ selectedModel.name }}</span>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="模型编码">
                    <span class="detail-value">{{ selectedModel.code || '-' }}</span>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :span="12">
                  <el-form-item label="模型类型">
                    <span class="detail-value">
                      <el-tag
                        :type="selectedModel.modelType === 'FACT' ? 'danger' : 'primary'"
                        size="small"
                      >
                        {{ selectedModel.modelType === 'FACT' ? '事实表' : '维度表' }}
                      </el-tag>
                    </span>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="是否注册">
                    <span class="detail-value">
                      <el-tag
                        :type="selectedModel.isRegistered ? 'success' : 'info'"
                        size="small"
                      >
                        {{ selectedModel.isRegistered ? '是' : '否' }}
                      </el-tag>
                    </span>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :span="12">
                  <el-form-item label="创建时间">
                    <span class="detail-value">{{ formatDateLong(selectedModel.createTime) || '-' }}</span>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="更新时间">
                    <span class="detail-value">{{ formatDateLong(selectedModel.updateTime) || '-' }}</span>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :span="24">
                  <el-form-item label="描述">
                    <span class="detail-value detail-description">{{ selectedModel.description || '-' }}</span>
                  </el-form-item>
                </el-col>
              </el-row>
            </el-form>
          </div>
          <div class="detail-fields">
            <h5>模型字段</h5>
            <el-table
              :data="modelFields"
              border
              style="width: 100%"
              v-loading="fieldsLoading"
            >
              <el-table-column prop="fieldName" label="字段名称" width="180" />
              <el-table-column prop="fieldType" label="字段类型" width="120">
                <template #default="{ row }">
                  <el-tag size="small">{{
                    getFieldTypeLabel(row.fieldType)
                  }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="长度" width="80">
                <template #default="{ row }">
                  <span v-if="needsLength(row.fieldType)"
                    >{{ row.fieldLength ?? "-" }}</span
                  >
                  <span v-else class="muted">-</span>
                </template>
              </el-table-column>
              <el-table-column label="精度" width="80">
                <template #default="{ row }">
                  <span v-if="needsPrecision(row.fieldType)"
                    >{{ row.fieldPrecision ?? "-" }}</span
                  >
                  <span v-else class="muted">-</span>
                </template>
              </el-table-column>
              <el-table-column label="小数位" width="80">
                <template #default="{ row }">
                  <span v-if="needsScale(row.fieldType)"
                    >{{ row.fieldScale ?? "-" }}</span
                  >
                  <span v-else class="muted">-</span>
                </template>
              </el-table-column>
              <el-table-column
                prop="description"
                label="描述"
                min-width="200"
              />
              <el-table-column label="主键" width="80">
                <template #default="{ row }">
                  <el-tag v-if="row.isPrimaryKey" type="success" size="small"
                    >是</el-tag
                  >
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="关联维度" width="180">
                <template #default="{ row }">
                  <span v-if="row.dimensionModelId">{{
                    row._dimensionModelName || row.dimensionModelId
                  }}</span>
                  <span v-else>-</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </div>
    </div>

    <b-modal
      v-model="directoryDialogVisible"
      :title="directoryDialogTitle"
      size="md"
    >
      <div class="modal-body">
        <form name="directoryForm" novalidate @submit.prevent="saveDirectory">
          <div class="form-group">
            <label class="form-control-label">目录名称</label>
            <input
              type="text"
              class="form-control"
              v-model="directoryForm.name"
              placeholder="请输入目录名称"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label">排序</label>
            <input
              type="number"
              class="form-control"
              v-model.number="directoryForm.sortOrder"
              min="0"
              max="9999"
            />
          </div>
        </form>
      </div>
      <template #modal-footer>
        <div>
          <button
            type="button"
            class="btn btn-secondary"
            @click="directoryDialogVisible = false"
          >
            取消
          </button>
          <button type="button" class="btn btn-primary" @click="saveDirectory">
            确定
          </button>
        </div>
      </template>
    </b-modal>

    <b-modal v-model="deleteDialogVisible" title="确认删除" size="md">
      <div class="modal-body">
        <p>{{ deleteMessage }}</p>
      </div>
      <template #modal-footer>
        <div>
          <button
            type="button"
            class="btn btn-secondary"
            @click="deleteDialogVisible = false"
          >
            取消
          </button>
          <button type="button" class="btn btn-danger" @click="confirmDelete">
            确认删除
          </button>
        </div>
      </template>
    </b-modal>

    <b-modal
      v-model="materializeDialogVisible"
      title="模型物化"
      size="lg"
      class="materialize-modal"
    >
      <div class="modal-body">
        <el-form label-width="100px" size="default">
          <el-form-item label="目标数据源" required>
            <select
              class="form-control"
              v-model="materializeForm.dataSourceId"
              style="width: 300px"
              @change="onDataSourceChange"
            >
              <option :value="null" disabled>请选择数据源</option>
              <option
                v-for="ds in dataSources"
                :key="ds.id"
                :value="ds.id"
              >{{ ds.name }} ({{ ds.type }})</option>
            </select>
          </el-form-item>
          <el-form-item label="Schema">
            <select
              v-if="materializeSchemas.length > 0"
              class="form-control"
              v-model="materializeForm.schemaName"
              style="width: 300px"
            >
              <option
                v-for="schema in materializeSchemas"
                :key="schema"
                :value="schema"
              >{{ schema }}</option>
            </select>
            <b-form-input
              v-else
              v-model="materializeForm.schemaName"
              placeholder="默认Schema"
              style="width: 300px"
            />
          </el-form-item>
          <el-form-item label="表名" required>
            <b-form-input
              v-model="materializeForm.tableName"
              placeholder="请输入表名"
              style="width: 300px"
            />
            <div v-if="materializeTableExists" class="materialize-warning">
              <font-awesome-icon icon="exclamation-triangle" />
              目标数据源中已存在同名表，物化将覆盖该表！
            </div>
          </el-form-item>
        </el-form>

        <div class="materialize-section">
          <div class="materialize-section-header">
            <span>字段类型映射（可调整）</span>
            <el-button
              link
              type="primary"
              size="small"
              @click="previewMaterializeDDL"
            >
              <font-awesome-icon icon="eye" /> 预览DDL
            </el-button>
          </div>
          <el-table
            :data="materializeFields"
            border
            size="small"
            v-loading="materializeLoading"
            max-height="300"
          >
            <el-table-column prop="fieldName" label="字段名" width="140" />
            <el-table-column prop="logicalType" label="逻辑类型" width="100">
              <template #default="{ row }">
                <el-tag size="small">{{ row.logicalType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="物理类型" width="180">
              <template #default="{ row }">
                <select
                  class="form-control form-control-sm"
                  v-model="row.physicalType"
                  style="width: 100%"
                >
                  <option v-if="!row.physicalType" value="" disabled>选择类型</option>
                  <option
                    v-for="pt in materializePhysicalTypes"
                    :key="pt"
                    :value="pt"
                  >{{ pt }}</option>
                </select>
              </template>
            </el-table-column>
            <el-table-column label="长度" width="100">
              <template #default="{ row }">
                <b-form-input
                  v-if="needsMaterializeLength(row.logicalType)"
                  v-model="row.fieldLength"
                  type="number"
                  size="sm"
                  min="0"
                  max="65535"
                  style="width: 90px"
                />
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="精度" width="100">
              <template #default="{ row }">
                <b-form-input
                  v-if="needsMaterializePrecision(row.logicalType)"
                  v-model="row.fieldPrecision"
                  type="number"
                  size="sm"
                  min="0"
                  max="38"
                  style="width: 90px"
                />
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="小数位" width="100">
              <template #default="{ row }">
                <b-form-input
                  v-if="needsMaterializeScale(row.logicalType)"
                  v-model="row.fieldScale"
                  type="number"
                  size="sm"
                  min="0"
                  max="38"
                  style="width: 90px"
                />
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="主键" width="70">
              <template #default="{ row }">
                <input
                  type="checkbox"
                  :checked="!!row.isPrimaryKey"
                  @change="row.isPrimaryKey = !!$event.target.checked"
                  style="width: 16px; height: 16px; cursor: pointer; margin: 0;"
                />
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="150" />
          </el-table>
        </div>

        <div v-if="materializeDDLPreview" class="materialize-ddl-preview">
          <div class="materialize-ddl-header">
            <span>DDL 预览</span>
            <el-button
              link
              size="small"
              @click="materializeDDLPreview = ''"
            >
              <font-awesome-icon icon="times" />
            </el-button>
          </div>
          <pre class="ddl-code">{{ materializeDDLPreview }}</pre>
        </div>
      </div>
      <template #modal-footer>
        <div>
          <button
            type="button"
            class="btn btn-secondary"
            @click="materializeDialogVisible = false"
          >
            取消
          </button>
          <button
            type="button"
            class="btn btn-primary"
            :disabled="materializeLoading || !canMaterialize"
            @click="confirmMaterialize"
          >
            <font-awesome-icon icon="database" />
            执行物化
          </button>
        </div>
      </template>
    </b-modal>
  </div>
</template>

<script lang="ts" src="./data-model.component.ts"></script>

<style scoped>
.data-model-container {
  height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
}

.data-model-layout {
  display: flex;
  height: 100%;
  gap: 0;
}

.tree-panel {
  width: 320px;
  min-width: 320px;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.tree-header {
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tree-header h5 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.tree-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  padding-right: 8px;
}

.tree-icon {
  margin-right: 6px;
}

.folder-icon {
  color: #e6a23c;
}

.model-icon {
  color: #409eff;
}

.tree-node-actions {
  display: none;
}

.custom-tree-node:hover .tree-node-actions {
  display: inline-flex;
}

.detail-panel {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background: #f5f7fa;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.model-detail {
  background: #fff;
  border-radius: 4px;
  padding: 24px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.detail-header h4 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.detail-info {
  margin-bottom: 24px;
}

.detail-fields h5 {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
}

.muted {
  color: #c0c4cc;
}

.model-detail-form :deep(.el-form-item__content) {
  line-height: 28px;
}

.detail-value {
  font-size: 14px;
  color: #303133;
}

.detail-description {
  display: block;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.6;
}

.materialize-warning {
  margin-top: 8px;
  color: #e6a23c;
  font-size: 13px;
}

.materialize-section {
  margin-top: 20px;
}

.materialize-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 600;
}

.materialize-ddl-preview {
  margin-top: 16px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #1e1e1e;
  overflow: hidden;
}

.materialize-ddl-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #323232;
  color: #fff;
  font-size: 13px;
}

.ddl-code {
  margin: 0;
  padding: 12px;
  color: #d4d4d4;
  font-family: "Consolas", "Monaco", "Courier New", monospace;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
  max-height: 300px;
}
</style>