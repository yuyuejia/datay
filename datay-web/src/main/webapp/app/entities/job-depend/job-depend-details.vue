<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <div v-if="jobDepend">
        <h2 class="jh-entity-heading" data-cy="jobDependDetailsHeading"><span>Job Depend</span> {{ jobDepend.id }}</h2>
        <dl class="row jh-entity-details">
          <dt>
            <span>Parent Job Code</span>
          </dt>
          <dd>
            <span>{{ jobDepend.parentJobCode }}</span>
          </dd>
          <dt>
            <span>Child Job Code</span>
          </dt>
          <dd>
            <span>{{ jobDepend.childJobCode }}</span>
          </dd>
          <dt>
            <span>Job Code</span>
          </dt>
          <dd>
            <span>{{ jobDepend.jobCode }}</span>
          </dd>
          <dt>
            <span>Last Interval</span>
          </dt>
          <dd>
            <span>{{ jobDepend.lastInterval }}</span>
          </dd>
          <dt>
            <span>Create Time</span>
          </dt>
          <dd>
            <span v-if="jobDepend.createTime">{{ formatDateLong(jobDepend.createTime) }}</span>
          </dd>
          <dt>
            <span>Tenant Id</span>
          </dt>
          <dd>
            <span>{{ jobDepend.tenantId }}</span>
          </dd>
        </dl>
        <button type="submit" @click.prevent="previousState()" class="btn btn-info" data-cy="entityDetailsBackButton">
          <font-awesome-icon icon="arrow-left"></font-awesome-icon>&nbsp;<span>返回</span>
        </button>
        <router-link
          v-if="jobDepend.id"
          :to="{ name: 'JobDependEdit', params: { jobDependId: jobDepend.id } }"
          custom
          v-slot="{ navigate }"
        >
          <button @click="navigate" class="btn btn-primary">
            <font-awesome-icon icon="pencil-alt"></font-awesome-icon>&nbsp;<span>编辑</span>
          </button>
        </router-link>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./job-depend-details.component.ts"></script>
