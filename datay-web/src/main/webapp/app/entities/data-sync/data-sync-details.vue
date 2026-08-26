<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <div v-if="dataSync">
        <h2 class="jh-entity-heading" data-cy="dataSyncDetailsHeading"><span>Data Sync</span> {{ dataSync.id }}</h2>
        <dl class="row jh-entity-details">
          <dt>
            <span>Job Name</span>
          </dt>
          <dd>
            <span>{{ dataSync.jobName }}</span>
          </dd>
          <dt>
            <span>Job Code</span>
          </dt>
          <dd>
            <span>{{ dataSync.jobCode }}</span>
          </dd>
          <dt>
            <span>Job Desc</span>
          </dt>
          <dd>
            <span>{{ dataSync.jobDesc }}</span>
          </dd>
          <dt>
            <span>Dir</span>
          </dt>
          <dd>
            <span>{{ dataSync.dir }}</span>
          </dd>
          <dt>
            <span>Type</span>
          </dt>
          <dd>
            <span>{{ dataSync.type }}</span>
          </dd>
          <dt>
            <span>Source</span>
          </dt>
          <dd>
            <span>{{ dataSync.source }}</span>
          </dd>
          <dt>
            <span>Target</span>
          </dt>
          <dd>
            <span>{{ dataSync.target }}</span>
          </dd>
          <dt>
            <span>Cron</span>
          </dt>
          <dd>
            <span>{{ dataSync.cron }}</span>
          </dd>
          <dt>
            <span>Job Context</span>
          </dt>
          <dd>
            <span>{{ dataSync.jobContext }}</span>
          </dd>
          <dt>
            <span>Status</span>
          </dt>
          <dd>
            <span>{{ dataSync.status }}</span>
          </dd>
          <dt>
            <span>Last Status</span>
          </dt>
          <dd>
            <span>{{ dataSync.lastStatus }}</span>
          </dd>
          <dt>
            <span>Update Time</span>
          </dt>
          <dd>
            <span v-if="dataSync.updateTime">{{ formatDateLong(dataSync.updateTime) }}</span>
          </dd>
          <dt>
            <span>Create Time</span>
          </dt>
          <dd>
            <span v-if="dataSync.createTime">{{ formatDateLong(dataSync.createTime) }}</span>
          </dd>
          <dt>
            <span>Project</span>
          </dt>
          <dd>
            <span>{{ dataSync.project }}</span>
          </dd>
          <dt>
            <span>Tenant Id</span>
          </dt>
          <dd>
            <span>{{ dataSync.tenantId }}</span>
          </dd>
          <dt>
            <span>Dr</span>
          </dt>
          <dd>
            <span>{{ dataSync.dr }}</span>
          </dd>
        </dl>
        <button type="submit" @click.prevent="previousState()" class="btn btn-info" data-cy="entityDetailsBackButton">
          <font-awesome-icon icon="arrow-left"></font-awesome-icon>&nbsp;<span>返回</span>
        </button>
        <router-link v-if="dataSync.id" :to="{ name: 'DataSyncEdit', params: { dataSyncId: dataSync.id } }" custom v-slot="{ navigate }">
          <button @click="navigate" class="btn btn-primary">
            <font-awesome-icon icon="pencil-alt"></font-awesome-icon>&nbsp;<span>编辑</span>
          </button>
        </router-link>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./data-sync-details.component.ts"></script>
