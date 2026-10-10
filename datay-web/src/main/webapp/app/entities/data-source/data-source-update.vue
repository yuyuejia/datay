<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <el-form name="editForm" label-width="160px" @submit.prevent="save()">
        <h2 id="datafusionApp.dataSource.home.createOrEditLabel" data-cy="DataSourceCreateUpdateHeading">创建或编辑 Data Source</h2>
        <div>
          <el-form-item label="Name">
            <el-input name="name" id="data-source-name" data-cy="name" v-model="v$.name.$model" />
          </el-form-item>
          <el-form-item label="Description">
            <el-input name="description" id="data-source-description" data-cy="description" v-model="v$.description.$model" />
          </el-form-item>
          <el-form-item label="Type">
            <el-select name="type" id="data-source-type" data-cy="type" v-model="dataSource.type" @change="onTypeChange" :disabled="!!dataSource.id" placeholder="请选择数据库类型">
            <el-option v-for="dbType in dbTypes" :key="dbType.name" :value="dbType.name" :label="dbType.displayName" />
          </el-select>
          </el-form-item>
          <el-form-item v-if="selectedDbType?.downloadable" label="JDBC 驱动">
            <div class="driver-status">
              <el-tag v-if="driverStatus?.installed" type="success" size="small">已安装</el-tag>
              <el-tag v-else type="warning" size="small">未安装</el-tag>
              <span class="driver-name text-muted">{{ driverStatus?.jarName || '驱动未下载' }}</span>
              <el-button type="primary" plain size="small" :loading="isDownloadingDriver" @click="onDriverDownload">
                {{ driverStatus?.installed ? '重新下载驱动' : '下载驱动' }}
              </el-button>
            </div>
            <small class="text-muted d-block">该数据库驱动未内置，测试连接或首次连接时会自动下载，也可手动下载到驱动目录</small>
          </el-form-item>
          <el-form-item v-if="showConnectionModeSelector" label="连接方式">
            <div class="d-flex gap-3">
              <div
                v-for="mode in formConfig.connectionModes"
                :key="mode.value"
                class="connection-mode-option"
                :class="{ selected: selectedConnectionMode === mode.value }"
                @click="onConnectionModeChange(mode.value)"
              >
                <span class="connection-mode-label">{{ mode.label }}</span>
                <small v-if="mode.description" class="text-muted d-block">{{ mode.description }}</small>
              </div>
            </div>
          </el-form-item>
          <el-form-item v-if="formConfig.network" label="连接方式">
            <el-radio-group v-model="urlMode" @change="onUrlModeChange">
              <el-radio-button value="simple">简易模式</el-radio-button>
              <el-radio-button value="custom">自定义模式</el-radio-button>
            </el-radio-group>
            <small class="text-muted d-block">简易模式自动生成 JDBC URL，自定义模式手动填写 JDBC URL</small>
          </el-form-item>
          <el-form-item v-if="showFileField" label="数据库文件路径">
            <el-input name="filePath" id="data-source-file-path" data-cy="filePath" placeholder="留空表示内存数据库，例如 /data/analytics.duckdb" v-model="filePath" @input="updateUrl" />
            <small class="text-muted">无需填写主机、端口、用户名和密码</small>
          </el-form-item>
          <el-form-item label="Url">
            <el-input name="url" id="data-source-url" data-cy="url" v-model="dataSource.url" :disabled="hasConnectionModes || urlMode === 'simple'" />
          </el-form-item>
          <div class="form-row" v-if="showNetworkFields">
            <el-form-item class="col-md-8" :label="hostLabel">
            <el-input name="hostname" id="data-source-hostname" data-cy="hostname" v-model="dataSource.hostname" @input="updateUrl" />
          </el-form-item>
            <el-form-item class="col-md-4" label="端口">
            <el-input name="port" id="data-source-port" data-cy="port" v-model="dataSource.port" @input="updateUrl" />
          </el-form-item>
          </div>
          <template v-if="formConfig.databaseEnabled && urlMode === 'simple'">
            <el-form-item v-if="hasIdentifierTypes" label="连接标识类型">
            <el-select name="oracleIdentifierType" id="data-source-oracle-identifier-type" v-model="oracleIdentifierType" @change="onOracleIdentifierTypeChange">
              <el-option v-for="idType in identifierTypes" :key="idType.value" :value="idType.value" :label="idType.label" />
            </el-select>
          </el-form-item>
            <el-form-item :label="databaseLabel">
            <el-input name="database" id="data-source-database" data-cy="database" v-model="dataSource.database" @input="updateUrl" />
          </el-form-item>
          </template>
          <el-form-item v-if="requiresCredentials" label="Username">
            <el-input name="username" id="data-source-username" data-cy="username" v-model="v$.username.$model" />
          </el-form-item>
          <el-form-item v-if="requiresCredentials" label="Password">
            <el-input type="password" name="password" id="data-source-password" data-cy="password" v-model="v$.password.$model" />
          </el-form-item>
          <el-form-item v-if="formConfig.schemaEnabled" label="默认Schema">
            <el-input name="schemaName" id="data-source-schemaName" data-cy="schemaName" v-model="dataSource.schemaName" />
          </el-form-item>

          <!-- 扩展参数设置区域 -->
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
        <div>
          <el-button id="cancel-save" data-cy="entityCreateCancelButton" @click="previousState()">
            <font-awesome-icon icon="ban"></font-awesome-icon>&nbsp;<span>取消</span>
          </el-button>
          <el-button type="primary" native-type="submit" id="save-entity" data-cy="entityCreateSaveButton" :disabled="v$.$invalid || isSaving">
            <font-awesome-icon icon="save"></font-awesome-icon>&nbsp;<span>保存</span>
          </el-button>
          <el-button type="info" id="test-connection" data-cy="testConnectionButton" @click="testConnection" :disabled="testConnectionDisabled">
            <font-awesome-icon icon="plug"></font-awesome-icon>&nbsp;
            <span v-if="isTestingConnection">测试中...</span>
            <span v-else>测试连接</span>
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>
<script lang="ts" src="./data-source-update.component.ts"></script>
<style scoped>
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

.driver-status {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.driver-status .driver-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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