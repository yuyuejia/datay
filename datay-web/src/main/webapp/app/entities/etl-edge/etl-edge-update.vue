<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <form name="editForm" novalidate @submit.prevent="save()">
        <h2 id="datafusionApp.eTLEdge.home.createOrEditLabel" data-cy="ETLEdgeCreateUpdateHeading">创建或编辑 ETL Edge</h2>
        <div>
          <div class="form-group" v-if="eTLEdge.id">
            <label for="id">ID</label>
            <input type="text" class="form-control" id="id" name="id" v-model="eTLEdge.id" readonly />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-edge-taskId">Task Id</label>
            <input
              type="text"
              class="form-control"
              name="taskId"
              id="etl-edge-taskId"
              data-cy="taskId"
              :class="{ valid: !v$.taskId.$invalid, invalid: v$.taskId.$invalid }"
              v-model="v$.taskId.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-edge-name">Name</label>
            <input
              type="text"
              class="form-control"
              name="name"
              id="etl-edge-name"
              data-cy="name"
              :class="{ valid: !v$.name.$invalid, invalid: v$.name.$invalid }"
              v-model="v$.name.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-edge-code">Code</label>
            <input
              type="text"
              class="form-control"
              name="code"
              id="etl-edge-code"
              data-cy="code"
              :class="{ valid: !v$.code.$invalid, invalid: v$.code.$invalid }"
              v-model="v$.code.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-edge-source">Source</label>
            <input
              type="text"
              class="form-control"
              name="source"
              id="etl-edge-source"
              data-cy="source"
              :class="{ valid: !v$.source.$invalid, invalid: v$.source.$invalid }"
              v-model="v$.source.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-edge-target">Target</label>
            <input
              type="text"
              class="form-control"
              name="target"
              id="etl-edge-target"
              data-cy="target"
              :class="{ valid: !v$.target.$invalid, invalid: v$.target.$invalid }"
              v-model="v$.target.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-edge-config">Config</label>
            <input
              type="text"
              class="form-control"
              name="config"
              id="etl-edge-config"
              data-cy="config"
              :class="{ valid: !v$.config.$invalid, invalid: v$.config.$invalid }"
              v-model="v$.config.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-edge-status">Status</label>
            <input
              type="text"
              class="form-control"
              name="status"
              id="etl-edge-status"
              data-cy="status"
              :class="{ valid: !v$.status.$invalid, invalid: v$.status.$invalid }"
              v-model="v$.status.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-edge-tenantId">Tenant Id</label>
            <input
              type="text"
              class="form-control"
              name="tenantId"
              id="etl-edge-tenantId"
              data-cy="tenantId"
              :class="{ valid: !v$.tenantId.$invalid, invalid: v$.tenantId.$invalid }"
              v-model="v$.tenantId.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-edge-dr">Dr</label>
            <input
              type="number"
              class="form-control"
              name="dr"
              id="etl-edge-dr"
              data-cy="dr"
              :class="{ valid: !v$.dr.$invalid, invalid: v$.dr.$invalid }"
              v-model.number="v$.dr.$model"
            />
          </div>
        </div>
        <div>
          <button type="button" id="cancel-save" data-cy="entityCreateCancelButton" class="btn btn-secondary" @click="previousState()">
            <font-awesome-icon icon="ban"></font-awesome-icon>&nbsp;<span>取消</span>
          </button>
          <button
            type="submit"
            id="save-entity"
            data-cy="entityCreateSaveButton"
            :disabled="v$.$invalid || isSaving"
            class="btn btn-primary"
          >
            <font-awesome-icon icon="save"></font-awesome-icon>&nbsp;<span>保存</span>
          </button>
        </div>
      </form>
    </div>
  </div>
</template>
<script lang="ts" src="./etl-edge-update.component.ts"></script>
