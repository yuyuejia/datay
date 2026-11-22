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
            <el-input
              v-model="dataModel.name"
              placeholder="请输入模型名称"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="模型编码" required>
            <el-input
              v-model="dataModel.code"
              placeholder="请输入模型编码"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="模型类型" required>
            <el-select v-model="dataModel.modelType" style="width: 100%" @change="onModelTypeChange" placeholder="请选择模型类型">
            <el-option value="ODS" label="ODS 贴源层" />
            <el-option value="DIMENSION" label="DIM 维度表" />
            <el-option value="DWD" label="DWD 明细层" />
            <el-option value="DWS" label="DWS 汇总层" />
            <el-option value="ADS" label="ADS 应用层" />
          </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="创建模式" required>
            <el-select v-model="modelMode" style="width: 100%" @change="onModelModeChange">
            <el-option value="normal" label="普通模式" />
            <el-option value="register" label="注册模式" />
          </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row v-if="dataModel.modelType === 'DIMENSION'" :gutter="20">
        <el-col :span="12">
          <el-form-item label="维度类型" required>
            <el-select v-model="dataModel.dimensionKind" style="width: 100%" @change="onDimensionKindChange">
            <el-option value="NORMAL" label="普通维度" />
            <el-option value="HIERARCHY" label="层级维度" />
            <el-option value="TIME" label="时间维度" />
            <el-option value="DEGENERATE" label="退化维度（直接使用事实表字段）" />
          </el-select>
          </el-form-item>
        </el-col>
        <el-col v-if="dataModel.dimensionKind === 'HIERARCHY'" :span="12">
          <el-form-item label="层级数量" required>
            <el-input-number :controls="false" v-model="dataModel.levelCount" :min="1" :max="10" @change="onLevelCountChange" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row v-if="dataModel.modelType === 'DIMENSION' && dataModel.dimensionKind === 'DEGENERATE'" :gutter="20">
        <el-col :span="24">
          <el-form-item label="说明">
            <span class="dimension-degenerate-hint">
              退化维度没有物理维表，也不需要在维度模型中维护字段。事实表字段关联到该维度后，查询时直接使用事实表字段进行分组和过滤。
            </span>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row v-if="dataModel.modelType === 'DIMENSION' && dataModel.dimensionKind !== 'DEGENERATE'" :gutter="20">
        <el-col :span="12">
          <el-form-item label="默认显示字段">
            <el-select v-model="dataModel.displayFieldName" style="width: 100%">
            <el-option :value="null" label="自动（优先名称字段）" />
            <el-option v-for="field in displayFieldOptions" :key="field.fieldName" :value="field.fieldName" :label="`${field.fieldName} (${field.fieldType})`" />
          </el-select>
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
                <el-checkbox
                  :model-value="isTimeLevelSelected(opt.code)"
                  @change="toggleTimeLevel(opt.code)"
                >
                  {{ opt.label }}
                </el-checkbox>
              </label>
            </div>
            <div class="time-granularity-hint">
              按由粗到细生成层级字段，最末级粒度决定成员与主键 date_key
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="起始日期">
            <el-date-picker type="date" value-format="YYYY-MM-DD" v-model="dataModel.timeStart" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="结束日期">
            <el-date-picker type="date" value-format="YYYY-MM-DD" v-model="dataModel.timeEnd" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row v-if="isEdit && dataModel.modelType === 'DIMENSION'" :gutter="20">
        <el-col :span="24">
          <el-form-item label="维度角色">
            <div class="dimension-roles">
              <div class="dimension-roles-hint">
                定义同一维度在事实表中的业务角色，例如日期维度的下单日期、支付日期、发货日期。
              </div>
              <el-table :data="dimensionRoles" border size="small">
                <el-table-column label="序号" width="60" align="center">
                  <template #default="{ $index }">{{ $index + 1 }}</template>
                </el-table-column>
                <el-table-column label="角色编码" width="200">
                  <template #default="{ row }">
                    <el-input v-model="row.code" placeholder="如 ORDER_DATE" :disabled="!!row.id" />
                  </template>
                </el-table-column>
                <el-table-column label="角色名称" width="200">
                  <template #default="{ row }">
                    <el-input v-model="row.name" placeholder="如 下单日期" />
                  </template>
                </el-table-column>
                <el-table-column label="描述" min-width="220">
                  <template #default="{ row }">
                    <el-input v-model="row.description" placeholder="角色说明" />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="130" align="center">
                  <template #default="{ $index }">
                    <el-button link type="primary" size="small" :disabled="$index === 0" @click="moveDimensionRole($index, -1)">
                      <font-awesome-icon icon="arrow-up" />
                    </el-button>
                    <el-button link type="primary" size="small" :disabled="$index === dimensionRoles.length - 1" @click="moveDimensionRole($index, 1)">
                      <font-awesome-icon icon="arrow-down" />
                    </el-button>
                    <el-button link type="danger" size="small" @click="removeDimensionRole($index)">
                      <font-awesome-icon icon="trash" />
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-button size="small" type="primary" plain @click="addDimensionRole">
                <font-awesome-icon icon="plus" /> 添加角色
              </el-button>
            </div>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row v-if="dataModel.modelType === 'DWD'" :gutter="20">
        <el-col :span="12">
          <el-form-item label="时间周期字段">
            <el-select v-model="dataModel.timeFieldName" style="width: 100%">
            <el-option :value="null" label="未设置" />
            <el-option v-for="field in timeFieldOptions" :key="field.fieldName" :value="field.fieldName" :label="`${field.fieldName} (${field.fieldType})`" />
          </el-select>
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
            <el-select v-model="registerSelectedDataSourceId" style="width: 100%" @change="onRegisterDataSourceChange">
            <el-option :value="null" disabled label="请选择数据源" />
            <el-option v-for="ds in registerAvailableDataSources" :key="ds.id" :value="ds.id" :label="ds.name" />
          </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="Schema" required>
            <el-select v-model="registerSelectedSchema" :disabled="!registerSelectedDataSourceId" style="width: 100%" @change="onRegisterSchemaChange">
            <el-option :value="null" disabled label="请选择Schema" />
            <el-option v-for="schema in registerSchemas" :key="schema" :value="schema" :label="schema" />
          </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="数据表" required>
            <el-select v-model="registerSelectedTable" :disabled="!registerSelectedSchema" style="width: 100%" @change="() => onRegisterTableChange(false)">
            <el-option :value="null" disabled label="请选择数据表" />
            <el-option v-for="table in registerTables" :key="table" :value="table" :label="table" />
          </el-select>
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
            <el-select v-model="dataModel.directoryId" style="width: 100%">
            <el-option :value="null" label="请选择所属目录" />
            <el-option v-for="dir in flatDirectoryOptions" :key="dir.id" :value="dir.id" :label="'\u00A0\u00A0'.repeat(dir.level) + dir.name" />
          </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="24">
          <el-form-item label="描述">
            <el-input type="textarea" v-model="dataModel.description" :rows="3" placeholder="请输入模型描述" />
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
          <el-button v-if="dataModel.modelType !== 'DIMENSION'" type="warning" size="small" plain @click="openQuickRoleDialog">
            <font-awesome-icon icon="wand-magic-sparkles" /> 快速生成维度角色
          </el-button>
          <el-button type="success" size="small" @click="showImportDialog">
            <font-awesome-icon icon="file-import" /> 从已有表引入字段
          </el-button>
        </div>
      </div>
      <div
        v-if="dataModel.modelType === 'DIMENSION' && dataModel.dimensionKind === 'DEGENERATE'"
        class="dimension-degenerate-hint fields-degenerate-hint"
      >
        退化维度无需维护维度字段；请在事实模型中将字段关联到该维度。
      </div>
      <el-table :data="fields" border style="width: 100%" row-key="id">
        <el-table-column label="序号" width="60" align="center">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column label="字段名称" width="180">
          <template #default="{ row }">
            <el-input
              v-model="row.fieldName"
              placeholder="字段名称"
              :disabled="isGeneratedField(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="字段类型" width="160">
          <template #default="{ row }">
            <el-select v-model="row.fieldType" style="width: 100%" :disabled="isGeneratedField(row)" @change="clearLengthAndPrecisionIfUnneeded(row)" placeholder="字段类型">
            <el-option v-for="lt in logicalTypes" :key="lt.type" :value="lt.type" :label="lt.label" />
          </el-select>
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
        <el-table-column label="长度" width="100">
          <template #default="{ row }">
            <el-input-number
              v-if="needsLength(row.fieldType)"
              :controls="false"
              v-model="row.fieldLength"
              placeholder="长度"
              style="width: 100%"
              :disabled="isGeneratedField(row)"
            />
            <span v-else class="field-placeholder">-</span>
          </template>
        </el-table-column>
        <el-table-column label="精度" width="100">
          <template #default="{ row }">
            <el-input-number
              v-if="needsPrecision(row.fieldType)"
              :controls="false"
              v-model="row.fieldPrecision"
              placeholder="精度"
              style="width: 100%"
              :disabled="isGeneratedField(row)"
            />
            <span v-else class="field-placeholder">-</span>
          </template>
        </el-table-column>
        <el-table-column label="小数位" width="100">
          <template #default="{ row }">
            <el-input-number
              v-if="needsScale(row.fieldType)"
              :controls="false"
              v-model="row.fieldScale"
              placeholder="小数位"
              style="width: 100%"
              :disabled="isGeneratedField(row)"
            />
            <span v-else class="field-placeholder">-</span>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="180">
          <template #default="{ row }">
            <el-input
              v-model="row.description"
              style="width: 100%"
              placeholder="字段描述"
              :disabled="isGeneratedField(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="主键" width="80" align="center">
          <template #default="{ row }">
            <el-checkbox
              :model-value="!!row.isPrimaryKey"
              :disabled="isGeneratedField(row)"
              @change="
                val => {
                  row.isPrimaryKey = !!val;
                  handlePrimaryKeyChange(row);
                }
              "
              style="margin: 0"
            />
          </template>
        </el-table-column>
        <el-table-column label="关联维度" width="220">
          <template #default="{ row }">
            <div class="dimension-select">
              <el-select
                v-model="row.dimensionModelId"
                style="width: 100%"
                clearable
                :disabled="isGeneratedField(row)"
                placeholder="选择维度模型"
                @change="val => handleDimensionModelChange(row, val)"
              >
                <el-option v-for="dim in dimensionModels" :key="dim.id" :value="dim.id" :label="dim.name" />
              </el-select>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="维度角色" width="200">
          <template #default="{ row }">
            <el-select
              v-if="row.dimensionModelId && getDimensionRoles(row.dimensionModelId).length"
              :model-value="row.dimensionRole || ''"
              clearable
              filterable
              placeholder="默认关联"
              style="width: 100%"
              :disabled="isGeneratedField(row)"
              @change="val => onDimensionRoleChange(row, val)"
            >
              <el-option value="" label="默认关联" />
              <el-option
                v-for="role in getDimensionRoles(row.dimensionModelId)"
                :key="role.id || role.code"
                :value="role.code"
                :label="role.name ? `${role.name} (${role.code})` : role.code"
              />
            </el-select>
            <span v-else class="field-placeholder">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row, $index }">
            <div class="field-actions">
              <el-button
                link
                type="primary"
                size="small"
                title="上移"
                :disabled="!canMoveField(row, -1)"
                @click="moveField(row, -1)"
              >
                <font-awesome-icon icon="arrow-up" />
              </el-button>
              <el-button
                link
                type="primary"
                size="small"
                title="下移"
                :disabled="!canMoveField(row, 1)"
                @click="moveField(row, 1)"
              >
                <font-awesome-icon icon="arrow-down" />
              </el-button>
              <el-button
                v-if="!isGeneratedField(row)"
                link
                type="danger"
                size="small"
                title="删除"
                @click="removeField($index)"
              >
                <font-awesome-icon icon="trash" />
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <app-modal v-model="quickRoleDialogVisible" title="快速生成维度角色" size="lg">
      <div class="modal-body">
        <p class="dimension-roles-hint">
          根据事实字段名生成角色建议。确认后会先把缺失的角色写入对应维度模型，再回填到字段。
        </p>
        <el-table :data="quickRoleSuggestions" border size="small">
          <el-table-column label="启用" width="60" align="center">
            <template #default="{ row }">
              <el-checkbox v-model="row.selected" />
            </template>
          </el-table-column>
          <el-table-column prop="dimensionModelName" label="维度" width="160" />
          <el-table-column prop="fieldName" label="事实字段" width="180" />
          <el-table-column label="角色编码" width="180">
            <template #default="{ row }">
              <el-input v-model="row.code" placeholder="如 ORDER_DATE" />
            </template>
          </el-table-column>
          <el-table-column label="角色名称" min-width="160">
            <template #default="{ row }">
              <el-input v-model="row.name" placeholder="如 下单日期" />
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="quickRoleDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmQuickRoles">生成并应用</el-button>
        </div>
      </template>
    </app-modal>

    <app-modal v-model="importDialogVisible" title="从已有表引入字段" size="lg">
      <div class="modal-body">
        <div class="form-row">
          <el-form-item class="col-md-4" label="数据源">
            <el-select v-model="importSelectedDataSourceId" @change="onImportDataSourceChange">
            <el-option :value="null" disabled label="请选择数据源" />
            <el-option v-for="ds in importAvailableDataSources" :key="ds.id" :value="ds.id" :label="ds.name" />
          </el-select>
          </el-form-item>
          <el-form-item class="col-md-4" label="Schema">
            <el-select v-model="importSelectedSchema" :disabled="!importSelectedDataSourceId" @change="onImportSchemaChange">
            <el-option :value="null" disabled label="请选择Schema" />
            <el-option v-for="schema in importSchemas" :key="schema" :value="schema" :label="schema" />
          </el-select>
          </el-form-item>
          <el-form-item class="col-md-4" label="数据表">
            <el-select v-model="importSelectedTable" :disabled="!importSelectedSchema" @change="onImportTableChange">
            <el-option :value="null" disabled label="请选择数据表" />
            <el-option v-for="table in importTables" :key="table" :value="table" :label="table" />
          </el-select>
          </el-form-item>
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
                  <el-checkbox
                    :model-value="isAllFieldsSelected"
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
                  <el-checkbox
                    :model-value="isFieldSelected(field)"
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
          <el-button @click="importDialogVisible = false">
            取消
          </el-button>
          <el-button type="primary" :disabled="importSelectedFields.length === 0" @click="confirmImportFields">
            引入选中字段 ({{ importSelectedFields.length }})
          </el-button>
        </div>
      </template>
    </app-modal>
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

.dimension-roles {
  width: 100%;
}

.dimension-roles-hint {
  margin-bottom: 8px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.dimension-roles .el-button {
  margin-top: 8px;
}

.dimension-degenerate-hint {
  display: block;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}

.fields-degenerate-hint {
  margin-bottom: 8px;
}

.field-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  white-space: nowrap;
}

.field-actions :deep(.el-button) {
  margin: 0;
  padding: 4px 6px;
  line-height: 1;
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
