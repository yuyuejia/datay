<template>
  <b-modal v-model="showModal" :title="modalTitle" size="lg" :close-on-backdrop="false" @hidden="handleHidden">
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
        <form name="editForm" novalidate @submit.prevent="save()">
          <div>
            <div class="form-group" v-if="dataSource.id">
              <label for="id">ID</label>
              <input type="text" class="form-control" id="id" name="id" v-model="dataSource.id" readonly />
            </div>
            <div class="form-group">
              <label class="form-control-label" for="data-source-name">名称</label>
              <input
                type="text"
                class="form-control"
                name="name"
                id="data-source-name"
                data-cy="name"
                v-model="dataSource.name"
              />
            </div>
            <div class="form-group">
              <label class="form-control-label" for="data-source-type">类型</label>
              <select
                class="form-control"
                name="type"
                id="data-source-type"
                data-cy="type"
                v-model="dataSource.type"
                @change="onTypeChange"
                :disabled="mode === 'create'"
              >
                <option value="">请选择数据库类型</option>
                <option v-for="dbType in dbTypes" :key="dbType.name" :value="dbType.name">
                  {{ dbType.displayName }}
                </option>
              </select>
            </div>
            <div class="form-row">
              <div class="form-group col-md-8">
                <label class="form-control-label" for="data-source-hostname">IP/主机</label>
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
              <label class="form-control-label" for="data-source-schemaName">数据库名</label>
              <input
                type="text"
                class="form-control"
                name="schemaName"
                id="data-source-schemaName"
                data-cy="schemaName"
                v-model="dataSource.schemaName"
                @input="updateUrl"
              />
            </div>
            <div class="form-group">
              <label class="form-control-label" for="data-source-url">URL</label>
              <input
                type="text"
                class="form-control"
                name="url"
                id="data-source-url"
                data-cy="url"
                v-model="dataSource.url"
              />
            </div>
            <div class="form-group">
              <label class="form-control-label" for="data-source-username">用户名</label>
              <input
                type="text"
                class="form-control"
                name="username"
                id="data-source-username"
                data-cy="username"
                v-model="dataSource.username"
              />
            </div>
            <div class="form-group">
              <label class="form-control-label" for="data-source-password">密码</label>
              <input
                type="password"
                class="form-control"
                name="password"
                id="data-source-password"
                data-cy="password"
                v-model="dataSource.password"
              />
            </div>
            <div class="form-group" v-if="selectedDbType && (selectedDbType.extraParamsTemplate || extraParamRows.length > 0)">
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
                      :readonly="!!selectedDbType?.extraParamsTemplate?.find(t => t.key === row.key)"
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
        </form>
      </div>
    </div>
    <template #modal-footer>
      <div>
        <button
          v-if="mode === 'create' && currentStep === 2"
          type="button"
          class="btn btn-secondary mr-2"
          @click="goToStep(1)"
        >
          <font-awesome-icon icon="arrow-left"></font-awesome-icon>&nbsp;<span>上一步</span>
        </button>
        <button type="button" class="btn btn-secondary mr-2" @click="closeModal()">
          <font-awesome-icon icon="ban"></font-awesome-icon>&nbsp;<span>取消</span>
        </button>
        <button
          v-if="mode === 'create' && currentStep === 1"
          type="button"
          class="btn btn-primary mr-2"
          :disabled="!selectedTypeName"
          @click="goToStep(2)"
        >
          <span>下一步</span>&nbsp;<font-awesome-icon icon="arrow-right"></font-awesome-icon>
        </button>
        <button
          v-else
          type="button"
          id="save-entity"
          data-cy="entityCreateSaveButton"
          :disabled="isSaving"
          class="btn btn-primary mr-2"
          @click="save()"
        >
          <font-awesome-icon icon="save"></font-awesome-icon>&nbsp;<span>保存</span>
        </button>
        <button
          v-if="mode === 'edit' || (mode === 'create' && currentStep === 2)"
          type="button"
          id="test-connection"
          data-cy="testConnectionButton"
          class="btn btn-info"
          @click="testConnection"
          :disabled="isTestingConnection || !dataSource.type || !dataSource.url || !dataSource.username"
        >
          <font-awesome-icon icon="plug"></font-awesome-icon>&nbsp;
          <span v-if="isTestingConnection">测试中...</span>
          <span v-else>测试连接</span>
        </button>
      </div>
    </template>
  </b-modal>
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
</style>