<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <form name="editForm" novalidate @submit.prevent="save()">
        <h2 id="datafusionApp.jobDepend.home.createOrEditLabel" data-cy="JobDependCreateUpdateHeading">创建或编辑 Job Depend</h2>
        <div>
          <div class="form-group" v-if="jobDepend.id">
            <label for="id">ID</label>
            <input type="text" class="form-control" id="id" name="id" v-model="jobDepend.id" readonly />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-depend-parentJobCode">Parent Job Code</label>
            <input
              type="text"
              class="form-control"
              name="parentJobCode"
              id="job-depend-parentJobCode"
              data-cy="parentJobCode"
              :class="{ valid: !v$.parentJobCode.$invalid, invalid: v$.parentJobCode.$invalid }"
              v-model="v$.parentJobCode.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-depend-childJobCode">Child Job Code</label>
            <input
              type="text"
              class="form-control"
              name="childJobCode"
              id="job-depend-childJobCode"
              data-cy="childJobCode"
              :class="{ valid: !v$.childJobCode.$invalid, invalid: v$.childJobCode.$invalid }"
              v-model="v$.childJobCode.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-depend-jobCode">Job Code</label>
            <input
              type="text"
              class="form-control"
              name="jobCode"
              id="job-depend-jobCode"
              data-cy="jobCode"
              :class="{ valid: !v$.jobCode.$invalid, invalid: v$.jobCode.$invalid }"
              v-model="v$.jobCode.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-depend-lastInterval">Last Interval</label>
            <input
              type="number"
              class="form-control"
              name="lastInterval"
              id="job-depend-lastInterval"
              data-cy="lastInterval"
              :class="{ valid: !v$.lastInterval.$invalid, invalid: v$.lastInterval.$invalid }"
              v-model.number="v$.lastInterval.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="job-depend-createTime">Create Time</label>
            <div class="d-flex">
              <input
                id="job-depend-createTime"
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
            <label class="form-control-label" for="job-depend-tenantId">Tenant Id</label>
            <input
              type="text"
              class="form-control"
              name="tenantId"
              id="job-depend-tenantId"
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
<script lang="ts" src="./job-depend-update.component.ts"></script>
