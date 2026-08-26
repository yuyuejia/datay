<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <form name="editForm" novalidate @submit.prevent="save()">
        <h2 id="datafusionApp.eTLComponent.home.createOrEditLabel" data-cy="ETLComponentCreateUpdateHeading">创建或编辑 ETL Component</h2>
        <div>
          <div class="form-group" v-if="eTLComponent.id">
            <label for="id">ID</label>
            <input type="text" class="form-control" id="id" name="id" v-model="eTLComponent.id" readonly />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-component-name">Name</label>
            <input
              type="text"
              class="form-control"
              name="name"
              id="etl-component-name"
              data-cy="name"
              :class="{ valid: !v$.name.$invalid, invalid: v$.name.$invalid }"
              v-model="v$.name.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-component-code">Code</label>
            <input
              type="text"
              class="form-control"
              name="code"
              id="etl-component-code"
              data-cy="code"
              :class="{ valid: !v$.code.$invalid, invalid: v$.code.$invalid }"
              v-model="v$.code.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-component-desc">Desc</label>
            <input
              type="text"
              class="form-control"
              name="desc"
              id="etl-component-desc"
              data-cy="desc"
              :class="{ valid: !v$.desc.$invalid, invalid: v$.desc.$invalid }"
              v-model="v$.desc.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-component-group">Group</label>
            <input
              type="text"
              class="form-control"
              name="group"
              id="etl-component-group"
              data-cy="group"
              :class="{ valid: !v$.group.$invalid, invalid: v$.group.$invalid }"
              v-model="v$.group.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-component-type">Type</label>
            <input
              type="text"
              class="form-control"
              name="type"
              id="etl-component-type"
              data-cy="type"
              :class="{ valid: !v$.type.$invalid, invalid: v$.type.$invalid }"
              v-model="v$.type.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-component-config">Config</label>
            <input
              type="text"
              class="form-control"
              name="config"
              id="etl-component-config"
              data-cy="config"
              :class="{ valid: !v$.config.$invalid, invalid: v$.config.$invalid }"
              v-model="v$.config.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-component-status">Status</label>
            <input
              type="text"
              class="form-control"
              name="status"
              id="etl-component-status"
              data-cy="status"
              :class="{ valid: !v$.status.$invalid, invalid: v$.status.$invalid }"
              v-model="v$.status.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-component-updateTime">Update Time</label>
            <div class="d-flex">
              <input
                id="etl-component-updateTime"
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
            <label class="form-control-label" for="etl-component-createTime">Create Time</label>
            <div class="d-flex">
              <input
                id="etl-component-createTime"
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
            <label class="form-control-label" for="etl-component-creater">Creater</label>
            <input
              type="text"
              class="form-control"
              name="creater"
              id="etl-component-creater"
              data-cy="creater"
              :class="{ valid: !v$.creater.$invalid, invalid: v$.creater.$invalid }"
              v-model="v$.creater.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-component-tenantId">Tenant Id</label>
            <input
              type="text"
              class="form-control"
              name="tenantId"
              id="etl-component-tenantId"
              data-cy="tenantId"
              :class="{ valid: !v$.tenantId.$invalid, invalid: v$.tenantId.$invalid }"
              v-model="v$.tenantId.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-component-dr">Dr</label>
            <input
              type="number"
              class="form-control"
              name="dr"
              id="etl-component-dr"
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
<script lang="ts" src="./etl-component-update.component.ts"></script>
