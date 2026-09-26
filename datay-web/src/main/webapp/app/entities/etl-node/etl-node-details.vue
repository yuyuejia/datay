<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <div v-if="eTLNode">
        <h2 class="jh-entity-heading" data-cy="eTLNodeDetailsHeading"><span>ETL Node</span> {{ eTLNode.id }}</h2>
        <dl class="row jh-entity-details">
          <dt>
            <span>Task Id</span>
          </dt>
          <dd>
            <span>{{ eTLNode.taskId }}</span>
          </dd>
          <dt>
            <span>Label</span>
          </dt>
          <dd>
            <span>{{ eTLNode.label }}</span>
          </dd>
          <dt>
            <span>Code</span>
          </dt>
          <dd>
            <span>{{ eTLNode.code }}</span>
          </dd>
          <dt>
            <span>Desc</span>
          </dt>
          <dd>
            <span>{{ eTLNode.desc }}</span>
          </dd>
          <dt>
            <span>Type</span>
          </dt>
          <dd>
            <span>{{ eTLNode.type }}</span>
          </dd>
          <dt>
            <span>Config</span>
          </dt>
          <dd>
            <span>{{ eTLNode.config }}</span>
          </dd>
          <dt>
            <span>X Axis</span>
          </dt>
          <dd>
            <span>{{ eTLNode.xAxis }}</span>
          </dd>
          <dt>
            <span>Y Axis</span>
          </dt>
          <dd>
            <span>{{ eTLNode.yAxis }}</span>
          </dd>
          <dt>
            <span>Status</span>
          </dt>
          <dd>
            <span>{{ eTLNode.status }}</span>
          </dd>
          <dt>
            <span>Update Time</span>
          </dt>
          <dd>
            <span v-if="eTLNode.updateTime">{{ formatDateLong(eTLNode.updateTime) }}</span>
          </dd>
          <dt>
            <span>Create Time</span>
          </dt>
          <dd>
            <span v-if="eTLNode.createTime">{{ formatDateLong(eTLNode.createTime) }}</span>
          </dd>
          <dt>
            <span>Tenant Id</span>
          </dt>
          <dd>
            <span>{{ eTLNode.tenantId }}</span>
          </dd>
          <dt>
            <span>Dr</span>
          </dt>
          <dd>
            <span>{{ eTLNode.dr }}</span>
          </dd>
        </dl>
        <el-button type="info" native-type="submit" @click.prevent="previousState()" data-cy="entityDetailsBackButton">
          <font-awesome-icon icon="arrow-left"></font-awesome-icon>&nbsp;<span>返回</span>
        </el-button>
        <router-link v-if="eTLNode.id" :to="{ name: 'ETLNodeEdit', params: { eTLNodeId: eTLNode.id } }" custom v-slot="{ navigate }">
          <el-button type="primary" @click="navigate">
            <font-awesome-icon icon="pencil-alt"></font-awesome-icon>&nbsp;<span>编辑</span>
          </el-button>
        </router-link>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./etl-node-details.component.ts"></script>
