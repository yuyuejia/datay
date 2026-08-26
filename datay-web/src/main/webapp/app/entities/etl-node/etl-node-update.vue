<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <form name="editForm" novalidate @submit.prevent="save()">
        <h2 id="datafusionApp.eTLNode.home.createOrEditLabel" data-cy="ETLNodeCreateUpdateHeading">创建或编辑 ETL Node</h2>
        <div>
          <div class="form-group" v-if="eTLNode.id">
            <label for="id">ID</label>
            <input type="text" class="form-control" id="id" name="id" v-model="eTLNode.id" readonly />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-node-taskId">Task Id</label>
            <input
              type="text"
              class="form-control"
              name="taskId"
              id="etl-node-taskId"
              data-cy="taskId"
              :class="{ valid: !v$.taskId.$invalid, invalid: v$.taskId.$invalid }"
              v-model="v$.taskId.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-node-label">Label</label>
            <input
              type="text"
              class="form-control"
              name="label"
              id="etl-node-label"
              data-cy="label"
              :class="{ valid: !v$.label.$invalid, invalid: v$.label.$invalid }"
              v-model="v$.label.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-node-code">Code</label>
            <input
              type="text"
              class="form-control"
              name="code"
              id="etl-node-code"
              data-cy="code"
              :class="{ valid: !v$.code.$invalid, invalid: v$.code.$invalid }"
              v-model="v$.code.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-node-desc">Desc</label>
            <input
              type="text"
              class="form-control"
              name="desc"
              id="etl-node-desc"
              data-cy="desc"
              :class="{ valid: !v$.desc.$invalid, invalid: v$.desc.$invalid }"
              v-model="v$.desc.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-node-type">Type</label>
            <input
              type="text"
              class="form-control"
              name="type"
              id="etl-node-type"
              data-cy="type"
              :class="{ valid: !v$.type.$invalid, invalid: v$.type.$invalid }"
              v-model="v$.type.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-node-config">Config</label>
            <input
              type="text"
              class="form-control"
              name="config"
              id="etl-node-config"
              data-cy="config"
              :class="{ valid: !v$.config.$invalid, invalid: v$.config.$invalid }"
              v-model="v$.config.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-node-xAxis">X Axis</label>
            <input
              type="text"
              class="form-control"
              name="xAxis"
              id="etl-node-xAxis"
              data-cy="xAxis"
              :class="{ valid: !v$.xAxis.$invalid, invalid: v$.xAxis.$invalid }"
              v-model="v$.xAxis.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-node-yAxis">Y Axis</label>
            <input
              type="text"
              class="form-control"
              name="yAxis"
              id="etl-node-yAxis"
              data-cy="yAxis"
              :class="{ valid: !v$.yAxis.$invalid, invalid: v$.yAxis.$invalid }"
              v-model="v$.yAxis.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-node-status">Status</label>
            <input
              type="text"
              class="form-control"
              name="status"
              id="etl-node-status"
              data-cy="status"
              :class="{ valid: !v$.status.$invalid, invalid: v$.status.$invalid }"
              v-model="v$.status.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-node-updateTime">Update Time</label>
            <div class="d-flex">
              <input
                id="etl-node-updateTime"
                data-cy="updateTime"
                type="datetime-local"
                class="form-control"
                name="updateTime"
                :class="{ valid: !v$.updateTime.$invalid, invalid: v$.updateTime.$invalid }"
                :value="convertDateTimeFromServer(v$.updateTime.$model)"
                @change="updateZonedDateTimeField('updateTime', $event)"
              />
            </div>
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-node-createTime">Create Time</label>
            <div class="d-flex">
              <input
                id="etl-node-createTime"
                data-cy="createTime"
                type="datetime-local"
                class="form-control"
                name="createTime"
                :class="{ valid: !v$.createTime.$invalid, invalid: v$.createTime.$invalid }"
                :value="convertDateTimeFromServer(v$.createTime.$model)"
                @change="updateZonedDateTimeField('createTime', $event)"
              />
            </div>
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-node-tenantId">Tenant Id</label>
            <input
              type="text"
              class="form-control"
              name="tenantId"
              id="etl-node-tenantId"
              data-cy="tenantId"
              :class="{ valid: !v$.tenantId.$invalid, invalid: v$.tenantId.$invalid }"
              v-model="v$.tenantId.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-node-dr">Dr</label>
            <input
              type="number"
              class="form-control"
              name="dr"
              id="etl-node-dr"
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
<script lang="ts" src="./etl-node-update.component.ts"></script>
