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
            <b-form-input
              v-model="dataModel.name"
              placeholder="请输入模型名称"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="模型编码" required>
            <b-form-input
              v-model="dataModel.code"
              placeholder="请输入模型编码"
            />
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
              @change="onModelTypeChange"
            >
              <option value="" disabled>请选择模型类型</option>
              <option value="ODS">ODS 贴源层</option>
              <option value="DIMENSION">DIM 维度表</option>
              <option value="DWD">DWD 明细层</option>
              <option value="DWS">DWS 汇总层</option>
              <option value="ADS">ADS 应用层</option>
            </select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="创建模式" required>
            <select
              class="form-control"
              v-model="modelMode"
              style="width: 100%"
              @change="onModelModeChange"
            >
              <option value="normal">普通模式</option>
              <option value="register">注册模式</option>
            </select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row v-if="dataModel.modelType === 'DIMENSION'" :gutter="20">
        <el-col :span="12">
          <el-form-item label="维度类型" required>
            <select
              class="form-control"
              v-model="dataModel.dimensionKind"
              style="width: 100%"
              @change="onDimensionKindChange"
            >
              <option value="NORMAL">普通维度</option>
              <option value="HIERARCHY">层级维度</option>
              <option value="TIME">时间维度</option>
            </select>
          </el-form-item>
        </el-col>
        <el-col v-if="dataModel.dimensionKind === 'HIERARCHY'" :span="12">
          <el-form-item label="层级数量" required>
            <b-form-input
              type="number"
              v-model.number="dataModel.levelCount"
              min="1"
              max="10"
              @change="onLevelCountChange"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row v-if="dataModel.modelType === 'DIMENSION'" :gutter="20">
        <el-col :span="12">
          <el-form-item label="默认显示字段">
            <select
              class="form-control"
              v-model="dataModel.displayFieldName"
              style="width: 100%"
            >
              <option :value="null">自动（优先名称字段）</option>
              <option
                v-for="field in displayFieldOptions"
                :key="field.fieldName"
                :value="field.fieldName"
              >
                {{ field.fieldName }} ({{ field.fieldType }})
              </option>
            </select>
            <div class="time-granularity-hint">
              图表与筛选默认展示该字段，留空则自动选择名称字段
            </div>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row
        v-if="
          dataModel.modelType === 'DIMENSION' &&
          dataModel.dimensionKind === 'TIME'
        "
        :gutter="20"
      >
        <el-col :span="12">
          <el-form-item label="时间粒度" required>
            <div class="time-granularity">
              <label
                v-for="opt in timeGranularityOptions"
                :key="opt.code"
                class="time-granularity-item"
              >
                <input
                  type="checkbox"
                  :checked="isTimeLevelSelected(opt.code)"
                  @change="toggleTimeLevel(opt.code)"
                />
                <span>{{ opt.label }}</span>
              </label>
            </div>
            <div class="time-granularity-hint">
              按由粗到细生成层级字段，最末级粒度决定成员与主键 date_key
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="起始日期">
            <b-form-input type="date" v-model="dataModel.timeStart" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="结束日期">
            <b-form-input type="date" v-model="dataModel.timeEnd" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row v-if="dataModel.modelType === 'DWD'" :gutter="20">
        <el-col :span="12">
          <el-form-item label="时间周期字段">
            <select
              class="form-control"
              v-model="dataModel.timeFieldName"
              style="width: 100%"
            >
              <option :value="null">未设置</option>
              <option
                v-for="field in timeFieldOptions"
                :key="field.fieldName"
                :value="field.fieldName"
              >
                {{ field.fieldName }} ({{ field.fieldType }})
              </option>
            </select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row
        v-if="modelMode === 'register'"
        :gutter="20"
        class="register-mode-row"
      >
        <el-col :span="8">
          <el-form-item label="数据源" required>
            <select
              class="form-control"
              v-model="registerSelectedDataSourceId"
              style="width: 100%"
              @change="onRegisterDataSourceChange"
            >
              <option :value="null" disabled>请选择数据源</option>
              <option
                v-for="ds in registerAvailableDataSources"
                :key="ds.id"
                :value="ds.id"
              >
                {{ ds.name }}
              </option>
            </select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="Schema" required>
            <select
              class="form-control"
              v-model="registerSelectedSchema"
              :disabled="!registerSelectedDataSourceId"
              style="width: 100%"
              @change="onRegisterSchemaChange"
            >
              <option :value="null" disabled>请选择Schema</option>
              <option
                v-for="schema in registerSchemas"
                :key="schema"
                :value="schema"
              >
                {{ schema }}
              </option>
            </select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="数据表" required>
            <select
              class="form-control"
              v-model="registerSelectedTable"
              :disabled="!registerSelectedSchema"
              style="width: 100%"
              @change="onRegisterTableChange"
            >
              <option :value="null" disabled>请选择数据表</option>
              <option
                v-for="table in registerTables"
                :key="table"
                :value="table"
              >
                {{ table }}
              </option>
            </select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row
        v-if="modelMode === 'register' && registerFieldsLoading"
        class="register-loading-row"
      >
        <el-col :span="24">
          <span class="register-loading-tip">
            <font-awesome-icon icon="spin fa-spinner" /> 正在加载表结构...
          </span>
        </el-col>
      </el-row>
      <el-row
        v-if="
          modelMode === 'register' &&
          registerAutoAdded &&
          !registerFieldsLoading
        "
        class="register-hint-row"
      >
        <el-col :span="24">
          <span class="register-success-tip">
            <font-awesome-icon icon="check-circle" /> 已从物理表自动导入
            {{ fields.length }} 个字段,您可以根据需要进行修改
          </span>
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
              :disabled="isGeneratedField(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="字段类型" width="160">
          <template #default="{ row }">
            <select
              class="form-control form-control-sm"
              v-model="row.fieldType"
              style="width: 100%"
              :disabled="isGeneratedField(row)"
              @change="clearLengthAndPrecisionIfUnneeded(row)"
            >
              <option value="" disabled>字段类型</option>
              <option
                v-for="lt in logicalTypes"
                :key="lt.type"
                :value="lt.type"
              >
                {{ lt.label }}
              </option>
            </select>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.fieldRole" size="small">{{
              fieldRoleLabel(row.fieldRole)
            }}</el-tag>
            <span v-else class="field-placeholder">-</span>
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
              :disabled="isGeneratedField(row)"
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
              :disabled="isGeneratedField(row)"
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
              :disabled="isGeneratedField(row)"
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
              :disabled="isGeneratedField(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="主键" width="80" align="center">
          <template #default="{ row }">
            <input
              type="checkbox"
              :checked="!!row.isPrimaryKey"
              :disabled="isGeneratedField(row)"
              @change="
                row.isPrimaryKey = !!$event.target.checked;
                handlePrimaryKeyChange(row);
              "
              style="width: 16px; height: 16px; cursor: pointer; margin: 0"
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
                :disabled="isGeneratedField(row)"
                @change="handleDimensionModelChange(row, $event.target.value)"
              >
                <option value="">选择维度模型</option>
                <option
                  v-for="dim in dimensionModels"
                  :key="dim.id"
                  :value="dim.id"
                >
                  {{ dim.name }}
                </option>
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
              :disabled="isGeneratedField(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row, $index }">
            <el-button
              v-if="!isGeneratedField(row)"
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

    <b-modal v-model="importDialogVisible" title="从已有表引入字段" size="lg">
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
              <option v-for="table in importTables" :key="table" :value="table">
                {{ table }}
              </option>
            </select>
          </div>
        </div>
        <div
          v-if="importSourceFields.length > 0"
          class="import-table-wrapper"
          :class="{ loading: importFieldsLoading }"
        >
          <table
            class="table table-bordered table-striped table-sm import-table"
          >
            <thead>
              <tr>
                <th style="width: 50px; text-align: center">
                  <input
                    type="checkbox"
                    :checked="isAllFieldsSelected"
                    @change="toggleAllFields"
                  />
                </th>
                <th style="width: 50px">#</th>
                <th style="width: 180px">字段名称</th>
                <th style="width: 70px">主键</th>
                <th style="width: 180px">字段类型</th>
                <th style="width: 80px">长度</th>
                <th style="width: 80px">精度</th>
                <th style="width: 80px">小数位</th>
                <th>描述</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(field, index) in importSourceFields"
                :key="field.tempId"
              >
                <td style="text-align: center">
                  <input
                    type="checkbox"
                    :checked="isFieldSelected(field)"
                    @change="toggleField(field)"
                  />
                </td>
                <td>{{ index + 1 }}</td>
                <td>{{ field.fieldName }}</td>
                <td style="text-align: center">
                  <el-tag v-if="field.isPrimaryKey" type="success" size="small"
                    >主键</el-tag
                  >
                  <span v-else class="muted">-</span>
                </td>
                <td>
                  {{ field.fieldType }}
                  <span class="import-field-type-hint"
                    >({{ field._rawType || "" }})</span
                  >
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

.time-granularity {
  display: flex;
  gap: 16px;
  align-items: center;
}

.time-granularity-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: normal;
  cursor: pointer;
  margin: 0;
}

.time-granularity-item input {
  width: 16px;
  height: 16px;
  cursor: pointer;
  margin: 0;
}

.time-granularity-hint {
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.muted {
  color: #c0c4cc;
}

.register-mode-row {
  background: #f5f7fa;
  padding: 16px 12px 4px;
  border-radius: 4px;
  margin-bottom: 8px;
}

.register-loading-row {
  margin-bottom: 8px;
}

.register-loading-tip {
  color: #409eff;
  font-size: 13px;
}

.register-hint-row {
  margin-bottom: 8px;
}

.register-success-tip {
  color: #67c23a;
  font-size: 13px;
}
</style>
