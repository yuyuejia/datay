<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <el-form name="editForm" label-width="160px" @submit.prevent="save()">
        <h2 id="datafusionApp.eTLTask.home.createOrEditLabel" data-cy="ETLTaskCreateUpdateHeading">创建或编辑 ETL Task</h2>
        <div>
          <el-form-item v-if="eTLTask.id" label="ID">
            <el-input id="id" name="id" v-model="eTLTask.id" readonly />
          </el-form-item>
          <el-form-item label="Task Name">
            <el-input name="taskName" id="etl-task-taskName" data-cy="taskName" v-model="v$.taskName.$model" />
          </el-form-item>
          <el-form-item label="Task Desc">
            <el-input name="taskDesc" id="etl-task-taskDesc" data-cy="taskDesc" v-model="v$.taskDesc.$model" />
          </el-form-item>
          <el-form-item label="Cron">
            <CronExpressionSelector :value="v$.cron.$model" @change="onCronChange" />
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
<script lang="ts" src="./etl-task-update.component.ts"></script>
