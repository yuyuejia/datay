<template>
  <div class="designer-page" v-loading="loading">
    <div class="designer-header">
      <div class="designer-header-left">
        <el-button text @click="goBack">← 返回</el-button>
        <el-input v-model="name" class="designer-name-input" placeholder="未命名看板" maxlength="120" />
        <el-tag v-if="aiMode" type="warning" size="small">AI 设计</el-tag>
      </div>
      <div class="designer-header-center">
        <el-radio-group v-model="layoutMode" size="small" class="designer-mode-switch">
          <el-radio-button value="standard">标准</el-radio-button>
          <el-radio-button value="screen">大屏</el-radio-button>
        </el-radio-group>
      </div>
      <div class="designer-header-actions">
        <el-button
          size="small"
          type="primary"
          :plain="!dashboardSettingsVisible"
          :disabled="loading"
          title="看板设置：模式、分辨率与画布缩放"
          @click="openDashboardSettings"
        >
          <font-awesome-icon icon="cog" class="mr-1"></font-awesome-icon>
          设置
        </el-button>
        <el-button size="small" title="重新加载看板数据集" @click="refreshData">刷新</el-button>
        <span v-if="loadingData" class="designer-loading-hint">加载中…</span>
        <el-button-group class="designer-history-actions" size="small">
          <el-button :disabled="!canUndo || saving || loading" title="撤销（Ctrl/Cmd+Z）" @click="undo">↶</el-button>
          <el-button :disabled="!canRedo || saving || loading" title="重做（Ctrl/Cmd+Shift+Z）" @click="redo">↷</el-button>
        </el-button-group>
        <el-button size="small" :disabled="loading || saving" title="在新页签预览当前设计，无需先保存" @click="openPreview">
          <font-awesome-icon icon="eye" class="mr-1"></font-awesome-icon>
          预览
        </el-button>
        <el-button size="small" :disabled="loading || saving" title="AI 看板助手" @click="openAiAssistant">
          <font-awesome-icon icon="wand-magic-sparkles" class="mr-1"></font-awesome-icon>
          AI 助手
        </el-button>
        <el-button size="small" type="primary" :loading="saving" @click="save">保存</el-button>
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
                  <div class="designer-list-title">{{ widget.title || (widget.type === 'html' ? '说明文本' : widget.id) }}</div>
                  <div class="designer-list-sub">{{ widget.type }} · {{ widget.datasetId || '未绑定数据集' }}</div>
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

      <main
        class="designer-main"
        :class="{ 'is-screen': isScreenMode, 'is-standard': !isScreenMode }"
        :style="!isScreenMode ? screenBackgroundStyle : undefined"
      >
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
        <div v-if="isScreenMode" class="designer-stage-caption">
          <span class="designer-stage-caption-title">大屏设计画布</span>
          <span>{{ screenWidth }} × {{ screenHeight }}</span>
          <span>缩放 {{ zoomPercent }}%{{ canvasZoom === 'fit' ? '（适应）' : '' }}</span>
        </div>
        <DashboardStage
          v-if="isScreenMode"
          class="designer-stage"
          :class="screenThemeClass"
          :width="screenWidth"
          :height="screenHeight"
          :zoom="canvasZoom"
          fit-mode="contain"
          :padding="16"
          :background-style="screenBackgroundStyle"
          @scale-change="onCanvasScaleChange"
        >
          <template #default="{ scale }">
            <DesignerCanvas
              v-bind="canvasBindings"
              :scale="scale"
              @select="selectWidget"
              @layout-change="onLayoutChange"
              @resize="onResize"
              @delete="removeWidget"
              @add-widget="onCanvasAddWidget"
            />
          </template>
        </DashboardStage>
        <DesignerCanvas
          v-else
          class="designer-canvas-standard"
          :class="!isScreenMode ? screenThemeClass : ''"
          v-bind="canvasBindings"
          :scale="1"
          @select="selectWidget"
          @layout-change="onLayoutChange"
          @resize="onResize"
          @delete="removeWidget"
          @add-widget="onCanvasAddWidget"
        />
        <div v-if="isCanvasOverflow" class="designer-canvas-overflow">
          <font-awesome-icon icon="triangle-exclamation" class="mr-1"></font-awesome-icon>
          内容超出画布高度，浏览时底部会被裁切
        </div>
      </main>

      <aside ref="propertiesRef" class="designer-properties">
        <template v-if="dashboardSettingsVisible">
          <div class="designer-property-title designer-settings-title">
            <span>看板设置</span>
            <el-button text size="small" @click="dashboardSettingsVisible = false">收起</el-button>
          </div>
          <div class="designer-property-form">
            <label>展示模式</label>
            <div class="designer-settings-value">
              <el-tag size="small" :type="isScreenMode ? 'primary' : 'info'">{{ isScreenMode ? '大屏模式' : '标准模式' }}</el-tag>
              <span class="designer-hint">可在顶部看板名称后切换</span>
            </div>

            <template v-if="isScreenMode">
              <label>屏幕分辨率</label>
              <el-select :model-value="screenPresetValue" style="width: 100%" @change="applyScreenPreset">
                <el-option v-for="preset in screenPresets" :key="preset.key" :label="preset.label" :value="preset.key" />
                <el-option label="自定义" value="custom" />
              </el-select>
              <div class="designer-inline">
                <el-input-number
                  v-model="screenWidth"
                  :min="640"
                  :max="7680"
                  :step="1"
                  controls-position="right"
                  style="flex: 1; min-width: 0"
                />
                <span class="designer-canvas-times">×</span>
                <el-input-number
                  v-model="screenHeight"
                  :min="480"
                  :max="4320"
                  :step="1"
                  controls-position="right"
                  style="flex: 1; min-width: 0"
                />
              </div>
              <div class="designer-hint">设计画布按该分辨率固定尺寸，设计时自动适应设计区；浏览时按全屏适配方式缩放。</div>

              <label>全屏适配方式</label>
              <el-select v-model="screenFitMode" style="width: 100%">
                <el-option v-for="option in fitModeOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
              <div class="designer-hint">{{ fitModeHint }}；仅在浏览 / 预览页生效。</div>

              <label>画布缩放</label>
              <div class="designer-inline">
                <el-button size="small" :disabled="zoomPercent <= 25" title="缩小画布" @click="zoomOut">−</el-button>
                <span class="designer-zoom-value">{{ zoomPercent }}%</span>
                <el-button size="small" :disabled="zoomPercent >= 200" title="放大画布" @click="zoomIn">+</el-button>
                <el-button size="small" :type="canvasZoom === 'fit' ? 'primary' : 'default'" @click="fitCanvas">适应</el-button>
              </div>
              <div class="designer-hint">默认自动适应设计区；放大后可滚动查看细节，浏览态始终自动缩放。</div>
            </template>
            <div v-else class="designer-settings-empty">标准模式使用自适应画布：宽度铺满设计区，高度随组件自动增长。</div>

            <label>主题</label>
            <el-radio-group v-model="screenTheme" size="small">
              <el-radio-button value="light">浅色</el-radio-button>
              <el-radio-button value="dark">深色</el-radio-button>
            </el-radio-group>

            <label>背景</label>
            <el-radio-group v-model="backgroundType" size="small">
              <el-radio-button value="color">纯色</el-radio-button>
              <el-radio-button value="gradient">渐变</el-radio-button>
              <el-radio-button value="image">图片</el-radio-button>
            </el-radio-group>
            <template v-if="backgroundType === 'color'">
              <el-color-picker v-model="backgroundColor" show-alpha />
            </template>
            <template v-else-if="backgroundType === 'gradient'">
              <div class="designer-inline">
                <el-color-picker v-model="gradientFrom" show-alpha />
                <span class="designer-canvas-times">→</span>
                <el-color-picker v-model="gradientTo" show-alpha />
              </div>
              <div class="designer-inline">
                <span class="designer-settings-label-inline">角度</span>
                <el-input-number
                  v-model="gradientAngle"
                  :min="0"
                  :max="360"
                  :step="15"
                  controls-position="right"
                  style="flex: 1; min-width: 0"
                />
              </div>
            </template>
            <template v-else>
              <div class="designer-settings-upload">
                <el-upload
                  ref="backgroundUploadRef"
                  :auto-upload="false"
                  :show-file-list="false"
                  accept="image/png,image/jpeg,image/webp,image/gif"
                  :on-change="onBackgroundFileChange"
                >
                  <el-button size="small" :loading="backgroundUploading">上传背景图</el-button>
                </el-upload>
                <el-button v-if="backgroundImagePath" text size="small" type="danger" @click="removeBackgroundImage">移除</el-button>
              </div>
              <img v-if="backgroundImageUrl" class="designer-settings-bg" :src="backgroundImageUrl" alt="背景图预览" />
              <div class="designer-hint">支持 PNG / JPEG / WebP / GIF，最大 5MB。</div>
            </template>

            <label>自动刷新</label>
            <div class="designer-inline">
              <el-input-number
                v-model="autoRefreshSeconds"
                :min="0"
                :max="86400"
                :step="10"
                controls-position="right"
                style="flex: 1; min-width: 0"
              />
              <span class="designer-settings-label-inline">秒</span>
            </div>
            <div class="designer-hint">0 表示不自动刷新；仅在浏览 / 预览页生效。</div>

            <label>栅格规则</label>
            <div class="designer-settings-grid">
              <span>24 栅格列</span>
              <span>行高 40px</span>
              <span>间距 12px</span>
            </div>
            <div class="designer-hint">组件按 24 栅格定位，垂直拖动步长为 52px。</div>

            <div v-if="isCanvasOverflow" class="designer-settings-warning">内容超出画布高度，浏览时底部会被裁切。</div>
          </div>
        </template>
        <template v-else-if="selectedWidget">
          <div class="designer-property-title">组件配置</div>
          <el-tabs v-model="propertyTab" class="designer-property-tabs">
            <el-tab-pane label="基础设置" name="basic">
              <div class="designer-property-form">
                <label>标题</label>
                <el-input v-model="selectedWidget.title" placeholder="组件标题" />
                <div v-if="selectedWidget.type === 'html'" class="designer-hint">留空则不显示标题栏，HTML 内容占满整个组件。</div>

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

                  <label>图表选项</label>
                  <div class="designer-chart-options">
                    <el-checkbox v-model="chartLegendVisible" class="designer-chart-option" @change="commitHistory"> 显示图例 </el-checkbox>
                    <el-checkbox v-model="chartDataLabelVisible" class="designer-chart-option" @change="commitHistory">
                      数据标签
                    </el-checkbox>
                    <el-checkbox v-if="hasChartAxes" v-model="chartAxesVisible" class="designer-chart-option" @change="commitHistory">
                      坐标轴
                    </el-checkbox>
                    <el-checkbox v-if="hasChartAxes" v-model="chartGridLinesVisible" class="designer-chart-option" @change="commitHistory">
                      网格线
                    </el-checkbox>
                  </div>

                  <template v-if="selectedWidget.encode">
                    <template v-if="chartType === 'pie'">
                      <label>分类字段</label>
                      <el-select
                        v-model="selectedWidget.encode.categoryField"
                        clearable
                        filterable
                        allow-create
                        placeholder="选择或输入分类字段"
                      >
                        <el-option v-for="field in widgetFields" :key="field.name" :label="field.label || field.name" :value="field.name" />
                      </el-select>

                      <label>数值字段</label>
                      <el-select v-model="widgetPrimaryValueField" clearable filterable allow-create placeholder="选择或输入数值字段">
                        <el-option
                          v-for="field in widgetValueFieldOptions"
                          :key="field.name"
                          :label="field.label || field.name"
                          :value="field.name"
                        />
                      </el-select>
                      <div class="designer-hint">饼图按分类字段分区，数值字段决定扇区大小；不绘制坐标轴。</div>

                      <label>聚合方式</label>
                      <el-select v-model="widgetAggregation">
                        <el-option value="sum" label="求和 sum" />
                        <el-option value="avg" label="平均 avg" />
                        <el-option value="min" label="最小 min" />
                        <el-option value="max" label="最大 max" />
                        <el-option value="count" label="计数 count" />
                        <el-option value="none" label="不聚合 none" />
                      </el-select>
                    </template>

                    <template v-else>
                      <label>横轴字段（X 轴）</label>
                      <el-select v-model="selectedWidget.encode.categoryField" clearable filterable allow-create placeholder="选择分类字段">
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

                      <label>数值字段聚合方式</label>
                      <div v-if="selectedWidget.encode.values?.length" class="designer-value-agg-list">
                        <div
                          v-for="(value, index) in selectedWidget.encode.values"
                          :key="`${value.field}-${index}`"
                          class="designer-value-agg-row"
                        >
                          <span class="designer-value-agg-field">{{ value.name || value.field || `字段 ${index + 1}` }}</span>
                          <el-select v-model="value.agg" size="small" @change="commitHistory">
                            <el-option value="sum" label="求和 sum" />
                            <el-option value="avg" label="平均 avg" />
                            <el-option value="min" label="最小 min" />
                            <el-option value="max" label="最大 max" />
                            <el-option value="count" label="计数 count" />
                            <el-option value="none" label="不聚合 none" />
                          </el-select>
                        </div>
                      </div>
                      <div v-else class="designer-hint">请先选择 Y 轴数值字段。</div>
                    </template>

                    <label>排序字段 / 方向</label>
                    <div class="designer-inline">
                      <el-select
                        v-model="selectedWidget.encode.sortField"
                        clearable
                        filterable
                        allow-create
                        placeholder="不排序"
                        style="flex: 1"
                      >
                        <el-option v-for="field in widgetFields" :key="field.name" :label="field.label || field.name" :value="field.name" />
                      </el-select>
                      <el-select v-model="selectedWidget.encode.sortOrder" style="width: 90px">
                        <el-option value="asc" label="升序" />
                        <el-option value="desc" label="降序" />
                      </el-select>
                    </div>

                    <label>返回行数上限</label>
                    <el-input-number v-model="selectedWidget.encode.limit" :min="1" :max="10000" controls-position="right" />
                  </template>
                </template>

                <template v-else-if="selectedWidget.type === 'table'">
                  <template v-if="selectedWidget.encode">
                    <label>排序字段 / 方向</label>
                    <div class="designer-inline">
                      <el-select
                        v-model="selectedWidget.encode.sortField"
                        clearable
                        filterable
                        allow-create
                        placeholder="不排序"
                        style="flex: 1"
                      >
                        <el-option v-for="field in widgetFields" :key="field.name" :label="field.label || field.name" :value="field.name" />
                      </el-select>
                      <el-select v-model="selectedWidget.encode.sortOrder" style="width: 90px">
                        <el-option value="asc" label="升序" />
                        <el-option value="desc" label="降序" />
                      </el-select>
                    </div>

                    <label>返回行数上限</label>
                    <el-input-number v-model="selectedWidget.encode.limit" :min="1" :max="10000" controls-position="right" />

                    <label>显示列</label>
                    <el-select
                      v-model="widgetTableColumns"
                      multiple
                      collapse-tags
                      collapse-tags-tooltip
                      filterable
                      allow-create
                      placeholder="默认展示全部列"
                    >
                      <el-option v-for="field in widgetFields" :key="field.name" :label="field.label || field.name" :value="field.name" />
                    </el-select>
                    <div class="designer-hint">不配置时展示数据集全部列；配置后按选择顺序展示。</div>

                    <label>分页大小</label>
                    <el-input-number
                      v-model="selectedWidget.encode.pageSize"
                      :min="5"
                      :max="1000"
                      controls-position="right"
                      placeholder="不分页"
                    />
                    <div class="designer-hint">配置后在前端按页展示；服务端分页仍受返回行数上限约束。</div>
                  </template>
                  <div class="designer-hint">明细表展示数据集列，可配置排序、返回行数上限和显示列。</div>
                </template>

                <template v-else-if="selectedWidget.type === 'kpi'">
                  <template v-if="selectedWidget.encode">
                    <label>数值字段</label>
                    <el-select v-model="widgetPrimaryValueField" clearable filterable allow-create placeholder="选择或输入数值字段">
                      <el-option
                        v-for="field in widgetValueFieldOptions"
                        :key="field.name"
                        :label="field.label || field.name"
                        :value="field.name"
                      />
                    </el-select>
                    <label>聚合方式</label>
                    <el-select v-model="widgetAggregation">
                      <el-option value="sum" label="求和 sum" />
                      <el-option value="avg" label="平均 avg" />
                      <el-option value="min" label="最小 min" />
                      <el-option value="max" label="最大 max" />
                      <el-option value="count" label="计数 count" />
                      <el-option value="none" label="不聚合 none" />
                    </el-select>
                  </template>
                  <div class="designer-hint">KPI 由所选字段与聚合方式计算，不再自动求和。</div>
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

            <label>目标数据集 / 字段</label>
            <div v-for="(target, index) in selectedFilter.targets" :key="index" class="designer-filter-target">
              <el-select v-model="target.datasetId" clearable placeholder="选择数据集" @change="onFilterTargetDatasetChange(target)">
                <el-option v-for="dataset in spec.datasets" :key="dataset.id" :label="dataset.name || dataset.id" :value="dataset.id" />
              </el-select>
              <template v-if="isSqlDataset(target.datasetId)">
                <div class="designer-filter-target-field">
                  <el-select
                    :model-value="filterTargetFieldValue(target)"
                    clearable
                    filterable
                    allow-create
                    placeholder="选择字段或 SQL 参数"
                    @update:model-value="value => onFilterTargetFieldSelect(target, String(value))"
                  >
                    <el-option :value="SQL_PARAM_OPTION" label="SQL 参数" />
                    <el-option
                      v-for="field in fieldCandidates(target.datasetId)"
                      :key="field.name"
                      :label="field.label || field.name"
                      :value="field.name"
                    />
                  </el-select>
                  <el-input
                    v-if="target.pushdown?.kind === 'sqlParam'"
                    v-model="target.pushdown.param"
                    size="small"
                    placeholder="SQL 参数名，如 store_name"
                    @change="commitHistory"
                  />
                </div>
              </template>
              <el-tag v-else-if="target.pushdown" type="success">{{ pushdownDetail(target) }}</el-tag>
              <el-select
                v-else
                v-model="target.field"
                clearable
                filterable
                allow-create
                :disabled="!target.datasetId"
                placeholder="选择或输入字段名"
                @change="onFilterTargetFieldChange(target)"
              >
                <el-option
                  v-for="field in fieldCandidates(target.datasetId)"
                  :key="field.name"
                  :label="field.label || field.name"
                  :value="field.name"
                />
              </el-select>
              <el-button text type="danger" :disabled="selectedFilter.targets.length <= 1" @click="removeFilterTarget(index)">
                删除
              </el-button>
            </div>
            <el-button text type="primary" @click="addFilterTarget">添加目标</el-button>
            <div class="designer-hint">SQL 参数目标表示数据集 SQL 中的 ${参数名}；普通字段目标可选择服务端或客户端过滤。</div>

            <div v-if="selectedFilter.type === 'select' || selectedFilter.type === 'multiSelect'" class="designer-hint">
              下拉选项将从字段目标的数据集去重值中加载；pushdown 目标由服务端处理。
            </div>

            <template
              v-if="
                (selectedFilter.type === 'select' || selectedFilter.type === 'multiSelect') &&
                !selectedFilter.targets?.some(target => target.field || target.pushdown)
              "
            >
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
  </div>
</template>

<script lang="ts" src="./analysis-dashboard-designer.component.ts"></script>

<style scoped>
.designer-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--app-topbar-height, 88px));
  min-height: 620px;
  padding: 8px;
}

.designer-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}

.designer-header-left {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
  justify-self: start;
}

.designer-header-center {
  display: flex;
  align-items: center;
  justify-self: center;
}

.designer-name-input {
  width: 280px;
  max-width: 100%;
}

.designer-mode-switch {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  height: 32px;
}

.designer-mode-switch :deep(.el-radio-button) {
  display: inline-flex;
  height: 100%;
}

.designer-mode-switch :deep(.el-radio-button__inner) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding-top: 0;
  padding-bottom: 0;
  line-height: 1;
}

.designer-header-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  min-width: 0;
  justify-self: end;
}

.designer-loading-hint {
  color: #909399;
  font-size: 11px;
  white-space: nowrap;
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
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  padding: 10px;
  overflow: auto;
  background: #f5f7fa;
  border-radius: 8px;
}

.designer-main.is-screen {
  overflow: hidden;
}

.designer-main.is-standard .designer-canvas-standard {
  flex: 1 0 auto;
  background-color: transparent;
}

.designer-stage {
  flex: 1 1 0;
  min-height: 0;
}

.designer-stage-caption {
  display: flex;
  flex: 0 0 auto;
  gap: 10px;
  align-items: center;
  margin-bottom: 6px;
  color: #606266;
  font-size: 12px;
}

.designer-stage-caption-title {
  color: #303133;
  font-weight: 600;
}

.designer-main.is-screen .designer-stage {
  height: 0;
  background: #dfe6f0;
  border-radius: 8px;
}

.designer-main.is-screen .designer-stage :deep(.dashboard-stage-frame) {
  outline: 1px solid #c3cfdf;
  box-shadow: 0 10px 34px rgb(31 45 61 / 14%);
}

.designer-canvas-times {
  color: #909399;
  font-size: 12px;
}

.designer-zoom-value {
  min-width: 44px;
  color: #606266;
  font-size: 12px;
  text-align: center;
}

.designer-canvas-overflow {
  flex: 0 0 auto;
  margin-top: 6px;
  padding: 6px 10px;
  color: #e6a23c;
  font-size: 12px;
  background: #fdf6ec;
  border: 1px solid #faecd8;
  border-radius: 6px;
}

.designer-preview-filters {
  display: flex;
  flex: 0 0 auto;
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

.designer-property-title {
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
  color: #303133;
  font-weight: 600;
}

.designer-settings-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.designer-settings-value {
  display: flex;
  gap: 8px;
  align-items: center;
}

.designer-settings-empty {
  padding: 8px 10px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
  background: #f5f7fa;
  border-radius: 6px;
}

.designer-settings-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.designer-settings-grid span {
  padding: 2px 8px;
  color: #409eff;
  font-size: 11px;
  background: #ecf5ff;
  border-radius: 10px;
}

.designer-settings-warning {
  padding: 6px 10px;
  color: #e6a23c;
  font-size: 12px;
  line-height: 1.5;
  background: #fdf6ec;
  border: 1px solid #faecd8;
  border-radius: 6px;
}

.designer-settings-label-inline {
  color: #909399;
  font-size: 12px;
  white-space: nowrap;
}

.designer-settings-upload {
  display: flex;
  gap: 8px;
  align-items: center;
}

.designer-settings-bg {
  width: 100%;
  max-height: 120px;
  object-fit: cover;
  border: 1px solid #ebeef5;
  border-radius: 6px;
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

.designer-filter-target {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 6px;
  align-items: center;
  margin-bottom: 6px;
}

.designer-filter-target-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.designer-value-agg-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.designer-chart-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.designer-chart-option {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 4px 0;
  background: transparent;
}

.designer-chart-option :deep(.el-checkbox__label) {
  padding-left: 6px;
  color: #606266;
  font-size: 12px;
}

.designer-value-agg-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 150px;
  gap: 8px;
  align-items: center;
}

.designer-value-agg-field {
  overflow: hidden;
  color: #606266;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
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
