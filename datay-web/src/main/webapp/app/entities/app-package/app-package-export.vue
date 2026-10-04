<template>
  <div>
    <h2 id="page-heading" data-cy="AppPackageExportHeading">
      <span>导出数据应用资产包</span>
    </h2>
    <br />

    <el-alert type="info" :closable="false" class="mb-3">
      <template #title>把已经开发完成的数据资产打包成一个可移植的资产包</template>
      <div class="mt-1 small">
        勾选要打包的数据源、数据模型、指标、ETL 任务、SQL 任务与编排任务；开启「自动补齐被引用资产」后，
        指标引用的事实表、模型引用的维度与数据源、编排引用的子任务都会自动一并打包，避免导出半截资产包。 资产包内部使用逻辑 ID
        引用，导入到其它租户时会自动完成 ID 替换。
      </div>
    </el-alert>

    <el-form label-width="120px" label-position="right" style="max-width: 900px">
      <el-form-item label="资产包名称" required>
        <el-input v-model="form.name" placeholder="如：电商销售分析应用" maxlength="100" />
      </el-form-item>
      <el-form-item label="资产包编码">
        <el-input v-model="form.code" placeholder="留空自动按名称生成，如 ecommerce_sales_app" maxlength="100" />
      </el-form-item>
      <el-form-item label="说明">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          placeholder="这个数据应用解决什么问题、包含哪些资产"
          maxlength="500"
        />
      </el-form-item>
      <el-form-item label="业务场景">
        <el-select
          v-model="form.category"
          filterable
          allow-create
          default-first-option
          clearable
          placeholder="选择或输入业务场景分类"
          style="width: 320px"
        >
          <el-option v-for="item in options.categories" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="版本">
        <el-input v-model="form.version" style="width: 160px" />
      </el-form-item>

      <el-divider content-position="left">选择要打包的资产</el-divider>

      <el-form-item :label="`数据源（${form.dataSourceIds.length}）`">
        <el-select
          v-model="form.dataSourceIds"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          placeholder="选择数据源"
          style="width: 100%"
        >
          <el-option v-for="item in options.dataSources" :key="item.id" :label="`${item.name}（${item.subtitle}）`" :value="item.id" />
        </el-select>
      </el-form-item>

      <el-form-item :label="`数据模型（${form.modelIds.length}）`">
        <el-select
          v-model="form.modelIds"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          placeholder="选择数据模型"
          style="width: 100%"
        >
          <el-option
            v-for="item in options.models"
            :key="item.id"
            :label="`${item.name} / ${item.code}（${item.subtitle}）`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item :label="`指标（${form.metricIds.length}）`">
        <el-select
          v-model="form.metricIds"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          placeholder="选择指标"
          style="width: 100%"
        >
          <el-option
            v-for="item in options.metrics"
            :key="item.id"
            :label="`${item.name} / ${item.code}（${item.subtitle}）`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item :label="`ETL 任务（${form.etlTaskIds.length}）`">
        <el-select
          v-model="form.etlTaskIds"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          placeholder="选择 ETL 任务"
          style="width: 100%"
        >
          <el-option v-for="item in options.etlTasks" :key="item.id" :label="`${item.name} / ${item.code}`" :value="item.id" />
        </el-select>
      </el-form-item>

      <el-form-item :label="`SQL 任务（${form.sqlJobIds.length}）`">
        <el-select
          v-model="form.sqlJobIds"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          placeholder="选择 SQL 任务"
          style="width: 100%"
        >
          <el-option
            v-for="item in options.sqlJobs"
            :key="item.id"
            :label="`${item.name}（${item.subtitle || 'OFFLINE'}）`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item :label="`编排任务（${form.dagJobIds.length}）`">
        <el-select
          v-model="form.dagJobIds"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          placeholder="选择编排任务"
          style="width: 100%"
        >
          <el-option
            v-for="item in options.dagJobs"
            :key="item.id"
            :label="`${item.name}（${item.subtitle || 'OFFLINE'}）`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>

      <el-divider content-position="left">选项</el-divider>

      <el-form-item label="包含引用">
        <el-switch v-model="form.includeReferences" />
        <span class="text-muted small ml-2">自动补齐被引用的数据源、维度模型、事实表与子任务</span>
      </el-form-item>
      <el-form-item label="保存到市场">
        <el-switch v-model="form.saveToMarket" />
        <span class="text-muted small ml-2">保存后可在「数据服务应用市场」中直接下载与复用</span>
      </el-form-item>
      <el-form-item label="覆盖同名包" v-if="form.saveToMarket">
        <el-switch v-model="form.overwrite" />
        <span class="text-muted small ml-2">关闭时会生成一个新的编码，不会覆盖已有资产包</span>
      </el-form-item>

      <el-form-item>
        <el-button @click="cancel">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">生成资产包</el-button>
      </el-form-item>
    </el-form>

    <app-modal v-model="resultVisible" size="lg" ok-only ok-title="关闭">
      <template #modal-title>
        <span>资产包已生成</span>
      </template>
      <div v-if="result">
        <el-alert type="success" :closable="false" class="mb-3" :title="`${result.name}（${result.code}）`" />
        <h6>资产构成</h6>
        <div class="mb-3">
          <el-tag v-for="entry in resultSummary" :key="entry.label" class="mr-2 mb-1" type="info">
            {{ entry.label }}：{{ entry.value }}
          </el-tag>
        </div>
        <p class="text-muted small mb-0">资产包已保存到「数据服务应用市场」，可以直接下载分发给其它租户，或直接在目标租户中初始化。</p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="resultVisible = false">关闭</el-button>
          <el-button type="primary" @click="goToMarket">返回应用市场</el-button>
        </div>
      </template>
    </app-modal>
  </div>
</template>

<script lang="ts" src="./app-package-export.component.ts"></script>
