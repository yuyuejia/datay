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
            <el-tag type="success" v-if="user.activated">已激活</el-tag>
            <el-tag type="danger" v-if="!user.activated">失效</el-tag>
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
                <el-tag type="info">{{ authority }}</el-tag>
              </li>
            </ul>
          </dd>
        </dl>
        <router-link custom v-slot="{ navigate }" :to="{ name: 'JhiUser' }">
          <el-button type="info" @click="navigate">
            <font-awesome-icon icon="arrow-left"></font-awesome-icon>&nbsp;<span>返回</span>
          </el-button>
        </router-link>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./user-management-view.component.ts"></script>
