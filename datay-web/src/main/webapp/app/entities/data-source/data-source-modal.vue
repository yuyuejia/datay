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
            <el-form-item label="名称">
            <el-input name="name" id="data-source-name" data-cy="name" v-model="dataSource.name" />
          </el-form-item>
            <el-form-item label="类型">
            <el-select name="type" id="data-source-type" data-cy="type" v-model="dataSource.type" @change="onTypeChange" disabled placeholder="请选择数据库类型">
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
            <el-form-item v-if="showUrlModeToggle" label="连接方式">
            <el-radio-group v-model="urlMode" @change="onUrlModeChange">
              <el-radio-button value="simple">简易模式</el-radio-button>
              <el-radio-button value="custom">自定义模式</el-radio-button>
            </el-radio-group>
            <small class="text-muted d-block">简易模式自动生成 JDBC URL，自定义模式手动填写 JDBC URL</small>
          </el-form-item>
            <el-form-item v-if="isDuckDb && duckdbMode === 'file'" label="数据库文件路径">
            <el-input name="duckdbFile" id="data-source-duckdb-file" data-cy="duckdbFile" placeholder="留空表示内存数据库，例如 /data/analytics.duckdb" v-model="duckdbFile" @input="updateUrl" />
              <small class="text-muted">无需填写主机、端口、用户名和密码</small>
          </el-form-item>
            <el-form-item label="URL">
            <el-input name="url" id="data-source-url" data-cy="url" v-model="dataSource.url" :disabled="isDuckDb || urlMode === 'simple'" />
          </el-form-item>
            <div class="form-row" v-if="(!isDuckDb && urlMode === 'simple') || (isDuckDb && duckdbMode === 'quack')">
              <el-form-item class="col-md-8" :label="isDuckDb ? 'Quack 服务地址' : 'IP/主机'">
            <el-input name="hostname" id="data-source-hostname" data-cy="hostname" v-model="dataSource.hostname" @input="updateUrl" />
          </el-form-item>
              <el-form-item class="col-md-4" label="端口">
            <el-input name="port" id="data-source-port" data-cy="port" v-model="dataSource.port" @input="updateUrl" />
          </el-form-item>
            </div>
            <template v-if="!isDuckDb && urlMode === 'simple'">
              <el-form-item v-if="isOracle" label="连接标识类型">
            <el-select name="oracleIdentifierType" id="data-source-oracle-identifier-type" v-model="oracleIdentifierType" @change="onOracleIdentifierTypeChange">
              <el-option value="service" label="服务名 (Service Name)" />
              <el-option value="sid" label="SID" />
            </el-select>
          </el-form-item>
              <el-form-item :label="isOracle ? (oracleIdentifierType === 'sid' ? 'SID' : '服务名') : '数据库名'">
            <el-input name="database" id="data-source-database" data-cy="database" v-model="dataSource.database" @input="updateUrl" />
          </el-form-item>
            </template>
            <el-form-item v-if="requiresCredentials" label="用户名">
            <el-input name="username" id="data-source-username" data-cy="username" v-model="dataSource.username" />
          </el-form-item>
            <el-form-item v-if="requiresCredentials" label="密码">
            <el-input type="password" name="password" id="data-source-password" data-cy="password" v-model="dataSource.password" />
          </el-form-item>
            <el-form-item v-if="!isDuckDb && schemaEnabled" label="默认Schema">
            <el-input name="schemaName" id="data-source-schemaName" data-cy="schemaName" v-model="dataSource.schemaName" />
          </el-form-item>
            <el-form-item v-if="effectiveExtraParamsTemplate.length > 0 || extraParamRows.length > 0" label="扩展参数">
            <div class="extra-params-toolbar">
                <el-button type="primary" plain size="small" @click="addExtraParam">
                  <font-awesome-icon icon="plus"></font-awesome-icon>&nbsp;添加参数
                </el-button>
              </div>
              <div v-if="extraParamRows.length === 0" class="extra-params-empty">暂无扩展参数</div>
              <div v-for="(row, index) in extraParamRows" :key="index" class="extra-param-row">
                <div class="extra-param-main">
                  <el-input class="extra-param-key" v-model="row.key" placeholder="参数名" :readonly="!!effectiveExtraParamsTemplate.find(t => t.key === row.key)" />
                  <el-input class="extra-param-value" v-model="row.value" :placeholder="row.label || '参数值'" />
                  <el-button class="extra-param-remove" type="danger" plain @click="removeExtraParam(index)" :disabled="!!row.required" title="删除参数">
                    <font-awesome-icon icon="trash"></font-awesome-icon>
                  </el-button>
                </div>
                <div v-if="row.description || row.required" class="extra-param-hint">
                  <span v-if="row.required" class="extra-param-required">* 必填</span>
                  <span v-if="row.description">{{ row.description }}</span>
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

.extra-params-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.extra-params-empty {
  font-size: 12px;
  line-height: 32px;
  color: #909399;
}

.extra-param-row {
  padding: 8px 10px;
  margin-bottom: 8px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background-color: #fafafa;
}

.extra-param-main {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr) auto;
  column-gap: 8px;
  align-items: center;
}

.extra-param-key,
.extra-param-value {
  width: 100%;
}

.extra-param-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
}

.extra-param-required {
  margin-right: 6px;
  color: #f56c6c;
}
</style>