<template>
  <div class="settings-page">
    <h2 id="page-heading" class="settings-heading">
      <span id="service-config-heading">服务配置</span>
      <router-link :to="{ name: 'ServiceConfigAdvanced' }" custom v-slot="{ navigate }">
        <el-button plain @click="navigate" data-cy="advancedConfigButton">
          <font-awesome-icon icon="cog" class="mr-1"></font-awesome-icon>
          <span>高级配置</span>
        </el-button>
      </router-link>
    </h2>

    <el-alert type="info" :closable="false" show-icon class="tenant-alert">
      <template #default>
        当前租户：<strong>{{ tenantLabel }}</strong
        >，以下配置仅对当前租户生效。
      </template>
    </el-alert>

    <div class="settings-body">
      <el-menu :default-active="activeKey" class="settings-menu" @select="activeKey = $event">
        <el-menu-item v-for="group in groups" :key="group.key" :index="group.key">
          <span>{{ group.label }}</span>
        </el-menu-item>
      </el-menu>

      <div v-loading="isLoading" class="settings-content">
        <div class="group-header">
          <h3>{{ activeGroup.label }}</h3>
          <p>{{ activeGroup.description }}</p>
        </div>

        <el-form label-position="top" class="settings-form">
          <el-form-item v-for="field in visibleFields" :key="field.key" :label="field.label">
            <el-select v-if="field.type === 'select'" v-model="values[field.key]" placeholder="请选择" style="width: 320px">
              <el-option v-for="opt in field.options" :key="opt.value" :label="opt.label" :value="opt.value"></el-option>
            </el-select>
            <el-select
              v-else-if="field.type === 'datasource'"
              v-model="values[field.key]"
              placeholder="请选择数据源"
              filterable
              clearable
              style="width: 420px"
            >
              <el-option v-for="ds in dataSourceOptions" :key="ds.id" :label="`${ds.name} (${ds.type})`" :value="ds.id"></el-option>
            </el-select>
            <el-input
              v-else-if="field.type === 'password'"
              v-model="values[field.key]"
              type="password"
              show-password
              :placeholder="field.placeholder"
              style="width: 420px"
            ></el-input>
            <el-input-number
              v-else-if="field.type === 'number'"
              v-model="values[field.key]"
              :min="field.min"
              :max="field.max"
              :step="field.step"
              :precision="field.precision"
              style="width: 220px"
            ></el-input-number>
            <el-switch v-else-if="field.type === 'switch'" v-model="values[field.key]"></el-switch>
            <el-input v-else v-model="values[field.key]" :placeholder="field.placeholder" style="width: 420px"></el-input>
            <div v-if="field.hint" class="field-hint">{{ field.hint }}</div>
          </el-form-item>
        </el-form>

        <div class="settings-actions">
          <el-button type="primary" :loading="isSaving" @click="saveGroup" data-cy="saveConfigButton">保存</el-button>
          <el-button v-if="activeGroup.test" :loading="isTesting" @click="testGroup" data-cy="testConfigButton"> 测试连接 </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./service-config-settings.component.ts"></script>

<style scoped>
.settings-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tenant-alert {
  margin-bottom: 16px;
}

.settings-body {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.settings-menu {
  width: 200px;
  flex-shrink: 0;
  border-right: 1px solid #ebeef5;
}

.settings-content {
  flex: 1;
  min-width: 0;
  min-height: 260px;
}

.group-header h3 {
  margin: 0 0 4px;
}

.group-header p {
  margin: 0 0 20px;
  color: #909399;
  font-size: 13px;
}

.settings-form {
  max-width: 560px;
}

.settings-actions {
  margin-top: 8px;
}

.field-hint {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
}
</style>
