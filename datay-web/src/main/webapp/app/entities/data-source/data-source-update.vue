<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <form name="editForm" novalidate @submit.prevent="save()">
        <h2 id="datafusionApp.dataSource.home.createOrEditLabel" data-cy="DataSourceCreateUpdateHeading">创建或编辑 Data Source</h2>
        <div>
          <div class="form-group" v-if="dataSource.id">
            <label for="id">ID</label>
            <input type="text" class="form-control" id="id" name="id" v-model="dataSource.id" readonly />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="data-source-name">Name</label>
            <input
              type="text"
              class="form-control"
              name="name"
              id="data-source-name"
              data-cy="name"
              :class="{ valid: !v$.name.$invalid, invalid: v$.name.$invalid }"
              v-model="v$.name.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="data-source-description">Description</label>
            <input
              type="text"
              class="form-control"
              name="description"
              id="data-source-description"
              data-cy="description"
              :class="{ valid: !v$.description.$invalid, invalid: v$.description.$invalid }"
              v-model="v$.description.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="data-source-type">Type</label>
            <select
              class="form-control"
              name="type"
              id="data-source-type"
              data-cy="type"
              :class="{ valid: !v$.type.$invalid, invalid: v$.type.$invalid }"
              v-model="dataSource.type"
              @change="onTypeChange"
            >
              <option value="">请选择数据库类型</option>
              <option v-for="dbType in dbTypes" :key="dbType.name" :value="dbType.name">
                {{ dbType.displayName }}
              </option>
            </select>
          </div>
          <div
            class="form-group"
            v-if="selectedDbType && selectedDbType.connectionModes && selectedDbType.connectionModes.length > 0"
          >
            <label class="form-control-label">连接方式</label>
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
          </div>
          <div class="form-group" v-if="isDuckDb && duckdbMode === 'file'">
            <label class="form-control-label" for="data-source-duckdb-file">数据库文件路径</label>
            <input
              type="text"
              class="form-control"
              name="duckdbFile"
              id="data-source-duckdb-file"
              data-cy="duckdbFile"
              placeholder="留空表示内存数据库，例如 /data/analytics.duckdb"
              v-model="duckdbFile"
              @input="updateUrl"
            />
            <small class="text-muted">无需填写主机、端口、用户名和密码</small>
          </div>
          <div class="form-row" v-if="isDuckDb && duckdbMode === 'quack'">
            <div class="form-group col-md-8">
              <label class="form-control-label" for="data-source-hostname">Quack 服务地址</label>
              <input
                type="text"
                class="form-control"
                name="hostname"
                id="data-source-hostname"
                data-cy="hostname"
                v-model="dataSource.hostname"
                @input="updateUrl"
              />
            </div>
            <div class="form-group col-md-4">
              <label class="form-control-label" for="data-source-port">端口</label>
              <input
                type="text"
                class="form-control"
                name="port"
                id="data-source-port"
                data-cy="port"
                v-model="dataSource.port"
                @input="updateUrl"
              />
            </div>
          </div>
          <div class="form-group">
            <label class="form-control-label" for="data-source-url">Url</label>
            <input
              type="text"
              class="form-control"
              name="url"
              id="data-source-url"
              data-cy="url"
              :class="{ valid: !v$.url.$invalid, invalid: v$.url.$invalid }"
              v-model="dataSource.url"
              :readonly="isDuckDb"
            />
          </div>
          <div class="form-group" v-if="!isDuckDb">
            <label class="form-control-label" for="data-source-schemaName">Schema Name</label>
            <input
              type="text"
              class="form-control"
              name="schemaName"
              id="data-source-schemaName"
              data-cy="schemaName"
              :class="{ valid: !v$.schemaName.$invalid, invalid: v$.schemaName.$invalid }"
              v-model="dataSource.schemaName"
            />
          </div>
          <div class="form-group" v-if="!isDuckDb">
            <label class="form-control-label" for="data-source-username">Username</label>
            <input
              type="text"
              class="form-control"
              name="username"
              id="data-source-username"
              data-cy="username"
              :class="{ valid: !v$.username.$invalid, invalid: v$.username.$invalid }"
              v-model="v$.username.$model"
            />
          </div>
          <div class="form-group" v-if="!isDuckDb">
            <label class="form-control-label" for="data-source-password">Password</label>
            <input
              type="password"
              class="form-control"
              name="password"
              id="data-source-password"
              data-cy="password"
              :class="{ valid: !v$.password.$invalid, invalid: v$.password.$invalid }"
              v-model="v$.password.$model"
            />
          </div>

          <!-- 扩展参数设置区域 -->
          <div class="form-group" v-if="effectiveExtraParamsTemplate.length > 0 || extraParamRows.length > 0">
            <div class="d-flex justify-content-between align-items-center mb-2">
              <label class="form-control-label mb-0">扩展参数</label>
              <button type="button" class="btn btn-sm btn-outline-primary" @click="addExtraParam">
                <font-awesome-icon icon="plus"></font-awesome-icon>&nbsp;添加参数
              </button>
            </div>
            <div v-if="extraParamRows.length === 0" class="text-muted small">暂无扩展参数</div>
            <div
              v-for="(row, index) in extraParamRows"
              :key="index"
              class="extra-param-row mb-2 d-flex gap-2 align-items-start"
            >
              <div class="flex-grow-1">
                <div class="d-flex gap-2 mb-1">
                  <input
                    type="text"
                    class="form-control form-control-sm"
                    v-model="row.key"
                      placeholder="参数名"
                      :readonly="!!effectiveExtraParamsTemplate.find(t => t.key === row.key)"
                  />
                  <input
                    type="text"
                    class="form-control form-control-sm"
                    v-model="row.value"
                    :placeholder="row.label || '参数值'"
                  />
                  <button
                    type="button"
                    class="btn btn-sm btn-outline-danger"
                    @click="removeExtraParam(index)"
                    :disabled="!!row.required"
                    title="删除参数"
                  >
                    <font-awesome-icon icon="trash"></font-awesome-icon>
                  </button>
                </div>
                <small v-if="row.description" class="text-muted">{{ row.description }}</small>
                <small v-if="row.required" class="text-danger ml-1">* 必填</small>
              </div>
            </div>
          </div>
        </div>
        <div>
          <button type="button" id="cancel-save" data-cy="entityCreateCancelButton" class="btn btn-secondary mr-2" @click="previousState()">
            <font-awesome-icon icon="ban"></font-awesome-icon>&nbsp;<span>取消</span>
          </button>
          <button
            type="submit"
            id="save-entity"
            data-cy="entityCreateSaveButton"
            :disabled="v$.$invalid || isSaving"
            class="btn btn-primary mr-2"
          >
            <font-awesome-icon icon="save"></font-awesome-icon>&nbsp;<span>保存</span>
          </button>
          <button
            type="button"
            id="test-connection"
            data-cy="testConnectionButton"
            class="btn btn-info mr-2"
            @click="testConnection"
            :disabled="testConnectionDisabled"
          >
            <font-awesome-icon icon="plug"></font-awesome-icon>&nbsp;
            <span v-if="isTestingConnection">测试中...</span>
            <span v-else>测试连接</span>
          </button>
        </div>
      </form>
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