<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <el-form name="editForm" label-width="160px" @submit.prevent="save()">
        <h2 id="datafusionApp.jobDepend.home.createOrEditLabel" data-cy="JobDependCreateUpdateHeading">创建或编辑 Job Depend</h2>
        <div>
          <el-form-item v-if="jobDepend.id" label="ID">
            <el-input id="id" name="id" v-model="jobDepend.id" readonly />
          </el-form-item>
          <el-form-item label="Parent Job Code">
            <el-input name="parentJobCode" id="job-depend-parentJobCode" data-cy="parentJobCode" v-model="v$.parentJobCode.$model" />
          </el-form-item>
          <el-form-item label="Child Job Code">
            <el-input name="childJobCode" id="job-depend-childJobCode" data-cy="childJobCode" v-model="v$.childJobCode.$model" />
          </el-form-item>
          <el-form-item label="Job Code">
            <el-input name="jobCode" id="job-depend-jobCode" data-cy="jobCode" v-model="v$.jobCode.$model" />
          </el-form-item>
          <el-form-item label="Last Interval">
            <el-input-number :controls="false"  name="lastInterval" id="job-depend-lastInterval" data-cy="lastInterval" v-model="v$.lastInterval.$model" />
          </el-form-item>
          <el-form-item label="Create Time">
            <div class="d-flex">
              <el-date-picker type="datetime" value-format="YYYY-MM-DDTHH:mm" id="job-depend-createTime" data-cy="createTime" name="createTime" :model-value="convertDateTimeFromServer(v$.createTime.$model)" @update:model-value="value => updateZonedDateTimeField('createTime', value)" />
            </div>
          </el-form-item>
          <el-form-item label="Tenant Id">
            <el-input name="tenantId" id="job-depend-tenantId" data-cy="tenantId" v-model="v$.tenantId.$model" />
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
<script lang="ts" src="./job-depend-update.component.ts"></script>
