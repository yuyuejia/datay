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
                  <div class="designer-list-title">{{ widgetLabel(widget) }}</div>
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
        :class="[screenThemeClass, { 'is-screen': isScreenMode, 'is-standard': !isScreenMode }]"
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
            manual-query
            @search="onFilterSearch"
          />
          <div v-else class="designer-preview-filters-empty">
            <span>暂无筛选器，添加后可在预览区设置条件，点击“查询”后作用于预览。</span>
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
              @child-layout-change="onChildLayoutChange"
              @child-resize="onChildResize"
              @delete="removeWidget"
              @layer-move="moveWidgetLayer"
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
          @child-layout-change="onChildLayoutChange"
          @child-resize="onChildResize"
          @delete="removeWidget"
          @layer-move="moveWidgetLayer"
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

              <label>布局单位</label>
              <el-radio-group v-model="canvasUnit" size="small">
                <el-radio-button value="grid">栅格</el-radio-button>
                <el-radio-button value="px">像素</el-radio-button>
              </el-radio-group>
              <div class="designer-hint">栅格按 24 列自动对齐；像素按设计分辨率自由定位，支持组件叠放（用组件左上角 ↑ ↓ 调整层级）。</div>

              <label>画布内边距</label>
              <div class="designer-inline">
                <el-input-number
                  v-model="canvasPadding"
                  :min="0"
                  :max="64"
                  :step="1"
                  controls-position="right"
                  style="flex: 1; min-width: 0"
                />
                <span class="designer-settings-label-inline">px</span>
              </div>
              <div class="designer-hint">设为 0 即贴边，适合整屏大屏布局。</div>

              <template v-if="canvasUnit === 'grid'">
                <label>栅格间距</label>
                <div class="designer-inline">
                  <el-input-number
                    v-model="canvasGap"
                    :min="0"
                    :max="48"
                    :step="1"
                    controls-position="right"
                    style="flex: 1; min-width: 0"
                  />
                  <span class="designer-settings-label-inline">px</span>
                </div>
              </template>

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

            <label>主题预设</label>
            <el-select v-model="themePreset" style="width: 100%">
              <el-option v-for="option in themePresetOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
            <label>主题配色</label>
            <div class="designer-inline">
              <el-color-picker v-model="themePrimary" show-alpha title="主色" />
              <el-color-picker v-model="themeSecondary" show-alpha title="辅助色" />
              <el-color-picker v-model="themeAccent" show-alpha title="强调色" />
              <span class="designer-settings-label-inline">主色 / 辅助 / 强调</span>
            </div>
            <div class="designer-inline">
              <el-color-picker v-model="themeSurface" show-alpha title="卡片底色" />
              <el-color-picker v-model="themeBorder" show-alpha title="卡片描边" />
              <el-color-picker v-model="themeText" show-alpha title="正文颜色" />
              <span class="designer-settings-label-inline">底色 / 描边 / 文字</span>
            </div>
            <el-button size="small" text type="primary" @click="resetThemeTokens">恢复预设配色</el-button>
            <div class="designer-hint">主题 token 是组件默认皮肤；单个组件样式可覆盖。</div>

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

            <label>定位规则</label>
            <div class="designer-settings-grid">
              <span>{{ canvasUnit === 'px' ? '像素定位' : '24 栅格列' }}</span>
              <span>行高 40px</span>
              <span>内边距 {{ canvasPadding }}px</span>
              <span v-if="canvasUnit === 'grid'">间距 {{ canvasGap }}px</span>
            </div>
            <div class="designer-hint">
              {{
                canvasUnit === 'px'
                  ? '组件按设计分辨率像素坐标定位，拖拽不做栅格吸附，允许重叠。'
                  : `组件按 24 栅格定位，垂直拖动步长为 ${40 + canvasGap}px。`
              }}
            </div>

            <div v-if="isCanvasOverflow" class="designer-settings-warning">内容超出画布高度，浏览时底部会被裁切。</div>
          </div>
        </template>
        <template v-else-if="selectedWidget">
          <div class="designer-property-title designer-inspector-head">
            <div class="designer-inspector-heading">
              <span class="designer-inspector-title">
                {{ selectedWidget.type === 'echarts' ? chartTypeLabel : widgetLabel(selectedWidget) }}
              </span>
              <span class="designer-inspector-sub">{{ selectedWidgetDatasetLabel }}</span>
            </div>
          </div>
          <el-tabs v-model="propertyTab" class="designer-property-tabs">
            <el-tab-pane label="基础配置" name="basic">
              <div class="designer-property-form">
                <template v-if="selectedWidget.type !== 'title' && selectedWidget.type !== 'clock'">
                  <label>标题</label>
                  <el-input v-model="selectedWidget.title" placeholder="组件标题" />
                  <div v-if="selectedWidget.type === 'html'" class="designer-hint">留空则不显示标题栏，HTML 内容占满整个组件。</div>
                </template>

                <template v-if="selectedWidget.type !== 'html' && selectedWidget.type !== 'title' && selectedWidget.type !== 'clock'">
                  <label>数据集</label>
                  <el-select v-model="selectedWidget.datasetId" clearable placeholder="选择数据集">
                    <el-option v-for="dataset in spec.datasets" :key="dataset.id" :label="dataset.name || dataset.id" :value="dataset.id" />
                  </el-select>
                </template>

                <template v-if="selectedWidget.type !== 'container' && containerOptions.length">
                  <label>所属容器</label>
                  <el-select v-model="widgetContainerId" clearable placeholder="不放入容器">
                    <el-option
                      v-for="container in containerOptions"
                      :key="container.id"
                      :label="container.title || container.id"
                      :value="container.id"
                    />
                  </el-select>
                  <div class="designer-hint">放入容器后，移动容器时子组件会一起移动。</div>
                </template>

                <template v-if="isPixelCanvas && selectedWidget.type !== 'container'">
                  <label>层级</label>
                  <el-input-number v-model="widgetZIndex" :min="0" :max="999" :step="1" controls-position="right" style="width: 100%" />
                  <div class="designer-hint">数值越大越靠上；也可用组件左上角 ↑ ↓ 微调。</div>
                </template>

                <template v-if="selectedWidget.type === 'container'">
                  <div class="designer-hint">
                    容器用于承载面板背景与标题，子组件通过「所属容器」归入；容器移动时子组件跟随，删除容器会一并删除子组件。
                  </div>
                </template>

                <template v-if="selectedWidget.type === 'title'">
                  <label>主标题</label>
                  <el-input v-model="titleWidgetText" placeholder="如 领导驾驶舱" maxlength="120" />
                  <label>副标题</label>
                  <el-input v-model="titleWidgetSubText" placeholder="可选，如 学生管理主题" maxlength="60" />
                  <label>对齐</label>
                  <el-radio-group v-model="titleWidgetAlign" size="small">
                    <el-radio-button value="left">左</el-radio-button>
                    <el-radio-button value="center">中</el-radio-button>
                    <el-radio-button value="right">右</el-radio-button>
                  </el-radio-group>
                  <label>边框</label>
                  <el-select v-model="titleWidgetFrame" style="width: 100%">
                    <el-option value="none" label="无" />
                    <el-option value="tech-frame" label="科技边框" />
                    <el-option value="bracket" label="左右括号" />
                  </el-select>
                  <div class="designer-hint">字号、颜色、字距与图标在「自定义配置 → 组件字体样式」中设置。</div>
                </template>

                <template v-else-if="selectedWidget.type === 'clock'">
                  <label>时间格式</label>
                  <el-select v-model="clockFormat" style="width: 100%">
                    <el-option value="date-week" label="日期 + 星期" />
                    <el-option value="date" label="日期" />
                    <el-option value="datetime" label="日期 + 时间（含秒）" />
                    <el-option value="time" label="时间（含秒）" />
                  </el-select>
                  <div class="designer-hint">日期类每分钟刷新，时间类每秒刷新；字体样式在「自定义配置 → 组件字体样式」中设置。</div>
                </template>

                <template v-if="selectedWidget.type === 'echarts'">
                  <label>类型</label>
                  <el-select :model-value="chartType" @update:model-value="setChartType">
                    <el-option value="bar" label="柱状图" />
                    <el-option value="line" label="折线图" />
                    <el-option value="area" label="面积图" />
                    <el-option value="pie" label="饼图" />
                    <el-option value="donut" label="环形图" />
                    <el-option value="gauge" label="仪表盘" />
                    <el-option value="scatter" label="散点图" />
                    <el-option value="radar" label="雷达图" />
                    <el-option value="funnel" label="漏斗图" />
                    <el-option value="heatmap" label="热力图" />
                    <el-option value="treemap" label="矩形树图" />
                    <el-option value="waterfall" label="瀑布图" />
                    <el-option value="sankey" label="桑基图" />
                    <el-option value="graph" label="关系图" />
                    <el-option value="boxplot" label="箱线图" />
                    <el-option value="map" label="地图（中国省级）" />
                  </el-select>

                  <template v-if="chartSubtypeOptions.length > 0">
                    <label>样式</label>
                    <el-select v-model="chartSubtype" @change="commitHistory">
                      <el-option v-for="option in chartSubtypeOptions" :key="option.value" :value="option.value" :label="option.label" />
                    </el-select>
                  </template>

                  <template v-if="chartType === 'bar'">
                    <label>方向</label>
                    <el-select v-model="chartOrientation" @change="commitHistory">
                      <el-option value="vertical" label="纵向" />
                      <el-option value="horizontal" label="横向" />
                    </el-select>
                    <el-checkbox
                      v-model="chartCombo"
                      class="designer-chart-option"
                      :disabled="widgetValueFields.length < 2"
                      @change="commitHistory"
                    >
                      双轴组合（首个度量柱状，其余折线）
                    </el-checkbox>
                    <div v-if="widgetValueFields.length < 2" class="designer-hint">添加至少两个度量后可启用双轴组合。</div>
                  </template>

                  <template v-if="chartType === 'map'">
                    <label>地图图层</label>
                    <div class="designer-map-layer">
                      <el-checkbox v-model="mapScatterEnabled">涟漪点</el-checkbox>
                      <template v-if="mapScatterEnabled">
                        <el-select v-model="mapScatterDatasetId" placeholder="图层数据集" style="width: 100%">
                          <el-option
                            v-for="dataset in spec.datasets"
                            :key="dataset.id"
                            :label="dataset.name || dataset.id"
                            :value="dataset.id"
                          />
                        </el-select>
                        <div class="designer-inline">
                          <el-select v-model="mapScatterLngField" filterable placeholder="经度字段" style="flex: 1; min-width: 0">
                            <el-option
                              v-for="field in fieldCandidates(mapScatterDatasetId)"
                              :key="field.name"
                              :label="field.label || field.name"
                              :value="field.name"
                            />
                          </el-select>
                          <el-select v-model="mapScatterLatField" filterable placeholder="纬度字段" style="flex: 1; min-width: 0">
                            <el-option
                              v-for="field in fieldCandidates(mapScatterDatasetId)"
                              :key="field.name"
                              :label="field.label || field.name"
                              :value="field.name"
                            />
                          </el-select>
                        </div>
                        <div class="designer-inline">
                          <el-select v-model="mapScatterNameField" clearable placeholder="名称字段" style="flex: 1; min-width: 0">
                            <el-option
                              v-for="field in fieldCandidates(mapScatterDatasetId)"
                              :key="field.name"
                              :label="field.label || field.name"
                              :value="field.name"
                            />
                          </el-select>
                          <el-select v-model="mapScatterValueField" clearable placeholder="数值字段" style="flex: 1; min-width: 0">
                            <el-option
                              v-for="field in fieldCandidates(mapScatterDatasetId)"
                              :key="field.name"
                              :label="field.label || field.name"
                              :value="field.name"
                            />
                          </el-select>
                        </div>
                        <div class="designer-inline">
                          <el-color-picker v-model="mapScatterColor" show-alpha />
                          <span class="designer-settings-label-inline">点大小</span>
                          <el-input-number
                            v-model="mapScatterSize"
                            :min="2"
                            :max="80"
                            controls-position="right"
                            style="flex: 1; min-width: 0"
                          />
                        </div>
                      </template>

                      <el-checkbox v-model="mapLinesEnabled">飞线</el-checkbox>
                      <template v-if="mapLinesEnabled">
                        <el-select v-model="mapLinesDatasetId" placeholder="流向数据集" style="width: 100%">
                          <el-option
                            v-for="dataset in spec.datasets"
                            :key="dataset.id"
                            :label="dataset.name || dataset.id"
                            :value="dataset.id"
                          />
                        </el-select>
                        <div class="designer-inline">
                          <el-select v-model="mapLinesFromLngField" filterable placeholder="起点经度" style="flex: 1; min-width: 0">
                            <el-option
                              v-for="field in fieldCandidates(mapLinesDatasetId)"
                              :key="field.name"
                              :label="field.label || field.name"
                              :value="field.name"
                            />
                          </el-select>
                          <el-select v-model="mapLinesFromLatField" filterable placeholder="起点纬度" style="flex: 1; min-width: 0">
                            <el-option
                              v-for="field in fieldCandidates(mapLinesDatasetId)"
                              :key="field.name"
                              :label="field.label || field.name"
                              :value="field.name"
                            />
                          </el-select>
                        </div>
                        <div class="designer-inline">
                          <el-select v-model="mapLinesToLngField" filterable placeholder="终点经度" style="flex: 1; min-width: 0">
                            <el-option
                              v-for="field in fieldCandidates(mapLinesDatasetId)"
                              :key="field.name"
                              :label="field.label || field.name"
                              :value="field.name"
                            />
                          </el-select>
                          <el-select v-model="mapLinesToLatField" filterable placeholder="终点纬度" style="flex: 1; min-width: 0">
                            <el-option
                              v-for="field in fieldCandidates(mapLinesDatasetId)"
                              :key="field.name"
                              :label="field.label || field.name"
                              :value="field.name"
                            />
                          </el-select>
                        </div>
                        <div class="designer-inline">
                          <el-select v-model="mapLinesValueField" clearable placeholder="数值字段" style="flex: 1; min-width: 0">
                            <el-option
                              v-for="field in fieldCandidates(mapLinesDatasetId)"
                              :key="field.name"
                              :label="field.label || field.name"
                              :value="field.name"
                            />
                          </el-select>
                          <el-color-picker v-model="mapLinesColor" show-alpha />
                        </div>
                        <div class="designer-inline">
                          <span class="designer-settings-label-inline">曲率</span>
                          <el-input-number
                            v-model="mapLinesCurveness"
                            :min="0"
                            :max="1"
                            :step="0.05"
                            controls-position="right"
                            style="flex: 1; min-width: 0"
                          />
                        </div>
                      </template>
                      <div class="designer-hint">区域填色沿用基础配置里的分类与度量字段；经纬度为十进制度。</div>
                    </div>
                  </template>

                  <template v-if="selectedWidget.encode">
                    <template v-if="['pie', 'donut', 'funnel', 'treemap', 'map'].includes(chartType)">
                      <label>{{ chartType === 'map' ? '区域名称字段' : '分类字段' }}</label>
                      <el-select v-model="widgetCategoryField" clearable filterable allow-create placeholder="选择或输入分类字段">
                        <el-option v-for="field in widgetFields" :key="field.name" :label="field.label || field.name" :value="field.name" />
                      </el-select>

                      <label>{{ chartType === 'map' ? '指标数值字段' : '数值字段' }}</label>
                      <el-select v-model="widgetPrimaryValueField" clearable filterable allow-create placeholder="选择或输入数值字段">
                        <el-option
                          v-for="field in widgetValueFieldOptions"
                          :key="field.name"
                          :label="field.label || field.name"
                          :value="field.name"
                        />
                      </el-select>
                      <div v-if="chartType === 'map'" class="designer-hint">
                        当前内置中国省级地图；区域名称字段需为省级名称或可识别写法（如
                        北京、广东省、广西壮族自治区），数值字段决定区域填色深浅。
                      </div>
                      <div v-else class="designer-hint">
                        饼图/环形图/漏斗图/矩形树图按分类字段展开，数值字段决定面积大小；不绘制坐标轴。
                      </div>

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

                    <template v-else-if="chartType === 'gauge'">
                      <label>数值字段</label>
                      <el-select v-model="widgetPrimaryValueField" clearable filterable allow-create placeholder="选择或输入数值字段">
                        <el-option
                          v-for="field in widgetValueFieldOptions"
                          :key="field.name"
                          :label="field.label || field.name"
                          :value="field.name"
                        />
                      </el-select>
                      <div class="designer-hint">仪表盘对数值字段聚合后展示单值，不绘制坐标轴。</div>

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
                      <label>{{
                        chartType === 'radar'
                          ? '维度 / 指标项'
                          : chartType === 'heatmap'
                            ? 'X 轴字段（列）'
                            : chartType === 'sankey' || chartType === 'graph'
                              ? '源节点字段'
                              : chartType === 'boxplot'
                                ? '分类字段'
                                : chartType === 'waterfall'
                                  ? '阶段 / 分类字段'
                                  : '横轴字段（X 轴）'
                      }}</label>
                      <el-select v-model="widgetCategoryField" clearable filterable allow-create placeholder="选择分类字段">
                        <el-option v-for="field in widgetFields" :key="field.name" :label="field.label || field.name" :value="field.name" />
                      </el-select>
                      <div v-if="chartType === 'heatmap'" class="designer-hint">热力图需要 X 轴与系列字段分别表示列和行。</div>
                      <div v-else-if="chartType === 'sankey' || chartType === 'graph'" class="designer-hint">
                        桑基图/关系图需要源节点字段与目标节点字段，数值字段作为连线权重。
                      </div>
                      <div v-else-if="chartType === 'radar'" class="designer-hint">
                        雷达图按该维度生成指标轴，每个度量生成一条雷达系列。
                      </div>
                      <div v-else-if="chartType === 'boxplot'" class="designer-hint">箱线图按分类字段对数值字段的原始明细计算四分位。</div>
                      <div v-else class="designer-hint">ECharts category 轴使用单个分类字段。</div>

                      <template v-if="chartType !== 'radar' && chartType !== 'boxplot' && chartType !== 'waterfall'">
                        <label>{{
                          chartType === 'heatmap'
                            ? 'Y 轴字段（行）'
                            : chartType === 'sankey' || chartType === 'graph'
                              ? '目标节点字段'
                              : '系列字段（可选）'
                        }}</label>
                        <el-select
                          v-model="widgetSeriesField"
                          :clearable="!['heatmap', 'sankey', 'graph'].includes(chartType)"
                          filterable
                          allow-create
                          :placeholder="
                            chartType === 'heatmap'
                              ? '选择热力图行字段'
                              : chartType === 'sankey' || chartType === 'graph'
                                ? '选择目标节点字段'
                                : '不拆分系列'
                          "
                        >
                          <el-option
                            v-for="field in widgetFields"
                            :key="field.name"
                            :label="field.label || field.name"
                            :value="field.name"
                          />
                        </el-select>
                        <div class="designer-hint">
                          {{
                            chartType === 'heatmap'
                              ? '热力图的 Y 轴字段，用于按行展开。'
                              : chartType === 'sankey' || chartType === 'graph'
                                ? '连线从源节点指向目标节点；节点自动去重。'
                                : '选择后按该字段拆分为多条系列，常用于多系列柱/折线图。'
                          }}
                        </div>
                      </template>

                      <label>{{
                        chartType === 'radar'
                          ? '指标系列（可多选）'
                          : chartType === 'heatmap' || chartType === 'sankey' || chartType === 'graph'
                            ? '数值字段（取第一个）'
                            : chartType === 'boxplot'
                              ? '分布数值字段'
                              : chartType === 'waterfall'
                                ? '增减值字段'
                                : '纵轴字段（Y 轴）'
                      }}</label>
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

                      <template v-if="chartType === 'scatter'">
                        <label>点大小字段（可选）</label>
                        <el-select v-model="widgetSizeField" clearable filterable allow-create placeholder="不映射点大小">
                          <el-option
                            v-for="field in widgetValueFieldOptions"
                            :key="field.name"
                            :label="field.label || field.name"
                            :value="field.name"
                          />
                        </el-select>
                        <div class="designer-hint">按数值大小映射散点符号大小。</div>
                      </template>

                      <label>数值字段聚合方式</label>
                      <div v-if="selectedWidget.encode.measures?.length" class="designer-value-agg-list">
                        <div
                          v-for="(measure, index) in selectedWidget.encode.measures"
                          :key="`${measure.field}-${index}`"
                          class="designer-value-agg-row"
                        >
                          <span class="designer-value-agg-field">{{ measure.name || measure.field || `字段 ${index + 1}` }}</span>
                          <el-select v-model="measure.agg" size="small" @change="commitHistory">
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
                  </template>

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
                      <el-select v-model="selectedWidget.encode.sortOrder" placeholder="方向" style="width: 90px">
                        <el-option value="asc" label="升序" />
                        <el-option value="desc" label="降序" />
                      </el-select>
                    </div>
                    <label>返回行数上限</label>
                    <el-input-number v-model="selectedWidget.encode.limit" :min="1" :max="10000" controls-position="right" />
                    <el-checkbox v-model="chartPercentOfTotal" class="designer-chart-option" @change="commitHistory">
                      计算占总比
                    </el-checkbox>
                    <div class="designer-hint">排序、行数上限与占总比在服务端查询时生效。</div>
                  </template>

                  <div class="designer-hint">外观、图例、坐标轴与高级图表配置在「自定义配置」中设置。</div>
                </template>

                <template v-else-if="selectedWidget.type === 'pivot'">
                  <template v-if="selectedWidget.encode">
                    <label>行维度</label>
                    <el-select v-model="widgetRowField" clearable filterable allow-create placeholder="选择行维度">
                      <el-option v-for="field in widgetFields" :key="field.name" :label="field.label || field.name" :value="field.name" />
                    </el-select>

                    <label>列维度（可选）</label>
                    <el-select v-model="widgetColumnField" clearable filterable allow-create placeholder="不展开列">
                      <el-option v-for="field in widgetFields" :key="field.name" :label="field.label || field.name" :value="field.name" />
                    </el-select>
                    <div class="designer-hint">按行维度分组，列维度横向展开；数值字段聚合后展示在交叉单元格。</div>

                    <label>数值字段</label>
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

                    <label>数值字段聚合方式</label>
                    <div v-if="selectedWidget.encode.measures?.length" class="designer-value-agg-list">
                      <div
                        v-for="(measure, index) in selectedWidget.encode.measures"
                        :key="`${measure.field}-${index}`"
                        class="designer-value-agg-row"
                      >
                        <span class="designer-value-agg-field">{{ measure.name || measure.field || `字段 ${index + 1}` }}</span>
                        <el-select v-model="measure.agg" size="small" @change="commitHistory">
                          <el-option value="sum" label="求和 sum" />
                          <el-option value="avg" label="平均 avg" />
                          <el-option value="min" label="最小 min" />
                          <el-option value="max" label="最大 max" />
                          <el-option value="count" label="计数 count" />
                          <el-option value="none" label="不聚合 none" />
                        </el-select>
                      </div>
                    </div>
                    <label>返回行数上限</label>
                    <el-input-number v-model="selectedWidget.encode.limit" :min="1" :max="10000" controls-position="right" />
                  </template>
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

                <template v-if="selectedWidget.type === 'table'">
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
                      <el-select v-model="selectedWidget.encode.sortOrder" placeholder="方向" style="width: 90px">
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
                  <div v-if="!selectedWidget.encode" class="designer-hint">
                    明细表展示数据集列，可配置排序、返回行数上限、显示列与分页。
                  </div>
                </template>
                <template v-if="['table', 'pivot', 'kpi'].includes(selectedWidget.type)">
                  <label>条件格式</label>
                  <div v-if="selectedWidget.formatRules?.length" class="designer-format-rule-list">
                    <div v-for="(rule, index) in selectedWidget.formatRules" :key="rule.id || index" class="designer-format-rule">
                      <el-select v-model="rule.field" clearable placeholder="全部数值" style="flex: 1; min-width: 110px">
                        <el-option v-for="field in widgetFields" :key="field.name" :label="field.label || field.name" :value="field.name" />
                      </el-select>
                      <el-select v-model="rule.operator" style="width: 96px">
                        <el-option value="gt" label="大于" />
                        <el-option value="gte" label="大于等于" />
                        <el-option value="lt" label="小于" />
                        <el-option value="lte" label="小于等于" />
                        <el-option value="eq" label="等于" />
                        <el-option value="neq" label="不等于" />
                        <el-option value="between" label="介于" />
                      </el-select>
                      <el-input-number v-model="rule.value" :controls="false" style="width: 82px" />
                      <el-input-number v-if="rule.operator === 'between'" v-model="rule.value2" :controls="false" style="width: 82px" />
                      <el-color-picker v-model="rule.color" size="small" title="文字颜色" />
                      <el-color-picker v-model="rule.background" size="small" title="背景色" />
                      <el-checkbox v-model="rule.bold">加粗</el-checkbox>
                      <el-button link type="danger" @click="removeFormatRule(index)">删除</el-button>
                    </div>
                  </div>
                  <el-button size="small" @click="addFormatRule">添加条件格式</el-button>
                  <div class="designer-hint">按数值条件设置文字颜色、背景色或加粗；字段为空时对全部数值单元格生效。</div>
                </template>
              </div>
            </el-tab-pane>

            <el-tab-pane label="自定义配置" name="custom">
              <el-collapse v-model="customSections" class="designer-config-collapse">
                <el-collapse-item name="background" title="背景与标题">
                  <div class="designer-property-form">
                    <label>边框预设</label>
                    <el-select v-model="cardPreset" style="width: 100%">
                      <el-option value="none" label="无" />
                      <el-option value="tech-corner" label="科技角标" />
                      <el-option value="tech-frame" label="科技边框" />
                      <el-option value="bracket" label="左右括号" />
                    </el-select>

                    <label>背景颜色</label>
                    <el-radio-group v-model="cardFillType" size="small" class="designer-config-radio">
                      <el-radio-button value="none">默认</el-radio-button>
                      <el-radio-button value="color">纯色</el-radio-button>
                      <el-radio-button value="gradient">渐变</el-radio-button>
                    </el-radio-group>
                    <div v-if="cardFillType === 'color'" class="designer-inline">
                      <el-color-picker v-model="cardFillColor" show-alpha />
                      <span class="designer-settings-label-inline">卡片底色</span>
                    </div>
                    <template v-else-if="cardFillType === 'gradient'">
                      <div class="designer-inline">
                        <el-color-picker v-model="cardGradientFrom" show-alpha />
                        <span class="designer-canvas-times">→</span>
                        <el-color-picker v-model="cardGradientTo" show-alpha />
                      </div>
                      <div class="designer-inline">
                        <span class="designer-settings-label-inline">角度</span>
                        <el-input-number
                          v-model="cardGradientAngle"
                          :min="0"
                          :max="360"
                          :step="15"
                          controls-position="right"
                          style="flex: 1; min-width: 0"
                        />
                      </div>
                    </template>

                    <label>描边</label>
                    <div class="designer-inline">
                      <el-input-number v-model="cardBorderWidth" :min="0" :max="8" controls-position="right" style="width: 86px" />
                      <el-select v-model="cardBorderStyle" style="width: 88px">
                        <el-option value="solid" label="实线" />
                        <el-option value="dashed" label="虚线" />
                        <el-option value="dotted" label="点线" />
                      </el-select>
                      <el-color-picker v-model="cardBorderColor" show-alpha />
                    </div>
                    <div class="designer-inline">
                      <span class="designer-settings-label-inline">圆角</span>
                      <el-input-number
                        v-model="cardBorderRadius"
                        :min="0"
                        :max="32"
                        controls-position="right"
                        style="flex: 1; min-width: 0"
                      />
                      <span class="designer-settings-label-inline">内边距</span>
                      <el-input-number v-model="cardPadding" :min="0" :max="48" controls-position="right" style="flex: 1; min-width: 0" />
                    </div>

                    <label>阴影与透明度</label>
                    <div class="designer-inline">
                      <span class="designer-settings-label-inline">模糊</span>
                      <el-input-number
                        v-model="cardShadowBlur"
                        :min="0"
                        :max="100"
                        controls-position="right"
                        style="flex: 1; min-width: 0"
                      />
                      <el-color-picker v-model="cardShadowColor" show-alpha />
                    </div>
                    <div class="designer-inline">
                      <span class="designer-settings-label-inline">透明度</span>
                      <el-input-number
                        v-model="cardOpacity"
                        :min="0"
                        :max="1"
                        :step="0.05"
                        controls-position="right"
                        style="flex: 1; min-width: 0"
                      />
                      <span class="designer-settings-label-inline">毛玻璃</span>
                      <el-input-number
                        v-model="cardBackdropBlur"
                        :min="0"
                        :max="20"
                        controls-position="right"
                        style="flex: 1; min-width: 0"
                      />
                    </div>

                    <div class="designer-config-subtitle">标题栏</div>
                    <el-checkbox v-if="selectedWidget.type !== 'title' && selectedWidget.type !== 'clock'" v-model="titleShow">
                      显示标题栏
                    </el-checkbox>
                    <label>图标</label>
                    <el-select v-model="titleIcon" filterable allow-create clearable placeholder="选择图标（可留空）" style="width: 100%">
                      <el-option v-for="icon in titleIconOptions" :key="icon" :label="icon" :value="icon" />
                    </el-select>
                    <label>标题装饰</label>
                    <el-select v-model="titleDecoration" style="width: 100%">
                      <el-option value="none" label="无" />
                      <el-option value="underline" label="整条下划线" />
                      <el-option value="gradient-underline" label="渐变短线" />
                      <el-option value="left-bar" label="左侧竖条" />
                    </el-select>
                    <div class="designer-inline">
                      <span class="designer-settings-label-inline">高度</span>
                      <el-input-number v-model="titleHeight" :min="0" :max="80" controls-position="right" style="flex: 1; min-width: 0" />
                      <el-color-picker v-model="titleBackground" show-alpha title="标题栏背景" />
                    </div>
                  </div>
                </el-collapse-item>

                <el-collapse-item name="font" title="组件字体样式">
                  <div class="designer-property-form">
                    <label>标题字号</label>
                    <div class="designer-inline">
                      <el-input-number
                        v-model="titleFontSize"
                        :min="10"
                        :max="48"
                        controls-position="right"
                        style="flex: 1; min-width: 0"
                      />
                      <el-color-picker v-model="titleColor" show-alpha title="标题颜色" />
                    </div>
                    <label>标题对齐</label>
                    <el-radio-group v-model="titleAlign" size="small" class="designer-config-radio">
                      <el-radio-button value="left">左对齐</el-radio-button>
                      <el-radio-button value="center">居中</el-radio-button>
                      <el-radio-button value="right">右对齐</el-radio-button>
                    </el-radio-group>
                    <div class="designer-inline">
                      <span class="designer-settings-label-inline">标题字距</span>
                      <el-input-number
                        v-model="titleLetterSpacing"
                        :min="0"
                        :max="8"
                        controls-position="right"
                        style="flex: 1; min-width: 0"
                      />
                    </div>
                    <label>内容区内边距</label>
                    <el-input-number v-model="bodyPadding" :min="0" :max="32" controls-position="right" style="width: 100%" />
                    <div class="designer-hint">留空项回落到主题 token；样式只影响展示，不影响数据绑定与筛选。</div>
                  </div>
                </el-collapse-item>

                <el-collapse-item v-if="selectedWidget.type === 'echarts' && hasChartAxes" name="axisTitle" title="轴标题">
                  <div class="designer-property-form">
                    <label>横轴标题</label>
                    <el-input v-model="chartXAxisTitle" placeholder="如 月份" maxlength="30" />
                    <label>纵轴标题</label>
                    <el-input v-model="chartYAxisTitle" placeholder="如 销售额" maxlength="30" />
                    <div class="designer-inline">
                      <span class="designer-settings-label-inline">字号</span>
                      <el-input-number
                        v-model="chartAxisTitleFontSize"
                        :min="8"
                        :max="24"
                        controls-position="right"
                        style="flex: 1; min-width: 0"
                      />
                      <el-color-picker v-model="chartAxisTitleColor" show-alpha title="轴标题颜色" />
                    </div>
                    <div class="designer-hint">纵轴标题自动竖向排列；颜色留空跟随图表风格预设。</div>
                  </div>
                </el-collapse-item>

                <el-collapse-item v-if="selectedWidget.type === 'echarts'" name="series" title="系列">
                  <div class="designer-property-form">
                    <label>图表风格</label>
                    <el-select v-model="chartStylePreset" style="width: 100%">
                      <el-option value="default" label="默认（跟随主题）" />
                      <el-option value="tech" label="科技风" />
                      <el-option value="minimal" label="简约" />
                      <el-option value="dark" label="深色" />
                    </el-select>
                    <template v-if="chartStylePreset !== 'default' && ['bar', 'line', 'area'].includes(chartType)">
                      <el-checkbox v-model="chartStyleGradient" class="designer-chart-option">渐变填充</el-checkbox>
                      <el-checkbox v-model="chartStyleGlow" class="designer-chart-option">线条发光</el-checkbox>
                    </template>
                    <el-checkbox v-model="chartDataLabelVisible" class="designer-chart-option" @change="commitHistory">
                      {{ chartType === 'map' ? '显示区域名称' : '数据标签' }}
                    </el-checkbox>
                    <div v-if="chartDataLabelVisible && chartType !== 'map'" class="designer-inline">
                      <span class="designer-settings-label-inline">标签字号</span>
                      <el-input-number
                        v-model="chartLabelFontSize"
                        :min="8"
                        :max="24"
                        controls-position="right"
                        style="flex: 1; min-width: 0"
                      />
                    </div>
                    <div v-if="chartType === 'bar'" class="designer-inline">
                      <span class="designer-settings-label-inline">柱条圆角</span>
                      <el-input-number
                        v-model="chartBarRadius"
                        :min="0"
                        :max="16"
                        controls-position="right"
                        style="flex: 1; min-width: 0"
                      />
                    </div>
                    <el-checkbox v-if="chartType === 'map'" v-model="chartMapRoam" class="designer-chart-option" @change="commitHistory">
                      允许缩放拖动
                    </el-checkbox>
                    <div class="designer-hint">风格预设只填充未显式配置的样式；坐标轴、图例与标签以本页开关为准。</div>
                  </div>
                </el-collapse-item>

                <el-collapse-item v-if="selectedWidget.type === 'echarts' && chartType !== 'map'" name="legend" title="图例">
                  <div class="designer-property-form">
                    <el-checkbox v-model="chartLegendVisible" class="designer-chart-option" @change="commitHistory">显示图例</el-checkbox>
                    <template v-if="chartLegendVisible">
                      <label>位置</label>
                      <el-radio-group v-model="chartLegendPosition" size="small" class="designer-config-radio">
                        <el-radio-button value="top">上</el-radio-button>
                        <el-radio-button value="bottom">下</el-radio-button>
                        <el-radio-button value="left">左</el-radio-button>
                        <el-radio-button value="right">右</el-radio-button>
                      </el-radio-group>
                      <div class="designer-inline">
                        <span class="designer-settings-label-inline">字号</span>
                        <el-input-number
                          v-model="chartLegendFontSize"
                          :min="8"
                          :max="24"
                          controls-position="right"
                          style="flex: 1; min-width: 0"
                        />
                      </div>
                    </template>
                  </div>
                </el-collapse-item>

                <el-collapse-item v-if="selectedWidget.type === 'echarts' && hasChartAxes" name="xAxis" title="横轴">
                  <div class="designer-property-form">
                    <el-checkbox v-model="chartXAxisVisible" class="designer-chart-option" @change="commitHistory">显示横轴</el-checkbox>
                    <div class="designer-inline">
                      <span class="designer-settings-label-inline">标签字号</span>
                      <el-input-number
                        v-model="chartXAxisLabelFontSize"
                        :min="8"
                        :max="24"
                        controls-position="right"
                        style="flex: 1; min-width: 0"
                      />
                    </div>
                  </div>
                </el-collapse-item>

                <el-collapse-item v-if="selectedWidget.type === 'echarts' && hasChartAxes" name="yAxis" title="纵轴">
                  <div class="designer-property-form">
                    <el-checkbox v-model="chartYAxisVisible" class="designer-chart-option" @change="commitHistory">显示纵轴</el-checkbox>
                    <div class="designer-inline">
                      <span class="designer-settings-label-inline">标签字号</span>
                      <el-input-number
                        v-model="chartYAxisLabelFontSize"
                        :min="8"
                        :max="24"
                        controls-position="right"
                        style="flex: 1; min-width: 0"
                      />
                    </div>
                  </div>
                </el-collapse-item>

                <el-collapse-item v-if="selectedWidget.type === 'echarts' && hasChartAxes" name="grid" title="网格线与刻度标记">
                  <div class="designer-property-form">
                    <el-checkbox v-model="chartGridLinesVisible" class="designer-chart-option" @change="commitHistory"
                      >显示网格线</el-checkbox
                    >
                    <template v-if="chartGridLinesVisible">
                      <label>网格线样式</label>
                      <el-select v-model="chartGridLineStyle" style="width: 100%">
                        <el-option value="solid" label="实线" />
                        <el-option value="dashed" label="虚线" />
                        <el-option value="dotted" label="点线" />
                      </el-select>
                    </template>
                    <el-checkbox v-model="chartAxisTick" class="designer-chart-option" @change="commitHistory">显示刻度标记</el-checkbox>
                    <div class="designer-inline">
                      <span class="designer-settings-label-inline">轴线颜色</span>
                      <el-color-picker v-model="chartAxisLineColor" show-alpha />
                    </div>
                  </div>
                </el-collapse-item>

                <el-collapse-item v-if="selectedWidget.type === 'kpi'" name="kpi" title="KPI 数值">
                  <div class="designer-property-form">
                    <label>标签（留空用组件标题）</label>
                    <el-input v-model="kpiLabel" placeholder="如 学生总数" />
                    <label>图标</label>
                    <el-select v-model="kpiIcon" filterable allow-create clearable placeholder="选择图标（可留空）" style="width: 100%">
                      <el-option v-for="icon in titleIconOptions" :key="icon" :label="icon" :value="icon" />
                    </el-select>
                    <label>布局</label>
                    <el-radio-group v-model="kpiLayout" size="small" class="designer-config-radio">
                      <el-radio-button value="label-top">标签在上</el-radio-button>
                      <el-radio-button value="label-left">标签在左</el-radio-button>
                      <el-radio-button value="value-only">仅数值</el-radio-button>
                    </el-radio-group>
                    <div class="designer-inline">
                      <span class="designer-settings-label-inline">字号</span>
                      <el-input-number
                        v-model="kpiValueFontSize"
                        :min="12"
                        :max="120"
                        controls-position="right"
                        style="flex: 1; min-width: 0"
                      />
                      <el-color-picker v-model="kpiValueColor" show-alpha />
                    </div>
                    <label>数字字体</label>
                    <el-select v-model="kpiValueFontFamily" style="width: 100%">
                      <el-option value="system" label="跟随主题" />
                      <el-option value="number-digital" label="Orbitron（科技）" />
                      <el-option value="number-condensed" label="Rajdhani（窄体）" />
                    </el-select>
                    <div class="designer-inline">
                      <el-checkbox v-model="kpiValueBold">加粗</el-checkbox>
                      <span class="designer-settings-label-inline">字距</span>
                      <el-input-number
                        v-model="kpiValueLetterSpacing"
                        :min="0"
                        :max="8"
                        controls-position="right"
                        style="flex: 1; min-width: 0"
                      />
                    </div>
                    <label>单位</label>
                    <div class="designer-inline">
                      <el-input v-model="kpiUnitText" placeholder="如 人" style="width: 88px" />
                      <el-input-number
                        v-model="kpiUnitFontSize"
                        :min="10"
                        :max="40"
                        controls-position="right"
                        style="flex: 1; min-width: 0"
                      />
                      <el-color-picker v-model="kpiUnitColor" show-alpha />
                    </div>
                    <div class="designer-inline">
                      <el-checkbox v-model="kpiAnimate">数值滚动动画</el-checkbox>
                      <span class="designer-settings-label-inline">小数位</span>
                      <el-input-number v-model="kpiDecimals" :min="0" :max="6" controls-position="right" style="width: 86px" />
                    </div>
                    <div class="designer-hint">单位留空时沿用「格式」中的单位，并按数值字号渲染。</div>
                  </div>
                </el-collapse-item>

                <el-collapse-item v-if="selectedWidget.type === 'echarts'" name="advanced" title="高级配置（ECharts option）">
                  <div class="designer-property-form">
                    <div class="designer-hint">
                      直接编辑该组件的 ECharts option，可配置 tooltip、legend、grid、坐标轴样式和系列样式。字段映射与数据仍由基础配置控制。
                    </div>
                    <el-alert v-if="customOptionError" type="error" :title="customOptionError" :closable="false" class="mb-2" />
                    <textarea v-model="customOptionText" class="designer-json designer-json--compact" spellcheck="false"></textarea>
                    <div class="designer-inline">
                      <el-button type="primary" @click="applyCustomOption">应用配置</el-button>
                      <el-button @click="resetCustomOption">恢复默认</el-button>
                    </div>
                  </div>
                </el-collapse-item>
              </el-collapse>

              <div class="designer-config-footer">
                <el-button size="small" @click="resetWidgetStyle">恢复默认皮肤</el-button>
                <div class="designer-hint">样式只影响展示，不影响数据绑定与筛选；留空项回落到主题 token。</div>
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
              <template v-else-if="isMetricDateRangeTarget(target)">
                <div class="designer-filter-target-field">
                  <el-select
                    :model-value="filterTargetFieldValue(target)"
                    clearable
                    filterable
                    allow-create
                    placeholder="选择指标时间范围或字段"
                    @update:model-value="value => onFilterTargetFieldSelect(target, String(value))"
                  >
                    <el-option
                      v-for="field in fieldCandidates(target.datasetId)"
                      :key="field.name"
                      :label="field.label || field.name"
                      :value="field.name"
                    />
                    <el-option :value="METRIC_TIME_OPTION" label="指标时间范围（事实表时间）" />
                  </el-select>
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
            <div class="designer-hint">
              SQL 参数目标表示数据集 SQL 中的
              ${参数名}；普通字段目标可选择服务端或客户端过滤。指标数据集的日期范围可选择“指标时间范围”（事实表时间），或直接绑定已有结果字段。
            </div>

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

            <label>{{ filterDefaultHint.label }}</label>
            <el-input v-model="filterDefaultDraft" :placeholder="filterDefaultHint.placeholder" @change="commitFilterDefault" />

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

/* 属性面板承载组件配置，略宽于左侧组件库，保证手风琴分组内的表单可读。 */
.designer-properties {
  width: 306px;
  min-width: 288px;
  padding: 14px 14px 18px;
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

/* 组件配置头部：组件名 + 数据集，对齐参考稿的抽屉标题。 */
.designer-inspector-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 2px 0 12px;
  border-bottom: 1px solid #ebeef5;
}

.designer-inspector-heading {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.designer-inspector-title {
  color: #303133;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.3;
}

.designer-inspector-sub {
  overflow: hidden;
  color: #909399;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  padding: 0 14px;
  font-size: 13px;
}

.designer-property-tabs :deep(.el-tabs__item.is-active) {
  font-weight: 600;
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

/* 自定义配置：参考设计稿的分组手风琴，去掉卡片感，仅保留分隔线。 */
.designer-config-collapse {
  --el-collapse-border-color: transparent;
  --el-collapse-header-height: 48px;

  margin-top: 2px;
}

.designer-config-collapse :deep(.el-collapse-item__header) {
  height: 48px;
  padding: 0 2px;
  color: #303133;
  font-size: 14px;
  font-weight: 600;
  line-height: 48px;
  background: transparent;
  border-bottom: 1px solid #ebeef5;
}

.designer-config-collapse :deep(.el-collapse-item__arrow) {
  margin: 0 2px 0 auto;
  color: #909399;
  font-size: 12px;
  font-weight: 400;
}

.designer-config-collapse :deep(.el-collapse-item__wrap) {
  background: transparent;
  border-bottom: 1px solid #ebeef5;
}

.designer-config-collapse :deep(.el-collapse-item__content) {
  padding: 2px 2px 16px;
  color: #606266;
  font-size: 12px;
}

.designer-config-collapse :deep(.el-collapse-item__content > .designer-property-form) {
  margin-top: 4px;
}

.designer-config-subtitle {
  margin: 14px 0 2px;
  padding-bottom: 6px;
  color: #303133;
  font-size: 12px;
  font-weight: 600;
  border-bottom: 1px dashed #ebeef5;
}

.designer-config-subtitle:first-child {
  margin-top: 0;
}

.designer-config-collapse .designer-chart-option {
  margin-top: 6px;
}

.designer-config-radio {
  display: flex;
  width: 100%;
}

.designer-config-radio :deep(.el-radio-button) {
  flex: 1;
  min-width: 0;
}

.designer-config-radio :deep(.el-radio-button__inner) {
  width: 100%;
  padding: 7px 4px;
  font-size: 12px;
}

.designer-config-footer {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #ebeef5;
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

.designer-format-rule-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.designer-format-rule {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
}

.designer-format-rule :deep(.el-checkbox__label) {
  padding-left: 4px;
  font-size: 12px;
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

.designer-map-layer {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  background: #f7f9fc;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}
</style>
