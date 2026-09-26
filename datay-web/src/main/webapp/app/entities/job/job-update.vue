<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <el-form name="editForm" label-width="160px" @submit.prevent="save()">
        <h2 id="datafusionApp.job.home.createOrEditLabel" data-cy="JobCreateUpdateHeading">创建或编辑 Job</h2>
        <div>
          <el-form-item v-if="job.id" label="ID">
            <el-input id="id" name="id" v-model="job.id" readonly />
          </el-form-item>
          <el-form-item label="Job Name">
            <el-input name="jobName" id="job-jobName" data-cy="jobName" v-model="v$.jobName.$model" />
          </el-form-item>
          <el-form-item label="Job Group">
            <el-input name="jobGroup" id="job-jobGroup" data-cy="jobGroup" v-model="v$.jobGroup.$model" />
          </el-form-item>
          <el-form-item label="Type">
            <el-input name="type" id="job-type" data-cy="type" v-model="v$.type.$model" />
          </el-form-item>
          <el-form-item label="Cron">
            <el-input name="cron" id="job-cron" data-cy="cron" v-model="v$.cron.$model" />
          </el-form-item>
          <el-form-item label="Job Context">
            <el-input name="jobContext" id="job-jobContext" data-cy="jobContext" v-model="v$.jobContext.$model" />
          </el-form-item>
          <el-form-item label="Status">
            <el-input name="status" id="job-status" data-cy="status" v-model="v$.status.$model" />
          </el-form-item>
          <el-form-item label="Update Time">
            <div class="d-flex">
              <el-date-picker type="datetime" value-format="YYYY-MM-DDTHH:mm" id="job-updateTime" data-cy="updateTime" name="updateTime" :model-value="convertDateTimeFromServer(v$.updateTime.$model)" @update:model-value="value => updateZonedDateTimeField('updateTime', value)" />
            </div>
          </el-form-item>
          <el-form-item label="Create Time">
            <div class="d-flex">
              <el-date-picker type="datetime" value-format="YYYY-MM-DDTHH:mm" id="job-createTime" data-cy="createTime" name="createTime" :model-value="convertDateTimeFromServer(v$.createTime.$model)" @update:model-value="value => updateZonedDateTimeField('createTime', value)" />
            </div>
          </el-form-item>
          <el-form-item label="Project">
            <el-input name="project" id="job-project" data-cy="project" v-model="v$.project.$model" />
          </el-form-item>
          <el-form-item label="Tenant Id">
            <el-input name="tenantId" id="job-tenantId" data-cy="tenantId" v-model="v$.tenantId.$model" />
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
<script lang="ts" src="./job-update.component.ts"></script>
