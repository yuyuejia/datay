<template>
  <nav data-cy="navbar" class="navbar navbar-expand-md navbar-dark jh-navbar">
    <router-link class="navbar-brand logo" to="/">
      <span class="navbar-title">Data<span class="logo-y">Y</span></span>
    </router-link>
    <button
      class="navbar-toggler jh-navbar-toggler d-lg-none"
      type="button"
      data-toggle="collapse"
      data-target="#header-tabs"
      aria-controls="header-tabs"
      aria-expanded="false"
      aria-label="Toggle navigation"
    >
      <font-awesome-icon icon="bars" />
    </button>

    <div class="collapse navbar-collapse" id="header-tabs">
      <ul class="navbar-nav">
        <li v-if="authenticated" class="nav-item dropdown pointer">
          <el-dropdown trigger="click" data-cy="tenantMenu" popper-class="jh-dropdown-menu">
            <a href="javascript:void(0)" class="nav-link dropdown-toggle">
              <font-awesome-icon icon="building" class="mr-1" />
              <span class="no-bold">{{ currentTenantDisplay }}</span>
            </a>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="t in availableTenants"
                  :key="t.id"
                  @click="switchTenant(t)"
                  :class="{ active: currentTenantId === t.id }"
                >
                  <font-awesome-icon v-if="currentTenantId === t.id" icon="check" class="mr-1 text-success" />
                  <span v-else class="mr-1"></span>
                  <span>{{ t.name }} <small class="text-muted">({{ t.code }})</small></span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </li>
      </ul>
      <ul class="navbar-nav ml-auto">
        <li class="nav-item">
          <router-link class="nav-link" to="/">
            <span>首页</span>
          </router-link>
        </li>
        <li v-if="authenticated" class="nav-item dropdown pointer">
          <el-dropdown trigger="click" id="datasource-menu" @command="goToMenu" popper-class="jh-dropdown-menu">
            <a href="javascript:void(0)" class="nav-link dropdown-toggle">
              <span class="no-bold">数据源</span>
            </a>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="/data-source">
                  <span>数据源管理</span>
                </el-dropdown-item>
                <el-dropdown-item command="/file-management">
                  <span>文件管理</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </li>
        <li v-if="authenticated" class="nav-item">
          <router-link class="nav-link" to="/etl-task">
            <span>数据集成</span>
          </router-link>
        </li>
        <li v-if="authenticated" class="nav-item dropdown pointer">
          <el-dropdown trigger="click" id="model-menu" @command="goToMenu" popper-class="jh-dropdown-menu">
            <a href="javascript:void(0)" class="nav-link dropdown-toggle">
              <span class="no-bold">数据模型</span>
            </a>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="/data-model">
                  <span>维度建模</span>
                </el-dropdown-item>
                <el-dropdown-item command="/metric">
                  <span>指标管理</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </li>
        <li v-if="authenticated" class="nav-item dropdown pointer">
          <el-dropdown trigger="click" id="entity-menu" data-cy="entity" @command="goToMenu" popper-class="jh-dropdown-menu">
            <a href="javascript:void(0)" class="nav-link dropdown-toggle">
              <span class="no-bold">数据开发</span>
            </a>
            <template #dropdown>
              <el-dropdown-menu>
                <entities-menu></entities-menu>
                <!-- jhipster-needle-add-entity-to-menu - JHipster will add entities to the menu here -->
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </li>
        <li v-if="authenticated" class="nav-item dropdown pointer">
          <el-dropdown trigger="click" id="application-menu" @command="goToMenu" popper-class="jh-dropdown-menu">
            <a href="javascript:void(0)" class="nav-link dropdown-toggle">
              <span class="no-bold">数据应用</span>
            </a>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="/metric-ai">
                  <span>智能问数</span>
                </el-dropdown-item>
                <el-dropdown-item command="/data-api">
                  <span>API 服务</span>
                </el-dropdown-item>
                <el-dropdown-item command="/analysis-dashboard">
                  <span>分析看板</span>
                </el-dropdown-item>
                <el-dropdown-item command="/app-package">
                  <span>应用市场</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </li>
        <li class="nav-item dropdown pointer">
          <el-dropdown trigger="click" id="account-menu" data-cy="accountMenu" @command="goToMenu" popper-class="jh-dropdown-menu">
            <a href="javascript:void(0)" class="nav-link dropdown-toggle">
              <span class="no-bold">{{ accountLabel }}</span>
            </a>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="authenticated" data-cy="settings" command="/account/settings">
                  <font-awesome-icon icon="wrench" />
                  <span>设置</span>
                </el-dropdown-item>
                <el-dropdown-item v-if="authenticated" data-cy="passwordItem" command="/account/password">
                  <font-awesome-icon icon="lock" />
                  <span>密码</span>
                </el-dropdown-item>
                <el-dropdown-item v-if="hasAnyAuthority('ROLE_ADMIN') && authenticated" command="/admin/user-management">
                  <span>用户管理</span>
                </el-dropdown-item>
                <el-dropdown-item v-if="hasAnyAuthority('ROLE_ADMIN') && authenticated" command="/admin/tenant-management">
                  <span>租户管理</span>
                </el-dropdown-item>
                <el-dropdown-item v-if="hasAnyAuthority('ROLE_ADMIN') && authenticated" command="/admin/role-management">
                  <span>角色管理</span>
                </el-dropdown-item>
                <el-dropdown-item v-if="hasAnyAuthority('ROLE_ADMIN') && authenticated" command="/admin/role-data-scope">
                  <span>数据权限</span>
                </el-dropdown-item>
                <el-dropdown-item v-if="hasAnyAuthority(['ROLE_ADMIN', 'ROLE_TENANT_ADMIN']) && authenticated" command="/service-config">
                  <font-awesome-icon icon="cog" />
                  <span>服务配置</span>
                </el-dropdown-item>
                <el-dropdown-item v-if="authenticated" data-cy="logout" id="logout" @click="logout()">
                  <font-awesome-icon icon="sign-out-alt" />
                  <span>退出</span>
                </el-dropdown-item>
                <el-dropdown-item v-if="!authenticated" data-cy="login" id="login" @click="showLogin()">
                  <font-awesome-icon icon="sign-in-alt" />
                  <span>登录</span>
                </el-dropdown-item>
                <el-dropdown-item v-if="!authenticated" command="/register" id="register">
                  <font-awesome-icon icon="user-plus" />
                  <span>注册</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </li>
      </ul>
    </div>
  </nav>
</template>

<script lang="ts" src="./jhi-navbar.component.ts"></script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
/* ==========================================================================
    Navbar
    ========================================================================== */
.navbar-version {
  font-size: 0.65em;
  color: #ccc;
}

.jh-navbar {
  background: linear-gradient(135deg, #0f1d45 0%, #16265c 100%);
  padding: 0.2em 1em;
}

.jh-navbar .profile-image {
  margin: -10px 0;
  height: 40px;
  width: 40px;
  border-radius: 50%;
}

.jh-navbar .dropdown-toggle::after {
  margin-left: 0.15em;
}

.jh-navbar ul.navbar-nav {
  padding: 0.5em;
}

.jh-navbar .navbar-nav .nav-item {
  margin-left: 1.5rem;
}

.jh-navbar a.nav-link,
.jh-navbar .no-bold {
  font-weight: 400;
}

.jh-navbar a.nav-link {
  color: rgba(255, 255, 255, 0.65);
  display: block;
}

/* Element Plus dropdown wrapper resets font-size/line-height to 14px/1.
   Normalize it so dropdown toggles match plain nav links. */
.jh-navbar :deep(.el-dropdown) {
  display: block;
  font-size: inherit;
  line-height: inherit;
  color: inherit;
  vertical-align: baseline;
}

.jh-navbar a.nav-link:hover,
.jh-navbar a.nav-link:focus {
  color: #fff;
}

.jh-navbar .jh-navbar-toggler {
  color: #ccc;
  font-size: 1.5em;
  padding: 10px;
}

.jh-navbar .jh-navbar-toggler:hover {
  color: #fff;
}

@media screen and (min-width: 768px) {
  .jh-navbar-toggler {
    display: none;
  }
}

@media screen and (min-width: 768px) and (max-width: 1150px) {
  span span {
    display: none;
  }
}

.navbar-title {
  display: inline-block;
  color: white;
}

/* ==========================================================================
    Logo styles
    ========================================================================== */
.navbar-brand.logo {
  padding: 0 7px;
}

.logo-y {
    color: #8ec5ff;
}
</style>
