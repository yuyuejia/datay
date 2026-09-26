<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <div v-if="eTLComponent">
        <h2 class="jh-entity-heading" data-cy="eTLComponentDetailsHeading"><span>ETL Component</span> {{ eTLComponent.id }}</h2>
        <dl class="row jh-entity-details">
          <dt>
            <span>Name</span>
          </dt>
          <dd>
            <span>{{ eTLComponent.name }}</span>
          </dd>
          <dt>
            <span>Code</span>
          </dt>
          <dd>
            <span>{{ eTLComponent.code }}</span>
          </dd>
          <dt>
            <span>Desc</span>
          </dt>
          <dd>
            <span>{{ eTLComponent.desc }}</span>
          </dd>
          <dt>
            <span>Group</span>
          </dt>
          <dd>
            <span>{{ eTLComponent.group }}</span>
          </dd>
          <dt>
            <span>Type</span>
          </dt>
          <dd>
            <span>{{ eTLComponent.type }}</span>
          </dd>
          <dt>
            <span>Config</span>
          </dt>
          <dd>
            <span>{{ eTLComponent.config }}</span>
          </dd>
          <dt>
            <span>Status</span>
          </dt>
          <dd>
            <span>{{ eTLComponent.status }}</span>
          </dd>
          <dt>
            <span>Update Time</span>
          </dt>
          <dd>
            <span v-if="eTLComponent.updateTime">{{ formatDateLong(eTLComponent.updateTime) }}</span>
          </dd>
          <dt>
            <span>Create Time</span>
          </dt>
          <dd>
            <span v-if="eTLComponent.createTime">{{ formatDateLong(eTLComponent.createTime) }}</span>
          </dd>
          <dt>
            <span>Creater</span>
          </dt>
          <dd>
            <span>{{ eTLComponent.creater }}</span>
          </dd>
          <dt>
            <span>Tenant Id</span>
          </dt>
          <dd>
            <span>{{ eTLComponent.tenantId }}</span>
          </dd>
          <dt>
            <span>Dr</span>
          </dt>
          <dd>
            <span>{{ eTLComponent.dr }}</span>
          </dd>
        </dl>
        <el-button type="info" native-type="submit" @click.prevent="previousState()" data-cy="entityDetailsBackButton">
          <font-awesome-icon icon="arrow-left"></font-awesome-icon>&nbsp;<span>返回</span>
        </el-button>
        <router-link
          v-if="eTLComponent.id"
          :to="{ name: 'ETLComponentEdit', params: { eTLComponentId: eTLComponent.id } }"
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

<script lang="ts" src="./etl-component-details.component.ts"></script>
