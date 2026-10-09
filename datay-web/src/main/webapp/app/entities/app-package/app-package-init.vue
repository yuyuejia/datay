<template>
  <app-modal
    :model-value="modelValue"
    size="xl"
    :ok-only="step === 'done'"
    :ok-title="step === 'done' ? '完成' : ''"
    close-on-backdrop="false"
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
  >
    <template #modal-title>
      <span>初始化数据应用</span>
    </template>

    <div v-if="step === 'form'">
      <el-alert type="info" :closable="false" class="mb-3">
        <template #title>
          <strong>{{ displayName }}</strong>
          <span class="text-muted ml-2">（{{ displayCode }}）</span>
        </template>
        <div class="mt-1">{{ displayDescription || '该资产包未填写说明' }}</div>
      </el-alert>

      <h6>将安装的资产</h6>
      <div class="asset-summary mb-3">
        <el-tag v-for="entry in summary" :key="entry.label" class="mr-2 mb-1" type="info"> {{ entry.label }}：{{ entry.value }} </el-tag>
        <span v-if="summary.length === 0" class="text-muted">资产包内容为空</span>
      </div>

      <h6>冲突处理</h6>
      <el-radio-group v-model="conflictStrategy" class="mb-3">
        <el-radio value="OVERWRITE">覆盖更新已存在的同编码 / 同名资产（推荐）</el-radio>
        <el-radio value="RENAME">编码冲突时自动重命名</el-radio>
        <el-radio value="SKIP">编码冲突时跳过该资产</el-radio>
      </el-radio-group>
      <div v-if="conflictStrategy === 'OVERWRITE'" class="text-muted small mb-3">
        用于资产包升级后的覆盖安装：同编码的数据模型 / 指标 / ETL 任务与同名的任务会就地更新并保留原有 ID，原本在线的任务覆盖后继续保持在线。
      </div>

      <div class="mb-3">
        <el-checkbox v-model="onlineJobs">初始化后直接把任务置为「上线」并加入调度</el-checkbox>
        <div class="text-muted small">默认只导入为离线状态，避免误触发调度。</div>
      </div>

      <h6>数据源绑定</h6>
      <div class="text-muted small mb-2">
        默认按「名称 + 类型 + 连接地址」匹配当前租户已有数据源，命中则直接复用，未命中才新建。也可以在下面手工指定绑定到某个已有数据源。
      </div>
      <el-table :data="dataSources" size="small" border style="width: 100%">
        <el-table-column label="资产包内数据源" min-width="200">
          <template #default="scope">
            <div>{{ scope.row.name || '未命名数据源' }}</div>
            <div class="text-muted small">{{ scope.row.type }} · {{ scope.row.url }}</div>
          </template>
        </el-table-column>
        <el-table-column label="目标租户数据源" min-width="220">
          <template #default="scope">
            <el-select
              v-model="dataSourceMapping[String(scope.row.oldId)]"
              placeholder="自动匹配 / 新建"
              clearable
              filterable
              style="width: 100%"
            >
              <el-option v-for="item in dataSourceOptions" :key="item.id" :label="item.label" :value="item.id" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>

      <el-alert v-if="errorMessage" type="error" :closable="false" class="mt-3" :title="errorMessage" />
    </div>

    <div v-else>
      <el-alert type="success" :closable="false" :title="result?.message || '初始化完成'" class="mb-3" />

      <el-alert v-if="(result?.nextSteps || []).length > 0" type="warning" :closable="false" class="mb-3">
        <template #title>下一步</template>
        <ul class="mb-0 pl-3">
          <li v-for="(step, index) in result.nextSteps" :key="index">{{ step }}</li>
        </ul>
      </el-alert>

      <h6>创建 / 复用统计</h6>
      <div class="asset-summary mb-3">
        <el-tag v-for="(value, key) in result?.counts || {}" :key="key" class="mr-2 mb-1" type="success">
          {{ countLabel(String(key)) }}：{{ value }}
        </el-tag>
      </div>

      <h6>数据源</h6>
      <el-table :data="result?.dataSources || []" size="small" border style="width: 100%">
        <el-table-column prop="name" label="资产包内数据源" min-width="180" />
        <el-table-column label="处理方式" width="120" align="center">
          <template #default="scope">
            <el-tag size="small" :type="scope.row.action === 'CREATED' ? 'success' : 'warning'">
              {{ actionLabel(scope.row.action) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="映射到 ID" width="120" align="center">
          <template #default="scope">{{ scope.row.oldId }} → {{ scope.row.newId }}</template>
        </el-table-column>
      </el-table>

      <template v-if="(result?.warnings || []).length > 0">
        <h6 class="mt-3">提示</h6>
        <ul class="text-muted small mb-0">
          <li v-for="(warning, index) in result.warnings" :key="index">{{ warning }}</li>
        </ul>
      </template>
    </div>

    <template #modal-footer>
      <div v-if="step === 'form'">
        <el-button @click="close">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="summary.length === 0" @click="submit">开始初始化</el-button>
      </div>
      <div v-else>
        <el-button type="primary" @click="close">完成</el-button>
      </div>
    </template>
  </app-modal>
</template>

<script lang="ts" src="./app-package-init.component.ts"></script>
