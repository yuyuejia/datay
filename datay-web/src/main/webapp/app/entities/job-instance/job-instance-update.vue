<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <el-form name="editForm" label-width="160px" @submit.prevent="save()">
        <h2 id="datafusionApp.jobInstance.home.createOrEditLabel" data-cy="JobInstanceCreateUpdateHeading">创建或编辑 Job Instance</h2>
        <div>
          <el-form-item v-if="jobInstance.id" label="ID">
            <el-input id="id" name="id" v-model="jobInstance.id" readonly />
          </el-form-item>
          <el-form-item label="Instance Code">
            <el-input name="instanceCode" id="job-instance-instanceCode" data-cy="instanceCode" v-model="v$.instanceCode.$model" />
          </el-form-item>
          <el-form-item label="Job Name">
            <el-input name="jobName" id="job-instance-jobName" data-cy="jobName" v-model="v$.jobName.$model" />
          </el-form-item>
          <el-form-item label="Job Code">
            <el-input name="jobCode" id="job-instance-jobCode" data-cy="jobCode" v-model="v$.jobCode.$model" />
          </el-form-item>
          <el-form-item label="Type">
            <el-input name="type" id="job-instance-type" data-cy="type" v-model="v$.type.$model" />
          </el-form-item>
          <el-form-item label="Job Context">
            <el-input name="jobContext" id="job-instance-jobContext" data-cy="jobContext" v-model="v$.jobContext.$model" />
          </el-form-item>
          <el-form-item label="Status">
            <el-input name="status" id="job-instance-status" data-cy="status" v-model="v$.status.$model" />
          </el-form-item>
          <el-form-item label="Job Message">
            <el-input name="jobMessage" id="job-instance-jobMessage" data-cy="jobMessage" v-model="v$.jobMessage.$model" />
          </el-form-item>
          <el-form-item label="Start Time">
            <el-input name="startTime" id="job-instance-startTime" data-cy="startTime" v-model="v$.startTime.$model" />
          </el-form-item>
          <el-form-item label="End Time">
            <el-input name="endTime" id="job-instance-endTime" data-cy="endTime" v-model="v$.endTime.$model" />
          </el-form-item>
          <el-form-item label="Create Time">
            <div class="d-flex">
              <el-date-picker type="datetime" value-format="YYYY-MM-DDTHH:mm" id="job-instance-createTime" data-cy="createTime" name="createTime" :model-value="convertDateTimeFromServer(v$.createTime.$model)" @update:model-value="value => updateZonedDateTimeField('createTime', value)" />
            </div>
          </el-form-item>
          <el-form-item label="Project">
            <el-input name="project" id="job-instance-project" data-cy="project" v-model="v$.project.$model" />
          </el-form-item>
          <el-form-item label="Tenant Id">
            <el-input name="tenantId" id="job-instance-tenantId" data-cy="tenantId" v-model="v$.tenantId.$model" />
          </el-form-item>
        </div>
        <div>
          <el-button id="cancel-save" data-cy="entityCreateCancelButton" @click="previousState()">
            <font-awesome-icon icon="ban"></font-awesome-icon>&nbsp;<span>取消</span>
          </el-button>
          <el-button type="primary" native-type="submit" id="save-entity" data-cy="entityCreateSaveButton" :disabled="v$.$invalid || isSaving">
            <font-awesome-icon icon="save"></font-awesome-icon>&nbsp;<span>保存</span>
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>
<script lang="ts" src="./job-instance-update.component.ts"></script>
