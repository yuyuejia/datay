<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <div v-if="jobInstance">
        <h2 class="jh-entity-heading" data-cy="jobInstanceDetailsHeading"><span>Job Instance</span> {{ jobInstance.id }}</h2>
        <dl class="row jh-entity-details">
          <dt>
            <span>Instance Code</span>
          </dt>
          <dd>
            <span>{{ jobInstance.instanceCode }}</span>
          </dd>
          <dt>
            <span>Job Name</span>
          </dt>
          <dd>
            <span>{{ jobInstance.jobName }}</span>
          </dd>
          <dt>
            <span>Job Code</span>
          </dt>
          <dd>
            <span>{{ jobInstance.jobCode }}</span>
          </dd>
          <dt>
            <span>Type</span>
          </dt>
          <dd>
            <span>{{ jobInstance.type }}</span>
          </dd>
          <dt>
            <span>Job Context</span>
          </dt>
          <dd>
            <span>{{ jobInstance.jobContext }}</span>
          </dd>
          <dt>
            <span>Status</span>
          </dt>
          <dd>
            <span>{{ jobInstance.status }}</span>
          </dd>
          <dt>
            <span>Job Message</span>
          </dt>
          <dd>
            <span>{{ jobInstance.jobMessage }}</span>
          </dd>
          <dt>
            <span>Exec Node</span>
          </dt>
          <dd>
            <span>{{ jobInstance.execNode }}</span>
          </dd>
          <dt>
            <span>Start Time</span>
          </dt>
          <dd>
            <span>{{ jobInstance.startTime }}</span>
          </dd>
          <dt>
            <span>End Time</span>
          </dt>
          <dd>
            <span>{{ jobInstance.endTime }}</span>
          </dd>
          <dt>
            <span>Create Time</span>
          </dt>
          <dd>
            <span v-if="jobInstance.createTime">{{ formatDateLong(jobInstance.createTime) }}</span>
          </dd>
          <dt>
            <span>Project</span>
          </dt>
          <dd>
            <span>{{ jobInstance.project }}</span>
          </dd>
          <dt>
            <span>Tenant Id</span>
          </dt>
          <dd>
            <span>{{ jobInstance.tenantId }}</span>
          </dd>
        </dl>
        <el-button type="info" native-type="submit" @click.prevent="previousState()" data-cy="entityDetailsBackButton">
          <font-awesome-icon icon="arrow-left"></font-awesome-icon>&nbsp;<span>返回</span>
        </el-button>
        <router-link
          v-if="jobInstance.id"
          :to="{ name: 'JobInstanceEdit', params: { jobInstanceId: jobInstance.id } }"
          custom
          v-slot="{ navigate }"
        >
          <el-button type="primary" @click="navigate">
            <font-awesome-icon icon="pencil-alt"></font-awesome-icon>&nbsp;<span>编辑</span>
          </el-button>
        </router-link>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./job-instance-details.component.ts"></script>
