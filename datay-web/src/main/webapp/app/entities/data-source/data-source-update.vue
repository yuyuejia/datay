<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <el-form name="editForm" label-width="160px" @submit.prevent="save()">
        <h2 id="datafusionApp.dataSource.home.createOrEditLabel" data-cy="DataSourceCreateUpdateHeading">创建或编辑 Data Source</h2>
        <div>
          <el-form-item v-if="dataSource.id" label="ID">
            <el-input id="id" name="id" v-model="dataSource.id" readonly />
          </el-form-item>
          <el-form-item label="Name">
            <el-input name="name" id="data-source-name" data-cy="name" v-model="v$.name.$model" />
          </el-form-item>
          <el-form-item label="Description">
            <el-input name="description" id="data-source-description" data-cy="description" v-model="v$.description.$model" />
          </el-form-item>
          <el-form-item label="Type">
            <el-select name="type" id="data-source-type" data-cy="type" v-model="dataSource.type" @change="onTypeChange" placeholder="请选择数据库类型">
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
          <div class="form-row" v-if="isDuckDb && duckdbMode === 'quack'">
            <el-form-item class="col-md-8" label="Quack 服务地址">
            <el-input name="hostname" id="data-source-hostname" data-cy="hostname" v-model="dataSource.hostname" @input="updateUrl" />
          </el-form-item>
            <el-form-item class="col-md-4" label="端口">
            <el-input name="port" id="data-source-port" data-cy="port" v-model="dataSource.port" @input="updateUrl" />
          </el-form-item>
          </div>
          <el-form-item label="Url">
            <el-input name="url" id="data-source-url" data-cy="url" v-model="dataSource.url" :readonly="isDuckDb" />
          </el-form-item>
          <el-form-item v-if="!isDuckDb" label="Schema Name">
            <el-input name="schemaName" id="data-source-schemaName" data-cy="schemaName" v-model="dataSource.schemaName" />
          </el-form-item>
          <el-form-item v-if="!isDuckDb" label="Username">
            <el-input name="username" id="data-source-username" data-cy="username" v-model="v$.username.$model" />
          </el-form-item>
          <el-form-item v-if="!isDuckDb" label="Password">
            <el-input type="password" name="password" id="data-source-password" data-cy="password" v-model="v$.password.$model" />
          </el-form-item>

          <!-- 扩展参数设置区域 -->
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
</style>