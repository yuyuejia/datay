<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <div v-if="serviceConfig">
        <h2 class="jh-entity-heading" data-cy="serviceConfigDetailsHeading"><span>Service Config</span> {{ serviceConfig.id }}</h2>
        <dl class="row jh-entity-details">
          <dt>
            <span>Df Group</span>
          </dt>
          <dd>
            <span>{{ serviceConfig.dfGroup }}</span>
          </dd>
          <dt>
            <span>Df Key</span>
          </dt>
          <dd>
            <span>{{ serviceConfig.dfKey }}</span>
          </dd>
          <dt>
            <span>Df Value</span>
          </dt>
          <dd>
            <span>{{ serviceConfig.dfValue }}</span>
          </dd>
          <dt>
            <span>Create Time</span>
          </dt>
          <dd>
            <span v-if="serviceConfig.createTime">{{ formatDateLong(serviceConfig.createTime) }}</span>
          </dd>
          <dt>
            <span>Ytenant Id</span>
          </dt>
          <dd>
            <span>{{ serviceConfig.ytenantId }}</span>
          </dd>
        </dl>
        <button type="submit" @click.prevent="previousState()" class="btn btn-info" data-cy="entityDetailsBackButton">
          <font-awesome-icon icon="arrow-left"></font-awesome-icon>&nbsp;<span>返回</span>
        </button>
        <router-link
          v-if="serviceConfig.id"
          :to="{ name: 'ServiceConfigEdit', params: { serviceConfigId: serviceConfig.id } }"
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

<script lang="ts" src="./service-config-details.component.ts"></script>
