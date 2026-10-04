<template>
  <div>
    <h2 id="page-heading" data-cy="AppPackageDetailsHeading" class="d-flex align-items-center justify-content-between flex-wrap-nowrap">
      <span>资产包详情</span>
      <div class="d-flex align-items-center">
        <el-button class="mr-2" @click="back">
          <font-awesome-icon icon="arrow-left"></font-awesome-icon>
          <span>返回</span>
        </el-button>
        <el-button class="mr-2" @click="download">
          <font-awesome-icon icon="download"></font-awesome-icon>
          <span>下载 JSON</span>
        </el-button>
        <el-button type="primary" @click="initVisible = true">
          <font-awesome-icon icon="play"></font-awesome-icon>
          <span>初始化数据应用</span>
        </el-button>
      </div>
    </h2>
    <br />

    <div v-if="appPackage">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="名称">{{ appPackage.name }}</el-descriptions-item>
        <el-descriptions-item label="编码">{{ appPackage.code }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ appPackage.version || '-' }}</el-descriptions-item>
        <el-descriptions-item label="业务场景">{{ appPackage.category || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ appPackage.packageType === 'SYSTEM' ? '系统预制' : '本租户导出' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateShort(appPackage.createTime) || '' }}</el-descriptions-item>
        <el-descriptions-item label="说明" :span="3">{{ appPackage.description || '-' }}</el-descriptions-item>
      </el-descriptions>

      <h5 class="mt-4">资产构成</h5>
      <div>
        <el-tag v-for="entry in summary" :key="entry.label" class="mr-2 mb-1" type="info">{{ entry.label }}：{{ entry.value }}</el-tag>
      </div>

      <el-tabs class="mt-3">
        <el-tab-pane :label="`数据源（${content?.dataSources?.length || 0}）`">
          <el-table :data="content?.dataSources || []" size="small" border>
            <el-table-column prop="name" label="名称" min-width="180" />
            <el-table-column prop="type" label="类型" width="120" />
            <el-table-column prop="url" label="连接地址" min-width="260" />
            <el-table-column prop="schemaName" label="Schema" width="120" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`数据模型（${content?.models?.length || 0}）`">
          <el-table :data="content?.models || []" size="small" border>
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="code" label="编码" min-width="150" />
            <el-table-column prop="modelType" label="模型类型" width="120" />
            <el-table-column label="字段数" width="90" align="center">
              <template #default="scope">{{ (scope.row.fields || []).length }}</template>
            </el-table-column>
            <el-table-column label="物理表" min-width="180">
              <template #default="scope"
                >{{ scope.row.schemaName ? `${scope.row.schemaName}.` : '' }}{{ scope.row.tableName || '-' }}</template
              >
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`指标（${content?.metrics?.length || 0}）`">
          <el-table :data="content?.metrics || []" size="small" border>
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="code" label="编码" min-width="150" />
            <el-table-column prop="metricType" label="类型" width="110" />
            <el-table-column prop="unit" label="单位" width="90" />
            <el-table-column prop="formula" label="公式" min-width="240" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`ETL 任务（${content?.etlTasks?.length || 0}）`">
          <el-table :data="content?.etlTasks || []" size="small" border>
            <el-table-column prop="taskName" label="任务名称" min-width="160" />
            <el-table-column prop="taskCode" label="编码" min-width="150" />
            <el-table-column prop="cron" label="调度" min-width="140" />
            <el-table-column label="节点 / 连线" width="120" align="center">
              <template #default="scope">{{ (scope.row.nodes || []).length }} / {{ (scope.row.edges || []).length }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`SQL 任务（${content?.sqlJobs?.length || 0}）`">
          <el-table :data="content?.sqlJobs || []" size="small" border>
            <el-table-column prop="jobName" label="任务名称" min-width="180" />
            <el-table-column prop="cron" label="调度" min-width="160" />
            <el-table-column label="绑定数据源" width="130" align="center">
              <template #default="scope">{{ scope.row.jobContext?.dataSourceId ?? '-' }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`编排任务（${content?.dagJobs?.length || 0}）`">
          <el-table :data="content?.dagJobs || []" size="small" border>
            <el-table-column prop="jobName" label="编排名称" min-width="180" />
            <el-table-column prop="cron" label="调度" min-width="160" />
            <el-table-column label="子任务数" width="110" align="center">
              <template #default="scope">{{ (scope.row.jobContext?.jobs || []).length }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <app-package-init v-model="initVisible" :pkg="appPackage" @initialized="onInitialized" />
  </div>
</template>

<script lang="ts" src="./app-package-details.component.ts"></script>
