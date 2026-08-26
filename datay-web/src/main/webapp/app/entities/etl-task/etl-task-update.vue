<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <form name="editForm" novalidate @submit.prevent="save()">
        <h2 id="datafusionApp.eTLTask.home.createOrEditLabel" data-cy="ETLTaskCreateUpdateHeading">创建或编辑 ETL Task</h2>
        <div>
          <div class="form-group" v-if="eTLTask.id">
            <label for="id">ID</label>
            <input type="text" class="form-control" id="id" name="id" v-model="eTLTask.id" readonly />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-task-taskName">Task Name</label>
            <input
              type="text"
              class="form-control"
              name="taskName"
              id="etl-task-taskName"
              data-cy="taskName"
              :class="{ valid: !v$.taskName.$invalid, invalid: v$.taskName.$invalid }"
              v-model="v$.taskName.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-task-taskDesc">Task Desc</label>
            <input
              type="text"
              class="form-control"
              name="taskDesc"
              id="etl-task-taskDesc"
              data-cy="taskDesc"
              :class="{ valid: !v$.taskDesc.$invalid, invalid: v$.taskDesc.$invalid }"
              v-model="v$.taskDesc.$model"
            />
          </div>
          <div class="form-group">
            <label class="form-control-label" for="etl-task-cron">Cron</label>
            <CronExpressionSelector :value="v$.cron.$model" @change="onCronChange" />
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
<script lang="ts" src="./etl-task-update.component.ts"></script>
