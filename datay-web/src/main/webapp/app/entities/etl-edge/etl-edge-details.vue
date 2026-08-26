<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <div v-if="eTLEdge">
        <h2 class="jh-entity-heading" data-cy="eTLEdgeDetailsHeading"><span>ETL Edge</span> {{ eTLEdge.id }}</h2>
        <dl class="row jh-entity-details">
          <dt>
            <span>Task Id</span>
          </dt>
          <dd>
            <span>{{ eTLEdge.taskId }}</span>
          </dd>
          <dt>
            <span>Name</span>
          </dt>
          <dd>
            <span>{{ eTLEdge.name }}</span>
          </dd>
          <dt>
            <span>Code</span>
          </dt>
          <dd>
            <span>{{ eTLEdge.code }}</span>
          </dd>
          <dt>
            <span>Source</span>
          </dt>
          <dd>
            <span>{{ eTLEdge.source }}</span>
          </dd>
          <dt>
            <span>Target</span>
          </dt>
          <dd>
            <span>{{ eTLEdge.target }}</span>
          </dd>
          <dt>
            <span>Config</span>
          </dt>
          <dd>
            <span>{{ eTLEdge.config }}</span>
          </dd>
          <dt>
            <span>Status</span>
          </dt>
          <dd>
            <span>{{ eTLEdge.status }}</span>
          </dd>
          <dt>
            <span>Tenant Id</span>
          </dt>
          <dd>
            <span>{{ eTLEdge.tenantId }}</span>
          </dd>
          <dt>
            <span>Dr</span>
          </dt>
          <dd>
            <span>{{ eTLEdge.dr }}</span>
          </dd>
        </dl>
        <button type="submit" @click.prevent="previousState()" class="btn btn-info" data-cy="entityDetailsBackButton">
          <font-awesome-icon icon="arrow-left"></font-awesome-icon>&nbsp;<span>返回</span>
        </button>
        <router-link v-if="eTLEdge.id" :to="{ name: 'ETLEdgeEdit', params: { eTLEdgeId: eTLEdge.id } }" custom v-slot="{ navigate }">
          <button @click="navigate" class="btn btn-primary">
            <font-awesome-icon icon="pencil-alt"></font-awesome-icon>&nbsp;<span>编辑</span>
          </button>
        </router-link>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./etl-edge-details.component.ts"></script>
