<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <form name="editForm" novalidate @submit.prevent="save()">
        <h2 id="datafusionApp.jobInstance.home.createOrEditLabel" data-cy="JobInstanceCreateUpdateHeading">创建或编辑 Job Instance</h2>
        <div>
          <div class="form-group" v-if="jobInstance.id">
            <label for="id">ID</label>
            <input type="text" class="form-control" id="id" name="id" v-model="jobInstance.id" readonly />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-instance-instanceCode">Instance Code</label>
            <input
              type="text"
              class="form-control"
              name="instanceCode"
              id="job-instance-instanceCode"
              data-cy="instanceCode"
              :class="{ valid: !v$.instanceCode.$invalid, invalid: v$.instanceCode.$invalid }"
              v-model="v$.instanceCode.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-instance-jobName">Job Name</label>
            <input
              type="text"
              class="form-control"
              name="jobName"
              id="job-instance-jobName"
              data-cy="jobName"
              :class="{ valid: !v$.jobName.$invalid, invalid: v$.jobName.$invalid }"
              v-model="v$.jobName.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-instance-jobCode">Job Code</label>
            <input
              type="text"
              class="form-control"
              name="jobCode"
              id="job-instance-jobCode"
              data-cy="jobCode"
              :class="{ valid: !v$.jobCode.$invalid, invalid: v$.jobCode.$invalid }"
              v-model="v$.jobCode.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-instance-type">Type</label>
            <input
              type="text"
              class="form-control"
              name="type"
              id="job-instance-type"
              data-cy="type"
              :class="{ valid: !v$.type.$invalid, invalid: v$.type.$invalid }"
              v-model="v$.type.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-instance-jobContext">Job Context</label>
            <input
              type="text"
              class="form-control"
              name="jobContext"
              id="job-instance-jobContext"
              data-cy="jobContext"
              :class="{ valid: !v$.jobContext.$invalid, invalid: v$.jobContext.$invalid }"
              v-model="v$.jobContext.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-instance-status">Status</label>
            <input
              type="text"
              class="form-control"
              name="status"
              id="job-instance-status"
              data-cy="status"
              :class="{ valid: !v$.status.$invalid, invalid: v$.status.$invalid }"
              v-model="v$.status.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-instance-jobMessage">Job Message</label>
            <input
              type="text"
              class="form-control"
              name="jobMessage"
              id="job-instance-jobMessage"
              data-cy="jobMessage"
              :class="{ valid: !v$.jobMessage.$invalid, invalid: v$.jobMessage.$invalid }"
              v-model="v$.jobMessage.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-instance-startTime">Start Time</label>
            <input
              type="text"
              class="form-control"
              name="startTime"
              id="job-instance-startTime"
              data-cy="startTime"
              :class="{ valid: !v$.startTime.$invalid, invalid: v$.startTime.$invalid }"
              v-model="v$.startTime.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-instance-endTime">End Time</label>
            <input
              type="text"
              class="form-control"
              name="endTime"
              id="job-instance-endTime"
              data-cy="endTime"
              :class="{ valid: !v$.endTime.$invalid, invalid: v$.endTime.$invalid }"
              v-model="v$.endTime.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-instance-createTime">Create Time</label>
            <div class="d-flex">
              <input
                id="job-instance-createTime"
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
            <label class="form-control-label" for="job-instance-project">Project</label>
            <input
              type="text"
              class="form-control"
              name="project"
              id="job-instance-project"
              data-cy="project"
              :class="{ valid: !v$.project.$invalid, invalid: v$.project.$invalid }"
              v-model="v$.project.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-instance-tenantId">Tenant Id</label>
            <input
              type="text"
              class="form-control"
              name="tenantId"
              id="job-instance-tenantId"
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
<script lang="ts" src="./job-instance-update.component.ts"></script>
