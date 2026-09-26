<template>
  <app-modal v-model="showModal" :title="modalTitle" size="lg" :close-on-backdrop="false" @hidden="handleHidden">
    <div class="modal-body">
      <div v-if="mode === 'create' && currentStep === 1">
        <div class="db-type-grid">
          <div
            v-for="dbType in dbTypes"
            :key="dbType.name"
            class="db-type-card"
            :class="{ selected: selectedTypeName === dbType.name }"
            @click="selectDbType(dbType)"
            @dblclick="selectAndGo(dbType)"
          >
            <div class="db-type-icon">
              <img v-if="dbType.image" :src="dbType.image" :alt="dbType.displayName" class="db-type-img" />
              <font-awesome-icon v-else :icon="dbType.icon || 'database'"></font-awesome-icon>
            </div>
            <div class="db-type-name">{{ dbType.displayName }}</div>
          </div>
        </div>
      </div>

      <div v-else>
        <el-form name="editForm" label-width="110px" @submit.prevent="save()">
          <div>
            <el-form-item v-if="dataSource.id" label="ID">
            <el-input id="id" name="id" v-model="dataSource.id" readonly />
          </el-form-item>
            <el-form-item label="名称">
            <el-input name="name" id="data-source-name" data-cy="name" v-model="dataSource.name" />
          </el-form-item>
            <el-form-item label="类型">
            <el-select name="type" id="data-source-type" data-cy="type" v-model="dataSource.type" @change="onTypeChange" :disabled="mode === 'create'" placeholder="请选择数据库类型">
            <el-option v-for="dbType in dbTypes" :key="dbType.name" :value="dbType.name" :label="dbType.displayName" />
          </el-select>
          </el-form-item>
            <el-form-item v-if="selectedDbType && selectedDbType.connectionModes && selectedDbType.connectionModes.length > 0" label="连接方式">
            <div class="d-flex gap-3">
                <div
                  v-for="mode in selectedDbType.connectionModes"
                  :key="mode.value"
                  class="connection-mode-option"
                  :class="{ selected: duckdbMode === mode.value }"
                  @click="onConnectionModeChange(mode.value)"
                >
                  <span class="connection-mode-label">{{ mode.label }}</span>
                  <small v-if="mode.description" class="text-muted d-block">{{ mode.description }}</small>
                </div>
              </div>
          </el-form-item>
            <el-form-item v-if="isDuckDb && duckdbMode === 'file'" label="数据库文件路径">
            <el-input name="duckdbFile" id="data-source-duckdb-file" data-cy="duckdbFile" placeholder="留空表示内存数据库，例如 /data/analytics.duckdb" v-model="duckdbFile" @input="updateUrl" />
              <small class="text-muted">无需填写主机、端口、用户名和密码</small>
          </el-form-item>
            <div class="form-row" v-if="!isDuckDb || duckdbMode === 'quack'">
              <el-form-item class="col-md-8" :label="isDuckDb ? 'Quack 服务地址' : 'IP/主机'">
            <el-input name="hostname" id="data-source-hostname" data-cy="hostname" v-model="dataSource.hostname" @input="updateUrl" />
          </el-form-item>
              <el-form-item class="col-md-4" label="端口">
            <el-input name="port" id="data-source-port" data-cy="port" v-model="dataSource.port" @input="updateUrl" />
          </el-form-item>
            </div>
            <el-form-item v-if="!isDuckDb" label="数据库名">
            <el-input name="schemaName" id="data-source-schemaName" data-cy="schemaName" v-model="dataSource.schemaName" @input="updateUrl" />
          </el-form-item>
            <el-form-item label="URL">
            <el-input name="url" id="data-source-url" data-cy="url" v-model="dataSource.url" :readonly="isDuckDb" />
          </el-form-item>
            <el-form-item v-if="!isDuckDb" label="用户名">
            <el-input name="username" id="data-source-username" data-cy="username" v-model="dataSource.username" />
          </el-form-item>
            <el-form-item v-if="!isDuckDb" label="密码">
            <el-input type="password" name="password" id="data-source-password" data-cy="password" v-model="dataSource.password" />
          </el-form-item>
            <el-form-item v-if="effectiveExtraParamsTemplate.length > 0 || extraParamRows.length > 0" label="扩展参数">
            <div class="d-flex justify-content-between align-items-center mb-2">
                
                <el-button type="primary" plain size="small" @click="addExtraParam">
                  <font-awesome-icon icon="plus"></font-awesome-icon>&nbsp;添加参数
                </el-button>
              </div>
              <div v-if="extraParamRows.length === 0" class="text-muted small">暂无扩展参数</div>
              <div
                v-for="(row, index) in extraParamRows"
                :key="index"
                class="extra-param-row mb-2 d-flex gap-2 align-items-start"
              >
                <div class="flex-grow-1">
                  <div class="d-flex gap-2 mb-1">
                    <el-input v-model="row.key" placeholder="参数名" :readonly="!!effectiveExtraParamsTemplate.find(t => t.key === row.key)" />
                    <el-input v-model="row.value" :placeholder="row.label || '参数值'" />
                    <el-button type="danger" plain size="small" @click="removeExtraParam(index)" :disabled="!!row.required" title="删除参数">
                      <font-awesome-icon icon="trash"></font-awesome-icon>
                    </el-button>
                  </div>
                  <small v-if="row.description" class="text-muted">{{ row.description }}</small>
                  <small v-if="row.required" class="text-danger ml-1">* 必填</small>
                </div>
              </div>
          </el-form-item>
          </div>
        </el-form>
      </div>
    </div>
    <template #modal-footer>
      <div>
        <el-button v-if="mode === 'create' && currentStep === 2" @click="goToStep(1)">
          <font-awesome-icon icon="arrow-left"></font-awesome-icon>&nbsp;<span>上一步</span>
        </el-button>
        <el-button @click="closeModal()">
          <font-awesome-icon icon="ban"></font-awesome-icon>&nbsp;<span>取消</span>
        </el-button>
        <el-button type="primary" v-if="mode === 'create' && currentStep === 1" :disabled="!selectedTypeName" @click="goToStep(2)">
          <span>下一步</span>&nbsp;<font-awesome-icon icon="arrow-right"></font-awesome-icon>
        </el-button>
        <el-button type="primary" v-else id="save-entity" data-cy="entityCreateSaveButton" :disabled="isSaving" @click="save()">
          <font-awesome-icon icon="save"></font-awesome-icon>&nbsp;<span>保存</span>
        </el-button>
        <el-button type="info" v-if="mode === 'edit' || (mode === 'create' && currentStep === 2)" id="test-connection" data-cy="testConnectionButton" @click="testConnection" :disabled="testConnectionDisabled">
          <font-awesome-icon icon="plug"></font-awesome-icon>&nbsp;
          <span v-if="isTestingConnection">测试中...</span>
          <span v-else>测试连接</span>
        </el-button>
      </div>
    </template>
  </app-modal>
</template>

<script lang="ts" src="./data-source-modal.component.ts"></script>

<style scoped>
.db-type-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.db-type-card {
  border: 2px solid #e9ecef;
  border-radius: 6px;
  padding: 10px 8px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s ease;
  background-color: #fff;
}

.db-type-card:hover {
  border-color: #007bff;
  transform: translateY(-2px);
  box-shadow: 0 2px 8px rgba(0, 123, 255, 0.15);
}

.db-type-card.selected {
  border-color: #007bff;
  background-color: #e7f1ff;
}

.db-type-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 36px;
  margin-bottom: 4px;
  color: #007bff;
  font-size: 24px;
}

.db-type-img {
  width: 28px;
  height: 28px;
  object-fit: contain;
}

.db-type-name {
  font-size: 13px;
  font-weight: 600;
  color: #212529;
  margin-bottom: 2px;
}

.db-type-desc {
  font-size: 11px;
  color: #6c757d;
}

.selected-type-img {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.selected-type-icon {
  font-size: 28px;
  color: #007bff;
}

.connection-mode-option {
  flex: 1;
  border: 2px solid #e9ecef;
  border-radius: 6px;
  padding: 8px 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  background-color: #fff;
}

.connection-mode-option:hover {
  border-color: #007bff;
}

.connection-mode-option.selected {
  border-color: #007bff;
  background-color: #e7f1ff;
}

.connection-mode-label {
  font-weight: 600;
  color: #212529;
}
</style>