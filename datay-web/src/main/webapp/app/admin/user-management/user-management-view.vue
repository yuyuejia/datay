<template>
  <div class="row justify-content-center">
    <div class="col-8">
      <div v-if="user">
        <h2 class="jh-entity-heading">
          <span>用户</span> [<strong>{{ user.login }}</strong
          >]
        </h2>
        <dl class="row jh-entity-details">
          <dt><span>登录</span></dt>
          <dd>
            <span>{{ user.login }}</span>
            <b-badge variant="success" v-if="user.activated">已激活</b-badge>
            <b-badge variant="danger" v-if="!user.activated">失效</b-badge>
          </dd>
          <dt><span>名字</span></dt>
          <dd>{{ user.firstName }}</dd>
          <dt><span>姓氏</span></dt>
          <dd>{{ user.lastName }}</dd>
          <dt><span>邮箱</span></dt>
          <dd>{{ user.email }}</dd>
          <dt><span>创建人</span></dt>
          <dd>{{ user.createdBy }}</dd>
          <dt><span>创建时间</span></dt>
          <dd>
            <span v-if="user.createdDate">
              {{ formatDate(user.createdDate) }}
            </span>
          </dd>
          <dt><span>最近修改人</span></dt>
          <dd>{{ user.lastModifiedBy }}</dd>
          <dt><span>最近修改时间</span></dt>
          <dd>
            <span v-if="user.lastModifiedDate">
              {{ formatDate(user.lastModifiedDate) }}
            </span>
          </dd>
          <dt><span>角色</span></dt>
          <dd>
            <ul class="list-unstyled">
              <li v-for="authority of user.authorities" :key="authority">
                <b-badge variant="info">{{ authority }}</b-badge>
              </li>
            </ul>
          </dd>
        </dl>
        <router-link custom v-slot="{ navigate }" :to="{ name: 'JhiUser' }">
          <button @click="navigate" class="btn btn-info">
            <font-awesome-icon icon="arrow-left"></font-awesome-icon>&nbsp;<span>返回</span>
          </button>
        </router-link>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./user-management-view.component.ts"></script>
