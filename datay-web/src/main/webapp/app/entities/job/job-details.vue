<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <div v-if="job">
        <h2 class="jh-entity-heading" data-cy="jobDetailsHeading"><span>Job</span> {{ job.id }}</h2>
        <dl class="row jh-entity-details">
          <dt>
            <span>Job Name</span>
          </dt>
          <dd>
            <span>{{ job.jobName }}</span>
          </dd>
          <dt>
            <span>Job Group</span>
          </dt>
          <dd>
            <span>{{ job.jobGroup }}</span>
          </dd>
          <dt>
            <span>Type</span>
          </dt>
          <dd>
            <span>{{ job.type }}</span>
          </dd>
          <dt>
            <span>Cron</span>
          </dt>
          <dd>
            <span>{{ job.cron }}</span>
          </dd>
          <dt>
            <span>Job Context</span>
          </dt>
          <dd>
            <span>{{ job.jobContext }}</span>
          </dd>
          <dt>
            <span>Status</span>
          </dt>
          <dd>
            <span>{{ job.status }}</span>
          </dd>
          <dt>
            <span>Update Time</span>
          </dt>
          <dd>
            <span v-if="job.updateTime">{{ formatDateLong(job.updateTime) }}</span>
          </dd>
          <dt>
            <span>Create Time</span>
          </dt>
          <dd>
            <span v-if="job.createTime">{{ formatDateLong(job.createTime) }}</span>
          </dd>
          <dt>
            <span>Project</span>
          </dt>
          <dd>
            <span>{{ job.project }}</span>
          </dd>
          <dt>
            <span>Tenant Id</span>
          </dt>
          <dd>
            <span>{{ job.tenantId }}</span>
          </dd>
        </dl>
        <button type="submit" @click.prevent="previousState()" class="btn btn-info" data-cy="entityDetailsBackButton">
          <font-awesome-icon icon="arrow-left"></font-awesome-icon>&nbsp;<span>返回</span>
        </button>
        <router-link v-if="job.id" :to="{ name: 'JobEdit', params: { jobId: job.id } }" custom v-slot="{ navigate }">
          <button @click="navigate" class="btn btn-primary">
            <font-awesome-icon icon="pencil-alt"></font-awesome-icon>&nbsp;<span>编辑</span>
          </button>
        </router-link>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./job-details.component.ts"></script>
