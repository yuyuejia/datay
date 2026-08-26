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
          <!-- <div class="form-group" v-if="selectedDbType && selectedDbType.supportedVersions.length > 0">
            <label class="form-control-label" for="data-source-version">Version</label>
            <select
              class="form-control"
              name="version"
              id="data-source-version"
              data-cy="version"
              v-model="dataSource.version"
              @change="onVersionChange"
            >
              <option value="">请选择版本</option>
              <option v-for="version in selectedDbType.supportedVersions" :key="version" :value="version">
                {{ version }}
              </option>
            </select>
          </div> -->
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
            />
          </div>
          <div class="form-group">
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
          <div class="form-group">
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
          <div class="form-group">
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
            :disabled="isTestingConnection || !dataSource.type || !dataSource.url || !dataSource.username"
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
