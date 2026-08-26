<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <form name="editForm" novalidate @submit.prevent="save()">
        <h2 id="datafusionApp.job.home.createOrEditLabel" data-cy="JobCreateUpdateHeading">创建或编辑 Job</h2>
        <div>
          <div class="form-group" v-if="job.id">
            <label for="id">ID</label>
            <input type="text" class="form-control" id="id" name="id" v-model="job.id" readonly />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-jobName">Job Name</label>
            <input
              type="text"
              class="form-control"
              name="jobName"
              id="job-jobName"
              data-cy="jobName"
              :class="{ valid: !v$.jobName.$invalid, invalid: v$.jobName.$invalid }"
              v-model="v$.jobName.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-jobGroup">Job Group</label>
            <input
              type="text"
              class="form-control"
              name="jobGroup"
              id="job-jobGroup"
              data-cy="jobGroup"
              :class="{ valid: !v$.jobGroup.$invalid, invalid: v$.jobGroup.$invalid }"
              v-model="v$.jobGroup.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-type">Type</label>
            <input
              type="text"
              class="form-control"
              name="type"
              id="job-type"
              data-cy="type"
              :class="{ valid: !v$.type.$invalid, invalid: v$.type.$invalid }"
              v-model="v$.type.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-cron">Cron</label>
            <input
              type="text"
              class="form-control"
              name="cron"
              id="job-cron"
              data-cy="cron"
              :class="{ valid: !v$.cron.$invalid, invalid: v$.cron.$invalid }"
              v-model="v$.cron.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-jobContext">Job Context</label>
            <input
              type="text"
              class="form-control"
              name="jobContext"
              id="job-jobContext"
              data-cy="jobContext"
              :class="{ valid: !v$.jobContext.$invalid, invalid: v$.jobContext.$invalid }"
              v-model="v$.jobContext.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-status">Status</label>
            <input
              type="text"
              class="form-control"
              name="status"
              id="job-status"
              data-cy="status"
              :class="{ valid: !v$.status.$invalid, invalid: v$.status.$invalid }"
              v-model="v$.status.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-updateTime">Update Time</label>
            <div class="d-flex">
              <input
                id="job-updateTime"
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
            <label class="form-control-label" for="job-createTime">Create Time</label>
            <div class="d-flex">
              <input
                id="job-createTime"
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
            <label class="form-control-label" for="job-project">Project</label>
            <input
              type="text"
              class="form-control"
              name="project"
              id="job-project"
              data-cy="project"
              :class="{ valid: !v$.project.$invalid, invalid: v$.project.$invalid }"
              v-model="v$.project.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-tenantId">Tenant Id</label>
            <input
              type="text"
              class="form-control"
              name="tenantId"
              id="job-tenantId"
              data-cy="tenantId"
              :class="{ valid: !v$.tenantId.$invalid, invalid: v$.tenantId.$invalid }"
              v-model="v$.tenantId.$model"
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
<script lang="ts" src="./job-update.component.ts"></script>
