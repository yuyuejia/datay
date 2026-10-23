<template>
  <div class="designer-page" v-loading="loading">
    <div class="designer-header">
      <div class="designer-header-left">
        <el-button text @click="goBack">← 返回</el-button>
        <el-input v-model="name" class="designer-name-input" placeholder="未命名看板" maxlength="120" />
        <el-tag v-if="editingId" type="info" size="small">编辑中</el-tag>
        <el-tag v-if="aiMode" type="warning" size="small">AI 设计</el-tag>
      </div>
      <div class="designer-header-actions">
        <el-button-group class="designer-history-actions">
          <el-button :disabled="!canUndo || saving || loading" title="撤销（Ctrl/Cmd+Z）" @click="undo">↶ 撤销</el-button>
          <el-button :disabled="!canRedo || saving || loading" title="重做（Ctrl/Cmd+Shift+Z）" @click="redo">↷ 重做</el-button>
        </el-button-group>
        <el-button :disabled="loading || saving" title="在新页签预览当前设计，无需先保存" @click="openPreview">
          <font-awesome-icon icon="eye" class="mr-1"></font-awesome-icon>
          预览
        </el-button>
        <el-button :disabled="loading || saving" @click="openAiAssistant">
          <font-awesome-icon icon="wand-magic-sparkles" class="mr-1"></font-awesome-icon>
          AI 助手
        </el-button>
        <el-button type="primary" :loading="saving" @click="save">保存看板</el-button>
      </div>
    </div>

    <div class="designer-body">
      <aside class="designer-sidebar">
        <el-tabs v-model="activePanel" stretch class="designer-sidebar-tabs">
          <el-tab-pane name="datasets">
            <template #label>
              <span class="designer-tab-label"><font-awesome-icon icon="database" />数据集</span>
            </template>
            <div class="designer-sidebar-section">
              <div class="designer-add-row">
                <el-select v-model="datasetToAdd" filterable placeholder="从数据集库选择" style="flex: 1">
                  <el-option
                    v-for="dataset in availableDatasets"
                    :key="dataset.id"
                    :label="`${dataset.name}${dataset.type === 'SQL' ? '（SQL）' : '（指标）'}`"
                    :value="dataset.id"
                  />
                </el-select>
                <el-button type="primary" :disabled="!datasetToAdd" @click="addDataset">添加</el-button>
              </div>
              <div class="designer-hint">数据集可被多个看板复用，修改数据集后所有看板自动生效。</div>
              <div v-if="!spec.datasets.length" class="designer-empty-hint">尚未添加数据集</div>
              <div v-for="dataset in spec.datasets" :key="dataset.id" class="designer-list-item">
                <div class="designer-list-main">
                  <div class="designer-list-title">
                    {{ dataset.name || dataset.id }}
                    <el-tag size="small" :type="dataset.type === 'SQL' ? 'warning' : 'primary'">
                      {{ dataset.type === 'SQL' ? 'SQL' : '指标' }}
                    </el-tag>
                    <el-tag v-if="!dataset.refId" size="small" type="info">内嵌</el-tag>
                  </div>
                  <div class="designer-list-sub">
                    别名 {{ dataset.id }}<span v-if="dataset.refId"> · 引用 {{ dataset.refId }}</span>
                  </div>
                </div>
                <el-button text size="small" type="danger" @click="removeDataset(dataset)">移除</el-button>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane name="widgets">
            <template #label>
              <span class="designer-tab-label"><font-awesome-icon icon="cubes" />组件</span>
            </template>
            <div class="designer-sidebar-section">
              <div class="designer-palette-tabs" role="tablist">
                <button
                  v-for="category in paletteCategories"
                  :key="category.key"
                  type="button"
                  class="designer-palette-tab"
                  :class="{ 'is-active': paletteCategory === category.key }"
                  @click="paletteCategory = category.key"
                >
                  <WidgetPaletteIcon :name="category.icon" class="designer-palette-tab-icon" />
                  <span>{{ category.label }}</span>
                </button>
              </div>

              <div class="designer-palette-grid">
                <div
                  v-for="item in activePaletteItems"
                  :key="item.key"
                  class="designer-palette-card"
                  :class="{ 'is-dragging': paletteDragItem?.key === item.key }"
                  draggable="true"
                  :title="`拖拽到画布添加${item.label}，或点击快速添加`"
                  @dragstart="onPaletteDragStart($event, item)"
                  @dragend="onPaletteDragEnd"
                  @click="addPaletteWidget(item)"
                >
                  <WidgetPaletteIcon :name="item.icon" class="designer-palette-card-icon" />
                  <span class="designer-palette-card-label">{{ item.label }}</span>
                  <span class="designer-palette-card-hint">拖拽添加</span>
                </div>
              </div>
              <div class="designer-hint">拖拽到画布指定位置，或点击卡片快速添加。</div>

              <div class="designer-widget-list-title">已添加组件</div>
              <div v-if="!spec.widgets.length" class="designer-empty-hint">尚未添加组件</div>
              <div
                v-for="widget in spec.widgets"
                :key="widget.id"
                class="designer-list-item designer-widget-item"
                :class="{ 'is-active': widget.id === selectedWidgetId }"
                @click="selectWidget(widget.id)"
              >
                <div class="designer-list-main">
                  <div class="designer-list-title">{{ widget.title || widget.id }}</div>
                  <div class="designer-list-sub">{{ widgetTypeLabel(widget.type) }} · {{ widget.datasetId || '未绑定数据集' }}</div>
                </div>
                <el-button text size="small" type="danger" @click.stop="removeWidget(widget.id)">删除</el-button>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane name="filters">
            <template #label>
              <span class="designer-tab-label"><font-awesome-icon icon="list" />筛选器</span>
            </template>
            <div class="designer-sidebar-section">
              <el-button size="small" type="primary" plain @click="addFilter">+ 新增全局筛选器</el-button>
              <div class="designer-hint">筛选器直接选择数据集和字段，作用于相关数据。</div>
              <div v-if="!(spec.filters && spec.filters.length)" class="designer-empty-hint">尚未添加筛选器</div>
              <div
                v-for="filter in spec.filters || []"
                :key="filter.id"
                class="designer-list-item designer-widget-item"
                :class="{ 'is-active': filter.id === selectedFilterId }"
                @click="selectFilter(filter.id)"
              >
                <div class="designer-list-main">
                  <div class="designer-list-title">{{ filter.label || filter.id }}</div>
                  <div class="designer-list-sub">{{ filter.type }} · {{ filterTargetLabel(filter) }}</div>
                </div>
                <el-button text size="small" type="danger" @click.stop="removeFilter(filter.id)">删除</el-button>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </aside>

      <main class="designer-main">
        <div class="designer-main-toolbar">
          <el-input v-model="description" placeholder="看板描述（可选）" class="designer-desc-input" />
          <div class="designer-main-meta">
            <span v-if="loadingData">数据加载中…</span>
            <el-button size="small" title="查看或编辑当前看板定义" @click="openSpecJson">JSON</el-button>
            <el-button size="small" @click="refreshData">刷新预览数据</el-button>
          </div>
        </div>
        <section class="designer-preview-filters">
          <div class="designer-preview-filters-header">
            <div class="designer-preview-filters-heading">
              <span class="designer-preview-filters-title">筛选器</span>
              <span class="designer-preview-filters-count">{{ (spec.filters || []).length }} 个</span>
            </div>
            <el-button text type="primary" size="small" @click="addFilter">+ 新增筛选器</el-button>
          </div>
          <DashboardFilters
            v-if="(spec.filters || []).length"
            :filters="spec.filters || []"
            :model-value="filterValues"
            :spec="spec"
            :source-data="rawDatasetData"
            @update:model-value="onFiltersChange"
          />
          <div v-else class="designer-preview-filters-empty">
            <span>暂无筛选器，添加后将在此处显示并实时作用于预览。</span>
            <el-button text type="primary" size="small" @click="openFiltersPanel">去配置</el-button>
          </div>
        </section>
        <div class="designer-tip">
          从左侧组件库拖拽图表、指标卡或表格到画布；拖动组件标题栏移动位置，拖动右下角调整大小。筛选结果会实时作用到相关组件。支持
          Ctrl/Cmd+Z 撤销、Ctrl/Cmd+Shift+Z 重做。点击「预览」可在新页签查看当前设计，无需先保存。
        </div>
        <DesignerCanvas
          :spec="spec"
          :dataset-data="datasetData"
          :loading-datasets="loadingDatasets"
          :dataset-errors="datasetErrors"
          :selected-id="selectedWidgetId"
          :drag-item="paletteDragItem"
          @select="selectWidget"
          @layout-change="onLayoutChange"
          @resize="onResize"
          @delete="removeWidget"
          @add-widget="onCanvasAddWidget"
        />
      </main>

      <aside class="designer-properties">
        <template v-if="selectedWidget">
          <div class="designer-property-title">组件配置</div>
          <el-tabs v-model="propertyTab" class="designer-property-tabs">
            <el-tab-pane label="基础设置" name="basic">
              <div class="designer-property-form">
                <label>标题</label>
                <el-input v-model="selectedWidget.title" placeholder="组件标题" />

                <template v-if="selectedWidget.type !== 'html'">
                  <label>数据集</label>
                  <el-select v-model="selectedWidget.datasetId" clearable placeholder="选择数据集">
                    <el-option v-for="dataset in spec.datasets" :key="dataset.id" :label="dataset.name || dataset.id" :value="dataset.id" />
                  </el-select>
                </template>

                <template v-if="selectedWidget.type === 'echarts'">
                  <label>类型</label>
                  <el-select :model-value="chartType" @update:model-value="setChartType">
                    <el-option value="bar" label="柱状图" />
                    <el-option value="line" label="折线图" />
                    <el-option value="pie" label="饼图" />
                    <el-option value="area" label="面积图" />
                  </el-select>

                  <template v-if="selectedWidget.dataRef">
                    <template v-if="chartType === 'pie'">
                      <label>分类字段</label>
                      <el-select
                        v-model="selectedWidget.dataRef.categoryField"
                        clearable
                        filterable
                        allow-create
                        placeholder="选择或输入分类字段"
                      >
                        <el-option v-for="field in widgetFields" :key="field.name" :label="field.label || field.name" :value="field.name" />
                      </el-select>

                      <label>数值字段</label>
                      <el-select
                        v-model="selectedWidget.dataRef.valueField"
                        clearable
                        filterable
                        allow-create
                        placeholder="选择或输入数值字段"
                      >
                        <el-option
                          v-for="field in widgetValueFieldOptions"
                          :key="field.name"
                          :label="field.label || field.name"
                          :value="field.name"
                        />
                      </el-select>
                      <div class="designer-hint">饼图按分类字段分区，数值字段决定扇区大小；不绘制坐标轴。</div>
                    </template>

                    <template v-else>
                      <label>横轴字段（X 轴）</label>
                      <el-select
                        v-model="selectedWidget.dataRef.categoryField"
                        clearable
                        filterable
                        allow-create
                        placeholder="选择分类字段"
                      >
                        <el-option v-for="field in widgetFields" :key="field.name" :label="field.label || field.name" :value="field.name" />
                      </el-select>
                      <div class="designer-hint">ECharts category 轴使用单个分类字段。</div>

                      <label>纵轴字段（Y 轴）</label>
                      <el-select
                        v-model="widgetValueFields"
                        multiple
                        collapse-tags
                        collapse-tags-tooltip
                        filterable
                        allow-create
                        placeholder="可选择一个或多个数值字段"
                      >
                        <el-option
                          v-for="field in widgetValueFieldOptions"
                          :key="field.name"
                          :label="field.label || field.name"
                          :value="field.name"
                        />
                      </el-select>
                      <div class="designer-hint">支持多个数值字段；多个字段会自动生成多条系列。</div>

                      <template v-if="widgetValueFields.length <= 1">
                        <label>系列拆分字段（可选）</label>
                        <el-select
                          v-model="selectedWidget.dataRef.seriesField"
                          clearable
                          filterable
                          allow-create
                          placeholder="按字段拆分为多系列"
                        >
                          <el-option
                            v-for="field in widgetFields"
                            :key="field.name"
                            :label="field.label || field.name"
                            :value="field.name"
                          />
                        </el-select>
                      </template>
                    </template>

                    <label>排序字段 / 方向</label>
                    <div class="designer-inline">
                      <el-select
                        v-model="selectedWidget.dataRef.sortField"
                        clearable
                        filterable
                        allow-create
                        placeholder="不排序"
                        style="flex: 1"
                      >
                        <el-option v-for="field in widgetFields" :key="field.name" :label="field.label || field.name" :value="field.name" />
                      </el-select>
                      <el-select v-model="selectedWidget.dataRef.sortOrder" style="width: 90px">
                        <el-option value="asc" label="升序" />
                        <el-option value="desc" label="降序" />
                      </el-select>
                    </div>

                    <label>返回行数上限</label>
                    <el-input-number v-model="selectedWidget.dataRef.limit" :min="1" :max="10000" controls-position="right" />
                  </template>
                </template>

                <template v-else-if="selectedWidget.type === 'table'">
                  <template v-if="selectedWidget.dataRef">
                    <label>排序字段 / 方向</label>
                    <div class="designer-inline">
                      <el-select
                        v-model="selectedWidget.dataRef.sortField"
                        clearable
                        filterable
                        allow-create
                        placeholder="不排序"
                        style="flex: 1"
                      >
                        <el-option v-for="field in widgetFields" :key="field.name" :label="field.label || field.name" :value="field.name" />
                      </el-select>
                      <el-select v-model="selectedWidget.dataRef.sortOrder" style="width: 90px">
                        <el-option value="asc" label="升序" />
                        <el-option value="desc" label="降序" />
                      </el-select>
                    </div>

                    <label>返回行数上限</label>
                    <el-input-number v-model="selectedWidget.dataRef.limit" :min="1" :max="10000" controls-position="right" />
                  </template>
                  <div class="designer-hint">明细表展示数据集全部列，可配置排序和返回行数上限。</div>
                </template>

                <template v-else-if="selectedWidget.type === 'kpi'">
                  <template v-if="selectedWidget.dataRef">
                    <label>数值字段</label>
                    <el-select
                      v-model="selectedWidget.dataRef.valueField"
                      clearable
                      filterable
                      allow-create
                      placeholder="选择或输入数值字段"
                    >
                      <el-option
                        v-for="field in widgetValueFieldOptions"
                        :key="field.name"
                        :label="field.label || field.name"
                        :value="field.name"
                      />
                    </el-select>
                  </template>
                  <div class="designer-hint">指标卡展示所选数值字段的汇总值；留空时自动取首个数值列。</div>
                </template>

                <template v-else-if="selectedWidget.type === 'html'">
                  <label>HTML 内容</label>
                  <el-input v-model="selectedWidget.html" type="textarea" :rows="10" spellcheck="false" />
                  <div class="designer-hint">说明文本不绑定数据集，内容会经过安全过滤后展示。</div>
                </template>
              </div>
            </el-tab-pane>

            <el-tab-pane label="自定义设置" name="custom">
              <div class="designer-property-form">
                <template v-if="selectedWidget.type === 'echarts'">
                  <div class="designer-hint">
                    直接编辑该组件的 ECharts option，可配置 tooltip、legend、grid、坐标轴样式和系列样式。字段映射与数据仍由基础设置控制。
                  </div>
                  <el-alert v-if="customOptionError" type="error" :title="customOptionError" :closable="false" class="mb-2" />
                  <textarea v-model="customOptionText" class="designer-json designer-json--compact" spellcheck="false"></textarea>
                  <div class="designer-inline">
                    <el-button type="primary" @click="applyCustomOption">应用配置</el-button>
                    <el-button @click="resetCustomOption">恢复默认</el-button>
                  </div>
                </template>
                <div v-else class="designer-hint">当前组件没有 ECharts 配置项。</div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </template>

        <template v-else-if="selectedFilter">
          <div class="designer-property-title">筛选器配置</div>
          <div class="designer-property-form">
            <label>名称</label>
            <el-input v-model="selectedFilter.label" placeholder="筛选器名称" />

            <label>类型</label>
            <el-select v-model="selectedFilter.type" @change="onFilterTypeChange">
              <el-option value="select" label="单选" />
              <el-option value="multiSelect" label="多选" />
              <el-option value="input" label="文本输入" />
              <el-option value="dateRange" label="日期范围" />
              <el-option value="numberRange" label="数值范围" />
            </el-select>

            <label>数据集</label>
            <el-select v-model="selectedFilter.datasetId" clearable placeholder="选择数据集" @change="onFilterDatasetChange">
              <el-option v-for="dataset in spec.datasets" :key="dataset.id" :label="dataset.name || dataset.id" :value="dataset.id" />
            </el-select>

            <label>字段</label>
            <el-select
              v-model="selectedFilter.field"
              clearable
              filterable
              allow-create
              :disabled="!selectedFilter.datasetId"
              placeholder="选择或输入字段名"
              @change="onFilterFieldChange"
            >
              <el-option v-for="field in selectedFilterFields" :key="field.name" :label="field.label || field.name" :value="field.name" />
            </el-select>

            <div v-if="selectedFilter.type === 'select' || selectedFilter.type === 'multiSelect'" class="designer-hint">
              {{
                selectedFilter.datasetId
                  ? selectedFilter.field
                    ? '下拉选项将从所选数据集字段的去重值中加载。'
                    : '请选择字段以加载下拉选项。'
                  : '未选择数据集字段时，可使用下方静态选项。'
              }}
            </div>

            <template v-if="(selectedFilter.type === 'select' || selectedFilter.type === 'multiSelect') && !selectedFilter.datasetId">
              <label>静态选项（逗号分隔）</label>
              <el-input v-model="filterOptionsText" type="textarea" :rows="3" placeholder="华东,华南,华北" />
            </template>

            <label>默认值（多值用逗号分隔）</label>
            <el-input v-model="filterDefaultText" placeholder="可选" />

            <div class="designer-hint">筛选值会直接作用于所选数据集字段。</div>
          </div>
        </template>

        <div v-else class="designer-property-empty">
          <font-awesome-icon icon="hand-pointer" size="2x" class="mb-2"></font-awesome-icon>
          <p>选择画布中的组件或左侧筛选器以编辑配置</p>
        </div>
      </aside>
    </div>

    <div v-if="aiPanelVisible" class="designer-ai-panel">
      <div class="designer-ai">
        <div class="designer-ai-header">
          <div class="designer-ai-title">
            <font-awesome-icon icon="wand-magic-sparkles"></font-awesome-icon>
            <span>AI 看板助手</span>
          </div>
          <el-button text @click="aiPanelVisible = false">关闭</el-button>
        </div>
        <div class="designer-ai-hint">描述需求或调整要求，生成结果会直接应用到当前画布，可继续手动编辑。</div>

        <div ref="aiMessageListRef" class="designer-ai-messages">
          <div v-if="aiMessages.length === 0" class="designer-ai-empty">
            <p>用一句话描述你想要的看板，AI 会自动确定数据集、图表与筛选器。</p>
            <div v-if="aiSamples.length" class="designer-ai-samples">
              <span v-for="sample in aiSamples" :key="sample" class="designer-ai-sample" @click="onAiSample(sample)">
                {{ sample }}
              </span>
            </div>
          </div>

          <div
            v-for="(message, index) in aiMessages"
            :key="index"
            class="designer-ai-message"
            :class="message.role === 'user' ? 'is-user' : 'is-assistant'"
          >
            <div class="designer-ai-bubble">
              <template v-if="message.loading">
                <span class="designer-ai-status">{{ message.statusText }}</span>
              </template>
              <template v-else-if="message.role === 'user'">
                {{ message.content }}
              </template>
              <template v-else>
                <pre class="designer-ai-explanation">{{ message.explanation }}</pre>
                <span v-if="message.hasSpec" class="designer-ai-badge">已应用到画布</span>
                <div v-if="message.toolCalls && message.toolCalls.length" class="designer-ai-tools">
                  <a class="designer-ai-toggle" @click="toggleAiTools(index)">
                    工具调用（{{ message.toolCalls.length }}）{{ aiToolsExpanded[index] ? '收起' : '展开' }}
                  </a>
                  <ul v-if="aiToolsExpanded[index]" class="designer-ai-tool-list">
                    <li v-for="(tool, toolIndex) in message.toolCalls" :key="toolIndex">
                      <span :class="tool.success ? 'text-success' : 'text-danger'">
                        {{ tool.name }}
                      </span>
                      <code v-if="formatAiToolArgs(tool.arguments)">
                        {{ formatAiToolArgs(tool.arguments) }}
                      </code>
                    </li>
                  </ul>
                </div>
              </template>
            </div>
          </div>
        </div>

        <div class="designer-ai-input">
          <textarea
            v-model="aiInput"
            class="designer-ai-textarea"
            rows="3"
            :disabled="aiGenerating || loading"
            :placeholder="aiPlaceholder"
            @keydown="onAiKeydown"
          ></textarea>
          <div class="designer-ai-actions">
            <span class="designer-ai-input-hint">Enter 发送 / Shift+Enter 换行</span>
            <el-button type="primary" size="small" :disabled="aiGenerating || loading" @click="sendAiMessage">
              {{ aiGenerating ? '生成中…' : '生成看板' }}
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="specJsonVisible" title="看板定义 JSON" width="720px" append-to-body>
      <el-alert v-if="specJsonError" type="error" :title="specJsonError" :closable="false" class="mb-2" />
      <p class="designer-json-hint">可手动修改下方 JSON，点击「应用并刷新」校验并重新渲染画布。</p>
      <textarea v-model="specJsonText" class="designer-json" spellcheck="false"></textarea>
      <template #footer>
        <el-button @click="specJsonVisible = false">关闭</el-button>
        <el-button type="primary" :loading="specJsonApplying" @click="applySpecJson"> 应用并刷新 </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" src="./analysis-dashboard-designer.component.ts"></script>

<style scoped>
.designer-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 96px);
  min-height: 620px;
  padding: 8px;
}

.designer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.designer-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.designer-name-input {
  width: 280px;
}

.designer-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.designer-history-actions {
  flex: 0 0 auto;
}

.designer-body {
  display: flex;
  flex: 1;
  gap: 10px;
  min-height: 0;
}

.designer-sidebar,
.designer-properties {
  width: 274px;
  min-width: 250px;
  padding: 10px;
  overflow: auto;
  background: #fff;
  border-radius: 8px;
}

.designer-sidebar {
  padding-top: 4px;
}

.designer-sidebar-tabs :deep(.el-tabs__header) {
  margin-bottom: 10px;
}

.designer-sidebar-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.designer-tab-label {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  font-size: 12px;
}

.designer-palette-tabs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4px;
  padding: 4px;
  background: #f5f7fa;
  border-radius: 8px;
}

.designer-palette-tab {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: center;
  padding: 6px 2px;
  color: #606266;
  font-size: 11px;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 6px;
  transition:
    color 0.15s,
    background 0.15s;
}

.designer-palette-tab:hover {
  color: #409eff;
  background: #ecf5ff;
}

.designer-palette-tab.is-active {
  color: #409eff;
  background: #fff;
  box-shadow: 0 1px 4px rgb(0 0 0 / 8%);
}

.designer-palette-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.designer-palette-card {
  display: flex;
  flex-direction: column;
  gap: 3px;
  align-items: flex-start;
  padding: 10px;
  cursor: grab;
  user-select: none;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  transition:
    border-color 0.15s,
    box-shadow 0.15s,
    transform 0.15s;
}

.designer-palette-card:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgb(64 158 255 / 15%);
  transform: translateY(-1px);
}

.designer-palette-card:active {
  cursor: grabbing;
}

.designer-palette-card.is-dragging {
  opacity: 0.45;
  border-style: dashed;
}

.designer-palette-card-icon {
  color: #409eff;
}

.designer-palette-card-label {
  color: #303133;
  font-size: 13px;
  font-weight: 500;
}

.designer-palette-card-hint {
  color: #a8abb2;
  font-size: 10px;
}

.designer-widget-list-title {
  margin-top: 4px;
  color: #303133;
  font-size: 12px;
  font-weight: 600;
}

.designer-add-row {
  display: flex;
  gap: 6px;
}

.designer-hint {
  color: #909399;
  font-size: 11px;
  line-height: 1.5;
}

.designer-empty-hint {
  padding: 20px 0;
  color: #c0c4cc;
  font-size: 12px;
  text-align: center;
}

.designer-list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}

.designer-widget-item {
  cursor: pointer;
}

.designer-widget-item.is-active {
  border-color: #409eff;
  background: #ecf5ff;
}

.designer-list-main {
  min-width: 0;
}

.designer-list-title {
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  color: #303133;
  font-size: 13px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.designer-list-sub {
  margin-top: 3px;
  overflow: hidden;
  color: #909399;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.designer-widget-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.designer-main {
  flex: 1;
  min-width: 0;
  padding: 10px;
  overflow: auto;
  background: #f5f7fa;
  border-radius: 8px;
}

.designer-main-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.designer-preview-filters {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 8px;
}

.designer-preview-filters-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.designer-preview-filters-heading {
  display: flex;
  align-items: center;
  gap: 8px;
}

.designer-preview-filters-title {
  color: #303133;
  font-size: 13px;
  font-weight: 600;
}

.designer-preview-filters-count {
  color: #909399;
  font-size: 11px;
}

.designer-preview-filters-empty {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  color: #909399;
  font-size: 12px;
  background: #fff;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
}

.designer-preview-filters-empty :deep(.el-button) {
  flex: 0 0 auto;
}

.designer-desc-input {
  max-width: 420px;
}

.designer-main-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
  font-size: 12px;
  white-space: nowrap;
}

.designer-tip {
  margin: 8px 0 4px;
  color: #909399;
  font-size: 11px;
}

.designer-property-title {
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
  color: #303133;
  font-weight: 600;
}

.designer-property-tabs {
  margin-top: 6px;
}

.designer-property-tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
}

.designer-property-tabs :deep(.el-tabs__item) {
  padding: 0 12px;
  font-size: 12px;
}

.designer-property-form {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;
}

.designer-property-form > label {
  margin-top: 4px;
  color: #606266;
  font-size: 12px;
}

.designer-inline {
  display: flex;
  gap: 6px;
  align-items: center;
}

.designer-property-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #c0c4cc;
  font-size: 12px;
  text-align: center;
}

.designer-ai {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.designer-ai-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
}

.designer-ai-title {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  color: #303133;
  font-weight: 600;
}

.designer-ai-hint {
  padding: 10px 16px 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.designer-ai-messages {
  flex: 1;
  min-height: 0;
  padding: 12px 16px;
  overflow-y: auto;
}

.designer-ai-empty {
  color: #909399;
  font-size: 13px;
}

.designer-ai-samples {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.designer-ai-sample {
  display: inline-block;
  padding: 3px 12px;
  color: #409eff;
  font-size: 12px;
  cursor: pointer;
  background: #ecf5ff;
  border: 1px solid #d9ecff;
  border-radius: 14px;
  transition: background 0.2s;
}

.designer-ai-sample:hover {
  background: #d9ecff;
}

.designer-ai-message {
  display: flex;
  margin-bottom: 12px;
}

.designer-ai-message.is-user {
  justify-content: flex-end;
}

.designer-ai-bubble {
  max-width: 92%;
  padding: 8px 12px;
  overflow-wrap: anywhere;
  font-size: 13px;
  color: #303133;
  background: #f4f4f5;
  border-radius: 8px;
}

.designer-ai-message.is-user .designer-ai-bubble {
  background: #ecf5ff;
}

.designer-ai-explanation {
  margin: 0;
  overflow-wrap: anywhere;
  font-family: inherit;
  white-space: pre-wrap;
}

.designer-ai-status {
  color: #909399;
}

.designer-ai-badge {
  display: inline-block;
  margin-top: 6px;
  padding: 1px 8px;
  color: #67c23a;
  font-size: 12px;
  background: #f0f9eb;
  border-radius: 4px;
}

.designer-ai-tools {
  margin-top: 6px;
}

.designer-ai-toggle {
  color: #409eff;
  font-size: 12px;
  cursor: pointer;
}

.designer-ai-tool-list {
  margin: 6px 0 0;
  padding-left: 18px;
  color: #606266;
  font-size: 12px;
}

.designer-ai-tool-list code {
  margin-left: 6px;
  color: #909399;
}

.designer-ai-input {
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
}

.designer-ai-textarea {
  box-sizing: border-box;
  display: block;
  width: 100%;
  padding: 8px 10px;
  color: #303133;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.5;
  resize: none;
  outline: none;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  transition: border-color 0.2s;
}

.designer-ai-textarea:focus {
  border-color: #409eff;
}

.designer-ai-textarea::placeholder {
  color: #a8abb2;
}

.designer-ai-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.designer-ai-input-hint {
  color: #c0c4cc;
  font-size: 12px;
}

.designer-ai-panel {
  position: fixed;
  top: 142px;
  right: 12px;
  bottom: 12px;
  z-index: 2000;
  width: 400px;
  max-width: calc(100vw - 24px);
  overflow: hidden;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  box-shadow: 0 8px 30px rgb(0 0 0 / 18%);
}

.designer-json-hint {
  margin: 0 0 8px;
  color: #909399;
  font-size: 12px;
}

.designer-json {
  box-sizing: border-box;
  display: block;
  width: 100%;
  height: 55vh;
  padding: 10px 12px;
  overflow: auto;
  color: #303133;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre;
  resize: vertical;
  outline: none;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

.designer-json:focus {
  border-color: #409eff;
}

.designer-json--compact {
  height: 320px;
  min-height: 180px;
}
</style>
