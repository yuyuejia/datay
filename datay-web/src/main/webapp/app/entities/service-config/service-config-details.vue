<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <div v-if="serviceConfig">
        <h2 class="jh-entity-heading" data-cy="serviceConfigDetailsHeading"><span>服务配置</span> {{ serviceConfig.id }}</h2>
        <dl class="row jh-entity-details">
          <dt>
            <span>配置分组</span>
          </dt>
          <dd>
            <span>{{ serviceConfig.dfGroup }}</span>
          </dd>
          <dt>
            <span>配置项</span>
          </dt>
          <dd>
            <span>{{ serviceConfig.dfKey }}</span>
          </dd>
          <dt>
            <span>配置值</span>
          </dt>
          <dd>
            <span>{{ serviceConfig.dfValue }}</span>
          </dd>
          <dt>
            <span>创建时间</span>
          </dt>
          <dd>
            <span v-if="serviceConfig.createTime">{{ formatDateLong(serviceConfig.createTime) }}</span>
          </dd>
          <dt>
            <span>租户ID</span>
          </dt>
          <dd>
            <span>{{ serviceConfig.tenantId }}</span>
          </dd>
        </dl>
        <el-button type="info" native-type="submit" @click.prevent="previousState()" data-cy="entityDetailsBackButton">
          <font-awesome-icon icon="arrow-left"></font-awesome-icon>&nbsp;<span>返回</span>
        </el-button>
        <router-link
          v-if="serviceConfig.id"
          :to="{ name: 'ServiceConfigEdit', params: { serviceConfigId: serviceConfig.id } }"
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

<script lang="ts" src="./service-config-details.component.ts"></script>
