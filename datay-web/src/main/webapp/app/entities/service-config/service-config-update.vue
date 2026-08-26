<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <form name="editForm" novalidate @submit.prevent="save()">
        <h2 id="datafusionApp.serviceConfig.home.createOrEditLabel" data-cy="ServiceConfigCreateUpdateHeading">
          创建或编辑 Service Config
        </h2>
        <div>
          <div class="form-group" v-if="serviceConfig.id">
            <label for="id">ID</label>
            <input type="text" class="form-control" id="id" name="id" v-model="serviceConfig.id" readonly />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="service-config-dfGroup">Df Group</label>
            <input
              type="text"
              class="form-control"
              name="dfGroup"
              id="service-config-dfGroup"
              data-cy="dfGroup"
              :class="{ valid: !v$.dfGroup.$invalid, invalid: v$.dfGroup.$invalid }"
              v-model="v$.dfGroup.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="service-config-dfKey">Df Key</label>
            <input
              type="text"
              class="form-control"
              name="dfKey"
              id="service-config-dfKey"
              data-cy="dfKey"
              :class="{ valid: !v$.dfKey.$invalid, invalid: v$.dfKey.$invalid }"
              v-model="v$.dfKey.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="service-config-dfValue">Df Value</label>
            <input
              type="text"
              class="form-control"
              name="dfValue"
              id="service-config-dfValue"
              data-cy="dfValue"
              :class="{ valid: !v$.dfValue.$invalid, invalid: v$.dfValue.$invalid }"
              v-model="v$.dfValue.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="service-config-createTime">Create Time</label>
            <div class="d-flex">
              <input
                id="service-config-createTime"
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
            <label class="form-control-label" for="service-config-ytenantId">Ytenant Id</label>
            <input
              type="text"
              class="form-control"
              name="ytenantId"
              id="service-config-ytenantId"
              data-cy="ytenantId"
              :class="{ valid: !v$.ytenantId.$invalid, invalid: v$.ytenantId.$invalid }"
              v-model="v$.ytenantId.$model"
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
<script lang="ts" src="./service-config-update.component.ts"></script>
