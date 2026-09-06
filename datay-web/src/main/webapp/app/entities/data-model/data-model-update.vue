<template>
  <div class="data-model-update-container">
    <div class="page-header">
      <h3>{{ isEdit ? "编辑数据模型" : "新增数据模型" }}</h3>
      <div>
        <el-button @click="previousState()">取消</el-button>
        <el-button type="primary" @click="save()" :loading="isSaving"
          >保存</el-button
        >
      </div>
    </div>

    <el-form :model="dataModel" label-width="100px" class="model-form">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="模型名称" required>
            <b-form-input v-model="dataModel.name" placeholder="请输入模型名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="模型编码" required>
            <b-form-input v-model="dataModel.code" placeholder="请输入模型编码" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="模型类型" required>
            <select
              class="form-control"
              v-model="dataModel.modelType"
              style="width: 100%"
            >
              <option value="" disabled>请选择模型类型</option>
              <option value="FACT">事实表</option>
              <option value="DIMENSION">维度表</option>
            </select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="是否注册">
            <select
              class="form-control"
              v-model="dataModel.isRegistered"
              style="width: 100%"
            >
              <option :value="null">请选择</option>
              <option :value="true">是</option>
              <option :value="false">否</option>
            </select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="所属目录">
            <select
              class="form-control"
              v-model="dataModel.directoryId"
              style="width: 100%"
            >
              <option :value="null">请选择所属目录</option>
              <template
                v-for="dir in flatDirectoryOptions"
                :key="'dir-' + dir.id"
              >
                <option :value="dir.id">
                  {{ "\u00A0\u00A0".repeat(dir.level) + dir.name }}
                </option>
              </template>
            </select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="24">
          <el-form-item label="描述">
            <textarea
              class="form-control"
              v-model="dataModel.description"
              :rows="3"
              placeholder="请输入模型描述"
            ></textarea>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <div class="fields-section">
      <div class="fields-header">
        <h4>模型字段</h4>
        <div class="fields-header-actions">
          <el-button type="primary" size="small" @click="addField">
            <font-awesome-icon icon="plus" /> 添加字段
          </el-button>
          <el-button type="success" size="small" @click="showImportDialog">
            <font-awesome-icon icon="file-import" /> 从已有表引入字段
          </el-button>
        </div>
      </div>
      <el-table :data="fields" border style="width: 100%" row-key="id">
        <el-table-column label="序号" width="60" align="center">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column label="字段名称" width="180">
          <template #default="{ row }">
            <b-form-input
              v-model="row.fieldName"
              size="sm"
              placeholder="字段名称"
            />
          </template>
        </el-table-column>
        <el-table-column label="字段类型" width="160">
          <template #default="{ row }">
            <select
              class="form-control form-control-sm"
              v-model="row.fieldType"
              style="width: 100%"
              @change="clearLengthAndPrecisionIfUnneeded(row)"
            >
              <option value="" disabled>字段类型</option>
              <option value="STRING">字符串(STRING)</option>
              <option value="TEXT">大文本(TEXT)</option>
              <option value="BINARY">二进制(BINARY)</option>
              <option value="BLOB">大对象(BLOB)</option>
              <option value="INTEGER">整数(INTEGER)</option>
              <option value="LONG">长整型(LONG)</option>
              <option value="DOUBLE">双精度(DOUBLE)</option>
              <option value="DECIMAL">高精度数值(DECIMAL)</option>
              <option value="DATE">日期(DATE)</option>
              <option value="DATETIME">日期时间(DATETIME)</option>
              <option value="BOOLEAN">布尔(BOOLEAN)</option>
            </select>
          </template>
        </el-table-column>
        <el-table-column label="长度" width="90">
          <template #default="{ row }">
            <b-form-input
              v-if="needsLength(row.fieldType)"
              v-model="row.fieldLength"
              type="number"
              size="sm"
              placeholder="长度"
            />
            <span v-else class="field-placeholder">-</span>
          </template>
        </el-table-column>
        <el-table-column label="精度" width="90">
          <template #default="{ row }">
            <b-form-input
              v-if="needsPrecision(row.fieldType)"
              v-model="row.fieldPrecision"
              type="number"
              size="sm"
              placeholder="精度"
            />
            <span v-else class="field-placeholder">-</span>
          </template>
        </el-table-column>
        <el-table-column label="小数位" width="90">
          <template #default="{ row }">
            <b-form-input
              v-if="needsScale(row.fieldType)"
              v-model="row.fieldScale"
              type="number"
              size="sm"
              placeholder="小数位"
            />
            <span v-else class="field-placeholder">-</span>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="180">
          <template #default="{ row }">
            <b-form-input
              v-model="row.description"
              size="sm"
              placeholder="字段描述"
            />
          </template>
        </el-table-column>
        <el-table-column label="主键" width="80" align="center">
          <template #default="{ row }">
            <input
              type="checkbox"
              :checked="!!row.isPrimaryKey"
              @change="row.isPrimaryKey = !!$event.target.checked; handlePrimaryKeyChange(row)"
              style="width: 16px; height: 16px; cursor: pointer; margin: 0;"
            />
          </template>
        </el-table-column>
        <el-table-column label="关联维度" width="220">
          <template #default="{ row }">
            <div class="dimension-select">
              <select
                class="form-control form-control-sm"
                v-model="row.dimensionModelId"
                style="width: 100%"
                @change="handleDimensionModelChange(row, $event.target.value)"
              >
                <option value="">选择维度模型</option>
                <option
                  v-for="dim in dimensionModels"
                  :key="dim.id"
                  :value="dim.id"
                >{{ dim.name }}</option>
              </select>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="排序" width="80">
          <template #default="{ row }">
            <el-input-number
              v-model="row.sortOrder"
              size="small"
              :min="0"
              controls-position="right"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ $index }">
            <el-button
              link
              type="danger"
              size="small"
              @click="removeField($index)"
            >
              <font-awesome-icon icon="trash" />
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <b-modal
      v-model="importDialogVisible"
      title="从已有表引入字段"
      size="lg"
    >
      <div class="modal-body">
        <div class="form-row">
          <div class="form-group col-md-4">
            <label class="form-control-label">数据源</label>
            <select
              class="form-control"
              v-model="importSelectedDataSourceId"
              @change="onImportDataSourceChange"
            >
              <option :value="null" disabled>请选择数据源</option>
              <option
                v-for="ds in importAvailableDataSources"
                :key="ds.id"
                :value="ds.id"
              >
                {{ ds.name }}
              </option>
            </select>
          </div>
          <div class="form-group col-md-4">
            <label class="form-control-label">Schema</label>
            <select
              class="form-control"
              v-model="importSelectedSchema"
              :disabled="!importSelectedDataSourceId"
              @change="onImportSchemaChange"
            >
              <option :value="null" disabled>请选择Schema</option>
              <option
                v-for="schema in importSchemas"
                :key="schema"
                :value="schema"
              >
                {{ schema }}
              </option>
            </select>
          </div>
          <div class="form-group col-md-4">
            <label class="form-control-label">数据表</label>
            <select
              class="form-control"
              v-model="importSelectedTable"
              :disabled="!importSelectedSchema"
              @change="onImportTableChange"
            >
              <option :value="null" disabled>请选择数据表</option>
              <option
                v-for="table in importTables"
                :key="table"
                :value="table"
              >
                {{ table }}
              </option>
            </select>
          </div>
        </div>
        <div v-if="importSourceFields.length > 0" class="import-table-wrapper" :class="{ loading: importFieldsLoading }">
          <table class="table table-bordered table-striped table-sm import-table">
            <thead>
              <tr>
                <th style="width: 50px; text-align: center;">
                  <input
                    type="checkbox"
                    :checked="isAllFieldsSelected"
                    @change="toggleAllFields"
                  />
                </th>
                <th style="width: 50px;">#</th>
                <th style="width: 180px;">字段名称</th>
                <th style="width: 70px;">主键</th>
                <th style="width: 180px;">字段类型</th>
                <th style="width: 80px;">长度</th>
                <th style="width: 80px;">精度</th>
                <th style="width: 80px;">小数位</th>
                <th>描述</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(field, index) in importSourceFields"
                :key="field.tempId"
              >
                <td style="text-align: center;">
                  <input
                    type="checkbox"
                    :checked="isFieldSelected(field)"
                    @change="toggleField(field)"
                  />
                </td>
                <td>{{ index + 1 }}</td>
                <td>{{ field.fieldName }}</td>
                <td style="text-align: center;">
                  <el-tag
                    v-if="field.isPrimaryKey"
                    type="success"
                    size="small"
                  >主键</el-tag>
                  <span v-else class="muted">-</span>
                </td>
                <td>
                  {{ field.fieldType }}
                  <span class="import-field-type-hint">({{ field._rawType || '' }})</span>
                </td>
                <td>{{ field.fieldLength }}</td>
                <td>{{ field.fieldPrecision }}</td>
                <td>{{ field.fieldScale }}</td>
                <td>{{ field.description }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else-if="importSelectedTable" class="import-empty-hint">
          <span v-if="importFieldsLoading">正在加载字段信息...</span>
          <span v-else>该表暂无字段信息</span>
        </div>
        <div v-else-if="importSelectedSchema" class="import-empty-hint">
          请选择数据表
        </div>
        <div v-else class="import-empty-hint">
          请先选择数据源、Schema 和数据表
        </div>
      </div>
      <template #modal-footer>
        <div>
          <button
            type="button"
            class="btn btn-secondary"
            @click="importDialogVisible = false"
          >
            取消
          </button>
          <button
            type="button"
            class="btn btn-primary"
            :disabled="importSelectedFields.length === 0"
            @click="confirmImportFields"
          >
            引入选中字段 ({{ importSelectedFields.length }})
          </button>
        </div>
      </template>
    </b-modal>
  </div>
</template>

<script lang="ts" src="./data-model-update.component.ts"></script>

<style scoped>
.data-model-update-container {
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

.model-form {
  margin-bottom: 32px;
}

.fields-section {
  margin-top: 24px;
}

.fields-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.fields-header h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.fields-header-actions {
  display: flex;
  gap: 8px;
}

.dimension-select {
  display: flex;
  flex-direction: column;
}

.import-empty-hint {
  text-align: center;
  padding: 40px 0;
  color: #909399;
  font-size: 14px;
}

.import-field-type-hint {
  color: #909399;
  font-size: 12px;
  margin-left: 4px;
}

.import-table-wrapper {
  max-height: 400px;
  overflow-y: auto;
  position: relative;
}

.import-table {
  margin-bottom: 0;
}

.import-table thead th {
  position: sticky;
  top: 0;
  background: #f5f7fa;
  z-index: 1;
}

.import-table-wrapper.loading {
  opacity: 0.6;
  pointer-events: none;
}

.field-placeholder {
  color: #c0c4cc;
  font-size: 13px;
}

.muted {
  color: #c0c4cc;
}
</style>