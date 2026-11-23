<template>
  <div class="data-query-container">
    <div class="query-header">
      <div class="header-left">
        <router-link :to="{ name: 'DataSource' }" class="back-link">
          <font-awesome-icon icon="arrow-left" />
          <span>返回</span>
        </router-link>
        <h3 class="query-title">
          数据查询 <span v-if="dataSourceName" class="ds-name">({{ dataSourceName }})</span>
        </h3>
      </div>
      <div class="header-right">
        <el-dropdown
          trigger="click"
          placement="bottom-end"
          :disabled="!hasAvailableMaintenanceAction(catalogMaintenanceActions)"
          @command="openCatalogMaintenance"
        >
          <el-button
            size="small"
            text
            type="warning"
            class="maintenance-dropdown-trigger"
            :disabled="!hasAvailableMaintenanceAction(catalogMaintenanceActions)"
            :title="
              hasAvailableMaintenanceAction(catalogMaintenanceActions)
                ? 'Catalog 级运维操作（作用于当前表的 Catalog）'
                : '请先打开表预览，且当前 Catalog 支持运维操作'
            "
          >
            <font-awesome-icon icon="database" />
            <span>Catalog 运维</span>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="action in catalogMaintenanceActions"
                :key="action.id"
                :command="action"
                :disabled="action.available === false"
              >
                <span class="maintenance-menu-item" :title="action.available === false ? action.unavailableReason : action.description">
                  <font-awesome-icon :icon="action.danger ? 'exclamation-circle' : 'wrench'" class="maintenance-menu-icon" />
                  <span>{{ action.label || action.id }}</span>
                  <span v-if="action.available === false" class="maintenance-menu-unavailable">暂不支持</span>
                </span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <div class="query-body">
      <div class="query-sidebar">
        <div class="sidebar-header">
          <span>数据库</span>
          <el-tooltip content="刷新目录" placement="bottom">
            <button class="sidebar-refresh-btn" :disabled="treeLoading" @click="loadSchemas">
              <font-awesome-icon icon="sync" :spin="treeLoading" />
            </button>
          </el-tooltip>
        </div>
        <div class="sidebar-search">
          <el-input
            v-model="tableSearch"
            size="small"
            clearable
            placeholder="搜索表名（需先展开 schema）"
            @input="onTableSearchInput"
            @clear="onTableSearchClear"
          >
            <template #prefix>
              <font-awesome-icon icon="search" />
            </template>
            <template #suffix>
              <font-awesome-icon v-if="tableSearching" icon="sync" spin class="searching-icon" />
            </template>
          </el-input>
          <div v-if="tableSearchHint" class="sidebar-search-hint">{{ tableSearchHint }}</div>
        </div>
        <div class="sidebar-content" v-loading="treeLoading">
          <div v-if="treeError && !treeLoading" class="tree-error">
            <font-awesome-icon icon="exclamation-circle" class="tree-error-icon" />
            <div class="tree-error-title">数据库加载失败</div>
            <div class="tree-error-message" :title="treeError">{{ treeError }}</div>
            <el-button size="small" type="primary" plain @click="loadSchemas">重试</el-button>
          </div>
          <template v-else>
            <el-tree
              ref="treeRef"
              :data="treeData"
              :props="{ label: 'label', children: 'children', isLeaf: 'isLeaf' }"
              node-key="id"
              lazy
              :load="loadTables"
              highlight-current
              class="schema-tree"
              @node-expand="handleNodeExpand"
              @node-collapse="handleNodeCollapse"
            >
              <template #default="{ node, data }">
                <span v-if="data?.type === 'more'" class="tree-more-node" :title="data.label" @click.stop="handleLoadMore(data)">
                  <font-awesome-icon icon="plus" class="node-icon" />
                  <span class="node-label">{{ data.label }}</span>
                </span>
                <span v-else-if="data?.type === 'hint'" class="tree-hint-node" :title="data.label">
                  <font-awesome-icon icon="circle-question" class="node-icon hint-icon" />
                  <span class="node-label">{{ data.label }}</span>
                </span>
                <span
                  v-else
                  class="custom-tree-node"
                  :class="{ 'is-table': data?.type === 'table' }"
                  @mouseenter="hoveredNodeId = data?.id || ''"
                  @mouseleave="hoveredNodeId = ''"
                  @dblclick.stop="handleNodeDblClick(data)"
                  @contextmenu.stop.prevent="openContextMenu($event, data)"
                >
                  <font-awesome-icon
                    :icon="data?.type === 'table' ? 'table' : data?.type === 'schema' ? 'cubes' : 'database'"
                    class="node-icon"
                  />
                  <span
                    class="node-label"
                    :title="data?.type === 'table' ? [data.catalog, data.schema, data.name].filter(Boolean).join('.') : node.label"
                  >
                    {{ node.label }}
                  </span>
                  <span v-if="data?.type === 'table' && hoveredNodeId === data.id" class="node-actions" @click.stop>
                    <button class="node-action-btn" title="预览数据" @click.stop="previewTable(data)">
                      <font-awesome-icon icon="eye" />
                    </button>
                    <button class="node-action-btn" title="更多操作" @click.stop="openContextMenu($event, data)">
                      <font-awesome-icon icon="ellipsis-vertical" />
                    </button>
                  </span>
                </span>
              </template>
            </el-tree>
            <el-empty v-if="!treeLoading && treeData.length === 0" description="暂无数据库" />
          </template>
        </div>
      </div>

      <div class="query-main">
        <el-tabs v-model="activeTab" type="card" class="query-tabs" @tab-remove="handleTabRemove">
          <el-tab-pane name="sql" :closable="false">
            <template #label>
              <span class="tab-label">
                <font-awesome-icon icon="code" />
                <span>SQL 查询</span>
              </span>
            </template>
            <div class="sql-pane">
              <div class="sql-editor-section" :style="{ height: editorHeight + 'px' }">
                <div class="section-header">
                  <div class="section-actions">
                    <el-button type="primary" size="small" text :loading="isExecuting" @click="executeQuery">
                      <font-awesome-icon icon="play" class="mr-1" />
                      <span>执行</span>
                    </el-button>
                    <el-button size="small" text @click="formatSql" class="format-btn" title="格式化 SQL (Ctrl+Shift+F)">
                      <font-awesome-icon icon="indent" />
                      <span>格式化</span>
                    </el-button>
                    <el-tooltip content="将当前 SQL 快速生成可复用的分析数据集" placement="bottom">
                      <el-button size="small" text @click="generateDataset" class="dataset-entry-btn">
                        <font-awesome-icon icon="database" class="mr-1" />
                        <span>生成数据集</span>
                      </el-button>
                    </el-tooltip>
                    <el-tooltip content="AI 助手：用自然语言生成 SQL" placement="bottom">
                      <el-button size="small" text @click="aiDrawerVisible = true" class="ai-entry-btn">
                        <font-awesome-icon icon="wand-magic-sparkles" class="mr-1" />
                        <span>AI 助手</span>
                      </el-button>
                    </el-tooltip>
                  </div>
                  <el-select
                    v-model="queryLimit"
                    size="small"
                    class="query-limit-select"
                    title="单次查询最多返回的行数，避免无条数限制的全量查询"
                  >
                    <el-option v-for="opt in queryLimitOptions" :key="opt" :value="opt" :label="`最多 ${opt} 行`" />
                  </el-select>
                </div>
                <div ref="editorContainer" class="sql-editor"></div>
              </div>

              <div class="resizer" :class="{ dragging: isDragging }" @mousedown="startResize">
                <div class="resizer-handle"></div>
              </div>

              <div class="result-section">
                <el-tabs v-model="activeResultTab" class="result-tabs">
                  <el-tab-pane label="查询结果" name="result">
                    <div class="result-content query-result-content">
                      <div v-if="resultTruncated" class="truncate-hint">
                        <font-awesome-icon icon="circle-info" />
                        <span>结果已截断，仅返回前 {{ resultRowLimit }} 行</span>
                      </div>
                      <div class="result-table-wrap">
                        <el-table
                          v-if="resultColumns.length > 0"
                          :data="resultData"
                          stripe
                          border
                          style="width: 100%"
                          height="100%"
                          size="small"
                        >
                          <el-table-column
                            v-for="col in resultColumns"
                            :key="col"
                            :prop="col"
                            :label="col"
                            show-overflow-tooltip
                            min-width="120"
                          />
                          <template #empty>
                            <el-empty description="查询成功，无数据返回" />
                          </template>
                        </el-table>
                        <el-empty v-else description="请执行 SQL 查询" />
                      </div>
                    </div>
                  </el-tab-pane>
                  <el-tab-pane label="执行日志" name="log">
                    <div class="result-content log-content">
                      <template v-if="hasExecuted">
                        <div class="log-item">
                          <span class="log-label">执行状态</span>
                          <el-tag size="small" :type="executionStatusType" effect="light">
                            {{ executionStatusLabel }}
                          </el-tag>
                        </div>
                        <div class="log-item">
                          <span class="log-label">耗时</span>
                          <span class="log-value">{{ executionTime !== null ? formatTime(executionTime) : '-' }}</span>
                        </div>
                        <div class="log-item">
                          <span class="log-label">影响行数</span>
                          <span class="log-value">{{ affectedRows !== null ? affectedRows : '-' }}</span>
                        </div>
                        <div class="log-block">
                          <span class="log-label">执行 SQL</span>
                          <pre class="log-sql">{{ executedSql }}</pre>
                        </div>
                        <div v-if="queryError" class="log-error">
                          <font-awesome-icon icon="exclamation-circle" />
                          <span>{{ queryError }}</span>
                        </div>
                      </template>
                      <el-empty v-else description="暂无执行日志" />
                    </div>
                  </el-tab-pane>
                </el-tabs>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane v-for="tab in previewTabs" :key="tab.key" :name="tab.key" closable>
            <template #label>
              <span class="tab-label">
                <font-awesome-icon icon="table" />
                <span>{{ tab.table }}</span>
              </span>
            </template>
            <div class="preview-pane">
              <div class="preview-toolbar">
                <div class="preview-meta">
                  <span class="preview-table-name">{{ tab.catalog ? tab.catalog + '.' : '' }}{{ tab.schema }}.{{ tab.table }}</span>
                  <span v-if="tab.metaLoading || tab.loading" class="preview-status">加载中…</span>
                  <span v-else-if="tab.metaError" class="error-tag">
                    <font-awesome-icon icon="exclamation-circle" />
                    <span>{{ tab.metaError }}</span>
                  </span>
                  <span v-else class="preview-status">{{ tab.rows.length }} 行 · {{ tab.columns.length }} 列</span>
                </div>
                <div class="preview-actions">
                  <el-select v-model="tab.limit" size="small" class="limit-select" @change="loadPreview(tab)">
                    <el-option :value="10" label="10 行" />
                    <el-option :value="100" label="100 行" />
                    <el-option :value="500" label="500 行" />
                    <el-option :value="1000" label="1000 行" />
                  </el-select>
                  <el-dropdown
                    v-if="hasAvailableMaintenanceAction(tableMaintenanceActions(tab))"
                    trigger="click"
                    placement="bottom-end"
                    @command="action => openMaintenance(tab, action)"
                  >
                    <el-button size="small" text class="maintenance-dropdown-trigger" title="表级运维操作">
                      <font-awesome-icon icon="wrench" />
                      <span>表运维</span>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item
                          v-for="action in tableMaintenanceActions(tab)"
                          :key="action.id"
                          :command="action"
                          :disabled="action.available === false"
                        >
                          <span
                            class="maintenance-menu-item"
                            :title="action.available === false ? action.unavailableReason : action.description"
                          >
                            <font-awesome-icon :icon="action.danger ? 'exclamation-circle' : 'wrench'" class="maintenance-menu-icon" />
                            <span>{{ action.label || action.id }}</span>
                            <span v-if="action.available === false" class="maintenance-menu-unavailable">暂不支持</span>
                          </span>
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                  <el-button size="small" text :loading="tab.metaLoading || tab.loading" @click="refreshPreview(tab)">
                    <font-awesome-icon icon="sync" />
                    <span>刷新</span>
                  </el-button>
                  <el-button size="small" text @click="copyPreviewSelect(tab)">
                    <font-awesome-icon icon="copy" />
                    <span>复制 SELECT</span>
                  </el-button>
                  <el-button size="small" text @click="openSelectInEditor(tab)">
                    <font-awesome-icon icon="code" />
                    <span>在查询页打开</span>
                  </el-button>
                </div>
              </div>

              <div class="preview-basic" v-loading="tab.metaLoading">
                <el-descriptions v-if="tab.detail" :column="3" border size="small">
                  <el-descriptions-item label="表名">{{ tab.detail.table || tab.table }}</el-descriptions-item>
                  <el-descriptions-item label="Schema">{{ tab.detail.schema || tab.schema || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="目录">{{ tab.detail.catalog || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="数据库类型">{{ tab.detail.dbType || dataSourceType || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="字段数">{{ tab.detail.columns?.length ?? 0 }}</el-descriptions-item>
                  <el-descriptions-item label="索引数">{{ tab.detail.indexes?.length ?? 0 }}</el-descriptions-item>
                  <el-descriptions-item label="表注释" :span="3">{{ tab.detail.comment || '-' }}</el-descriptions-item>
                </el-descriptions>
                <span v-else-if="tab.metaError" class="error-tag">
                  <font-awesome-icon icon="exclamation-circle" />
                  <span>{{ tab.metaError }}</span>
                </span>
                <span v-else-if="!tab.metaLoading" class="preview-status">暂无表信息</span>
              </div>

              <el-tabs v-model="tab.subTab" class="preview-subtabs" @tab-change="handlePreviewSubTabChange(tab, $event)">
                <el-tab-pane label="字段信息" name="columns">
                  <div class="preview-section" v-loading="tab.metaLoading">
                    <el-table :data="tab.detail?.columns || []" stripe border size="small" style="width: 100%">
                      <el-table-column prop="name" label="字段名" min-width="140" show-overflow-tooltip />
                      <el-table-column label="类型" min-width="120" show-overflow-tooltip>
                        <template #default="{ row }">{{ formatColumnType(row) }}</template>
                      </el-table-column>
                      <el-table-column label="可空" width="70" align="center">
                        <template #default="{ row }">{{ row.nullable === false ? '否' : '是' }}</template>
                      </el-table-column>
                      <el-table-column label="主键" width="70" align="center">
                        <template #default="{ row }">
                          <font-awesome-icon v-if="row.primaryKey" icon="check" class="pk-icon" />
                          <span v-else>-</span>
                        </template>
                      </el-table-column>
                      <el-table-column prop="defaultValue" label="默认值" min-width="100" show-overflow-tooltip />
                      <el-table-column prop="comment" label="注释" min-width="140" show-overflow-tooltip />
                      <template #empty>
                        <el-empty description="暂无字段信息" />
                      </template>
                    </el-table>
                  </div>
                </el-tab-pane>

                <el-tab-pane label="健康监控" name="health">
                  <div class="preview-section table-health-section" v-loading="tab.healthLoading">
                    <div v-if="tab.healthError" class="error-tag">
                      <font-awesome-icon icon="exclamation-circle" />
                      <span>{{ tab.healthError }}</span>
                    </div>
                    <template v-else-if="tab.health">
                      <template v-if="tab.health.supported">
                        <div class="health-overview">
                          <div class="health-score-box">
                            <el-progress
                              type="dashboard"
                              :percentage="tab.health.healthScore ?? 0"
                              :width="96"
                              :stroke-width="8"
                              :color="healthScoreColor(tab.health.healthScore)"
                            >
                              <template #default="{ percentage }">
                                <div class="health-score-value">{{ percentage }}</div>
                                <div class="health-score-label">健康分</div>
                              </template>
                            </el-progress>
                          </div>
                          <div class="health-overview-main">
                            <div class="health-overview-title">
                              <el-tag :type="healthStatusTagType(tab.health.status)" effect="dark" size="small">
                                {{ healthStatusText(tab.health.status) }}
                              </el-tag>
                              <span class="health-overview-format">{{ tab.health.format || 'ICEBERG' }}</span>
                              <span class="preview-status">最近更新：{{ formatTimestamp(tab.health.lastUpdatedMs) }}</span>
                              <span v-if="tab.health.lastOperation" class="preview-status"> 最近操作：{{ tab.health.lastOperation }} </span>
                            </div>
                            <div class="health-location" :title="tab.health.location || ''">{{ tab.health.location || '-' }}</div>
                            <div v-if="tab.health.warnings?.length" class="health-warnings">
                              <el-alert
                                v-for="(warning, index) in tab.health.warnings"
                                :key="index"
                                :title="warning"
                                :type="tab.health.status === 'CRITICAL' ? 'error' : 'warning'"
                                :closable="false"
                                show-icon
                              />
                            </div>
                            <div v-else class="health-ok">
                              <font-awesome-icon icon="check-circle" />
                              <span>未发现明显的存储健康问题</span>
                            </div>
                          </div>
                        </div>

                        <div class="health-cards">
                          <div class="health-card">
                            <div class="health-card-label">数据文件</div>
                            <div class="health-card-value">{{ formatCount(tab.health.summary?.dataFiles) }}</div>
                            <div class="health-card-sub">{{ formatBytes(tab.health.summary?.dataSizeBytes) }}</div>
                          </div>
                          <div class="health-card" :class="{ 'is-warning': (tab.health.summary?.smallFiles ?? 0) > 0 }">
                            <div class="health-card-label">小文件</div>
                            <div class="health-card-value">{{ formatCount(tab.health.summary?.smallFiles) }}</div>
                            <div class="health-card-sub">阈值 {{ formatBytes(tab.health.summary?.smallFileThresholdBytes) }}</div>
                          </div>
                          <div class="health-card" :class="{ 'is-danger': (tab.health.summary?.deleteFiles ?? 0) > 0 }">
                            <div class="health-card-label">删除文件</div>
                            <div class="health-card-value">{{ formatCount(tab.health.summary?.deleteFiles) }}</div>
                            <div class="health-card-sub">{{ formatBytes(tab.health.summary?.deleteSizeBytes) }}</div>
                          </div>
                          <div class="health-card">
                            <div class="health-card-label">分区</div>
                            <div class="health-card-value">{{ formatCount(tab.health.summary?.partitionCount) }}</div>
                            <div class="health-card-sub">{{ tab.health.partitioning?.partitioned ? '已分区' : '未分区' }}</div>
                          </div>
                          <div class="health-card">
                            <div class="health-card-label">快照</div>
                            <div class="health-card-value">{{ formatCount(tab.health.summary?.snapshotCount) }}</div>
                            <div class="health-card-sub">展示 {{ tab.health.snapshots?.length ?? 0 }} 条</div>
                          </div>
                          <div v-if="(tab.health.summary?.dataFiles ?? 0) > 0" class="health-card">
                            <div class="health-card-label">平均文件大小</div>
                            <div class="health-card-value">{{ formatBytes(tab.health.summary?.avgFileSizeBytes) }}</div>
                            <div class="health-card-sub">推荐 ≥ 128 MB</div>
                          </div>
                          <div v-if="tab.health.format === 'DUCKLAKE'" class="health-card">
                            <div class="health-card-label">内联数据</div>
                            <div class="health-card-value">{{ formatCount(tab.health.summary?.inlinedRows) }}</div>
                            <div class="health-card-sub">内联删除 {{ formatCount(tab.health.summary?.inlinedDeleteRows) }} 条</div>
                          </div>
                        </div>

                        <el-descriptions class="health-descriptions" :column="3" border size="small">
                          <el-descriptions-item label="表位置" :span="3">
                            <span class="health-location-text">{{ tab.health.location || '-' }}</span>
                          </el-descriptions-item>
                          <template v-if="tab.health.format === 'ICEBERG'">
                            <el-descriptions-item label="当前元数据文件">
                              {{ formatBytes(tab.health.summary?.currentMetadataSizeBytes) }}
                            </el-descriptions-item>
                            <el-descriptions-item label="元数据版本">
                              {{ formatCount(tab.health.summary?.metadataFileCount) }} 个 /
                              {{ formatBytes(tab.health.summary?.metadataSizeBytes) }}
                            </el-descriptions-item>
                            <el-descriptions-item label="Manifest List">
                              {{ formatBytes(tab.health.summary?.manifestListSizeBytes) }}
                            </el-descriptions-item>
                            <el-descriptions-item label="Manifest 文件">
                              {{ formatCount(tab.health.summary?.manifestFileCount) }} 个 /
                              {{ formatBytes(tab.health.summary?.manifestSizeBytes) }}
                            </el-descriptions-item>
                          </template>
                          <template v-else>
                            <el-descriptions-item label="Catalog 类型">
                              {{ tab.health.catalogType || 'duckdb' }}
                            </el-descriptions-item>
                            <el-descriptions-item label="元数据格式">
                              {{ tab.health.metadataVersion || '-' }}
                            </el-descriptions-item>
                            <el-descriptions-item label="元数据存储">
                              {{ formatBytes(tab.health.summary?.metadataSizeBytes) }}
                            </el-descriptions-item>
                            <el-descriptions-item label="数据路径" :span="3">
                              <span class="health-location-text">{{ tab.health.dataPath || '-' }}</span>
                            </el-descriptions-item>
                            <el-descriptions-item label="内联数据">
                              {{ formatCount(tab.health.summary?.inlinedRows) }} 行
                            </el-descriptions-item>
                            <el-descriptions-item label="内联删除">
                              {{ formatCount(tab.health.summary?.inlinedDeleteRows) }} 条
                            </el-descriptions-item>
                            <el-descriptions-item label="元数据文件数">
                              {{ formatCount(tab.health.summary?.metadataFileCount) }} 个
                            </el-descriptions-item>
                          </template>
                          <el-descriptions-item label="数据文件总量">
                            {{ formatCount(tab.health.summary?.totalFiles) }} 个 /
                            {{ formatBytes(tab.health.summary?.totalSizeBytes) }}
                          </el-descriptions-item>
                          <el-descriptions-item label="总行数">
                            {{ formatCount(tab.health.summary?.totalRecords) }}
                          </el-descriptions-item>
                        </el-descriptions>

                        <div class="health-block">
                          <div class="health-block-title">分区信息</div>
                          <el-table
                            v-if="tab.health.partitioning?.fields?.length"
                            :data="tab.health.partitioning?.fields || []"
                            stripe
                            border
                            size="small"
                          >
                            <el-table-column prop="name" label="分区字段" min-width="180" show-overflow-tooltip />
                            <el-table-column prop="transform" label="转换" width="120" />
                            <el-table-column prop="sourceId" label="源字段 ID" width="110" align="center" />
                            <el-table-column prop="specId" label="Spec" width="80" align="center" />
                          </el-table>
                          <el-empty v-else description="未分区" :image-size="56" />
                        </div>

                        <div v-if="tab.health.format === 'ICEBERG'" class="health-block">
                          <div class="health-block-title">
                            Manifest 清单
                            <span v-if="tab.health.manifestsTruncated" class="preview-status">
                              仅展示前 {{ tab.health.manifests?.length ?? 0 }} 个
                            </span>
                          </div>
                          <el-table :data="tab.health.manifests || []" stripe border size="small" max-height="260">
                            <el-table-column label="类型" width="90">
                              <template #default="{ row }">
                                <el-tag size="small" :type="row.contentType === 'DELETES' ? 'danger' : 'success'">
                                  {{ row.contentType === 'DELETES' ? '删除' : '数据' }}
                                </el-tag>
                              </template>
                            </el-table-column>
                            <el-table-column prop="path" label="Manifest 文件" min-width="280" show-overflow-tooltip />
                            <el-table-column label="大小" width="100">
                              <template #default="{ row }">{{ formatBytes(row.sizeBytes) }}</template>
                            </el-table-column>
                            <el-table-column label="新增 / 存在 / 删除" width="150" align="center">
                              <template #default="{ row }">
                                {{ formatCount(row.addedFiles) }} / {{ formatCount(row.existingFiles) }} /
                                {{ formatCount(row.deletedFiles) }}
                              </template>
                            </el-table-column>
                            <el-table-column label="新增行数" width="100" align="right">
                              <template #default="{ row }">{{ formatCount(row.addedRows) }}</template>
                            </el-table-column>
                          </el-table>
                        </div>

                        <div class="health-block">
                          <div class="health-block-title">
                            数据文件（按大小升序）
                            <span v-if="tab.health.filesTruncated" class="preview-status">
                              仅展示最小的 {{ tab.health.files?.length ?? 0 }} 个
                            </span>
                          </div>
                          <el-table :data="tab.health.files || []" stripe border size="small" max-height="300">
                            <el-table-column label="类型" width="100">
                              <template #default="{ row }">
                                <el-tag size="small" :type="row.contentType === 'DATA' ? 'success' : 'danger'">
                                  {{
                                    row.contentType === 'DATA' ? '数据' : row.contentType === 'POSITION_DELETES' ? '位置删除' : '等值删除'
                                  }}
                                </el-tag>
                              </template>
                            </el-table-column>
                            <el-table-column prop="path" label="文件" min-width="340" show-overflow-tooltip />
                            <el-table-column prop="format" label="格式" width="80" />
                            <el-table-column label="大小" width="100">
                              <template #default="{ row }">{{ formatBytes(row.sizeBytes) }}</template>
                            </el-table-column>
                            <el-table-column label="记录数" width="90" align="right">
                              <template #default="{ row }">{{ formatCount(row.recordCount) }}</template>
                            </el-table-column>
                            <el-table-column prop="partition" label="分区" min-width="150" show-overflow-tooltip />
                          </el-table>
                        </div>

                        <div class="health-block">
                          <div class="health-block-title">快照</div>
                          <el-table :data="tab.health.snapshots || []" stripe border size="small" max-height="240">
                            <el-table-column prop="snapshotId" label="Snapshot ID" min-width="170" />
                            <el-table-column prop="sequenceNumber" label="序号" width="80" align="right" />
                            <el-table-column label="时间" width="170">
                              <template #default="{ row }">{{ formatTimestamp(row.timestampMs) }}</template>
                            </el-table-column>
                            <el-table-column prop="operation" label="操作" width="100" />
                            <el-table-column label="总行数" width="100" align="right">
                              <template #default="{ row }">{{ formatCount(row.totalRecords) }}</template>
                            </el-table-column>
                            <el-table-column label="数据文件" width="100" align="right">
                              <template #default="{ row }">{{ formatCount(row.totalDataFiles) }}</template>
                            </el-table-column>
                            <el-table-column label="删除文件" width="100" align="right">
                              <template #default="{ row }">{{ formatCount(row.totalDeleteFiles) }}</template>
                            </el-table-column>
                          </el-table>
                        </div>
                      </template>
                      <el-empty v-else :description="tab.health.message || '当前表不支持健康监控'" />
                    </template>
                    <el-empty v-else-if="!tab.healthLoading" description="切换到本页签以加载表健康信息" />
                  </div>
                </el-tab-pane>

                <el-tab-pane label="索引信息" name="indexes">
                  <div class="preview-section" v-loading="tab.metaLoading">
                    <el-table :data="tab.detail?.indexes || []" stripe border size="small" style="width: 100%">
                      <el-table-column prop="name" label="索引名" min-width="160" show-overflow-tooltip />
                      <el-table-column label="唯一" width="70" align="center">
                        <template #default="{ row }">{{ row.unique ? '是' : '否' }}</template>
                      </el-table-column>
                      <el-table-column prop="type" label="类型" width="100" />
                      <el-table-column label="字段" min-width="200" show-overflow-tooltip>
                        <template #default="{ row }">{{ (row.columns || []).join(', ') }}</template>
                      </el-table-column>
                      <template #empty>
                        <el-empty description="暂无索引信息" />
                      </template>
                    </el-table>
                  </div>
                </el-tab-pane>

                <el-tab-pane label="数据预览" name="data">
                  <div class="preview-data" v-loading="tab.loading">
                    <el-table v-if="tab.columns.length > 0" :data="tab.rows" stripe border style="width: 100%" height="100%" size="small">
                      <el-table-column
                        v-for="col in tab.columns"
                        :key="col"
                        :prop="col"
                        :label="col"
                        show-overflow-tooltip
                        min-width="140"
                      />
                      <template #empty>
                        <el-empty description="查询成功，无数据返回" />
                      </template>
                    </el-table>
                    <el-empty v-else-if="!tab.loading" :description="tab.error ? '数据加载失败' : '暂无数据'" />
                  </div>
                </el-tab-pane>

                <el-tab-pane label="数据血缘" name="lineage" lazy>
                  <div class="preview-section table-lineage-section">
                    <TableLineage
                      v-if="tab.subTab === 'lineage'"
                      :data-source-id="dataSourceId"
                      :schema="tab.schema || ''"
                      :table="tab.table"
                    />
                  </div>
                </el-tab-pane>
              </el-tabs>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <data-query-ai v-model="aiDrawerVisible" :data-source-id="dataSourceId" assistant-id="query" @apply-sql="applyAiSql" />

    <teleport to="body">
      <div
        v-if="contextMenu.visible"
        class="table-context-menu"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
        @click.stop
        @contextmenu.stop.prevent
      >
        <div class="ctx-header" :title="contextMenu.data?.name">{{ contextMenu.data?.name }}</div>
        <div class="ctx-item" @click="handleTableCommand('preview', contextMenu.data)">
          <font-awesome-icon icon="eye" />
          <span>预览数据</span>
        </div>
        <div class="ctx-divider"></div>
        <div class="ctx-item" @click="handleTableCommand('select', contextMenu.data)">
          <font-awesome-icon icon="code" />
          <span>复制 SELECT</span>
        </div>
        <div class="ctx-item" @click="handleTableCommand('insert', contextMenu.data)">
          <font-awesome-icon icon="pencil-alt" />
          <span>复制 INSERT</span>
        </div>
        <div class="ctx-item" @click="handleTableCommand('ddl', contextMenu.data)">
          <font-awesome-icon icon="file-code" />
          <span>复制 CREATE TABLE</span>
        </div>
        <div class="ctx-divider"></div>
        <div class="ctx-item" @click="handleTableCommand('toEditor', contextMenu.data)">
          <font-awesome-icon icon="arrow-right-to-bracket" />
          <span>在 SQL 编辑器中打开</span>
        </div>
      </div>
    </teleport>

    <el-dialog
      v-model="maintenanceDialog.visible"
      :title="maintenanceDialog.action?.label || '运维操作'"
      width="580px"
      append-to-body
      class="maintenance-dialog"
    >
      <div v-if="maintenanceDialog.action" class="maintenance-body">
        <p class="maintenance-description">{{ maintenanceDialog.action.description }}</p>
        <el-alert
          :title="maintenanceDialog.action.confirm || '是否执行该操作？'"
          :type="maintenanceDialog.action.danger ? 'warning' : 'info'"
          :closable="false"
          show-icon
        />
        <el-form v-if="maintenanceDialog.action.params?.length" label-width="140px" class="maintenance-form">
          <el-form-item v-for="param in maintenanceDialog.action.params" :key="param.name" :label="param.label || param.name">
            <el-switch v-if="param.type === 'boolean'" v-model="maintenanceDialog.params[param.name]" />
            <el-input-number
              v-else-if="param.type === 'number'"
              v-model="maintenanceDialog.params[param.name]"
              :min="param.min ?? undefined"
              :max="param.max ?? undefined"
              :step="param.step ?? 1"
            />
            <el-input v-else v-model="maintenanceDialog.params[param.name]" />
            <div v-if="param.description" class="maintenance-param-desc">{{ param.description }}</div>
          </el-form-item>
        </el-form>
        <div v-if="maintenanceDialog.result" class="maintenance-result">
          <el-alert
            :title="maintenanceDialog.result.message || (maintenanceDialog.result.success ? '操作完成' : '操作失败')"
            :type="maintenanceDialog.result.success ? 'success' : 'error'"
            :closable="false"
            show-icon
          />
          <pre v-if="maintenanceDialog.result.rows?.length" class="maintenance-result-json">{{
            formatMaintenanceRows(maintenanceDialog.result.rows)
          }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button @click="closeMaintenance">关闭</el-button>
        <el-button type="primary" :loading="maintenanceDialog.running" @click="runMaintenance">执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" src="./data-query.component.ts"></script>

<style scoped>
.data-query-container {
  height: calc(100vh - var(--app-topbar-height, 88px));
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color, #fff);
}

.query-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
  background: var(--el-bg-color, #fff);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-link {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--el-text-color-regular, #606266);
  text-decoration: none;
  cursor: pointer;
  font-size: 14px;
}

.back-link:hover {
  color: var(--el-color-primary, #409eff);
}

.query-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.ds-name {
  font-weight: normal;
  color: var(--el-text-color-regular, #606266);
  font-size: 14px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.query-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.query-sidebar {
  width: 260px;
  border-right: 1px solid var(--el-border-color-lighter, #e4e7ed);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  background: var(--el-bg-color, #fff);
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary, #303133);
}

.sidebar-refresh-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: 3px;
  background: transparent;
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
  cursor: pointer;
}

.sidebar-refresh-btn:hover:not(:disabled) {
  background: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
}

.sidebar-refresh-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.sidebar-content {
  flex: 1;
  overflow: auto;
  padding: 8px;
}

.sidebar-search {
  padding: 8px 12px 4px;
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
}

.sidebar-search :deep(.el-input__inner) {
  font-size: 12px;
}

.searching-icon {
  color: var(--el-color-primary, #409eff);
}

.sidebar-search-hint {
  padding-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary, #909399);
}

.schema-tree {
  background: transparent;
}

.tree-more-node,
.tree-hint-node {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  height: 26px;
  font-size: 12px;
}

.tree-more-node {
  color: var(--el-color-primary, #409eff);
  cursor: pointer;
}

.tree-more-node:hover {
  text-decoration: underline;
}

.tree-hint-node {
  color: var(--el-text-color-placeholder, #a8abb2);
  cursor: default;
}

.tree-hint-node .hint-icon {
  color: var(--el-text-color-placeholder, #a8abb2);
}

.tree-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px 12px;
  text-align: center;
}

.tree-error-icon {
  font-size: 28px;
  color: var(--el-color-danger, #f56c6c);
}

.tree-error-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.tree-error-message {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 5;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary, #909399);
  overflow-wrap: anywhere;
}

.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  height: 26px;
  font-size: 13px;
  padding: 0;
  contain: content;
}

.custom-tree-node.is-table {
  color: var(--el-text-color-regular, #606266);
}

.node-icon {
  font-size: 12px;
  color: var(--el-color-primary, #409eff);
  flex-shrink: 0;
}

.node-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.node-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.node-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: 3px;
  background: transparent;
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}

.node-action-btn:hover {
  background: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
}

.query-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.query-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.query-tabs :deep(.el-tabs__header) {
  margin: 0;
  flex-shrink: 0;
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
}

.query-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow: hidden;
  min-height: 0;
}

.query-tabs :deep(.el-tab-pane) {
  height: 100%;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.tab-label .fa-icon {
  font-size: 12px;
}

.sql-pane {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sql-editor-section {
  display: flex;
  flex-direction: column;
  min-height: 80px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary, #303133);
  background: var(--el-fill-color-lighter, #f5f7fa);
  flex-shrink: 0;
  gap: 12px;
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.section-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.query-limit-select {
  width: 118px;
  flex-shrink: 0;
}

.truncate-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: normal;
  color: var(--el-color-warning, #e6a23c);
  white-space: nowrap;
  flex-shrink: 0;
}

.sql-editor {
  flex: 1;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: 4px;
  min-height: 0;
}

.sql-editor :deep(.cm-editor) {
  height: 100%;
  font-size: 13px;
}

.sql-editor :deep(.cm-scroller) {
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
}

.sql-editor :deep(.cm-gutters) {
  border-right: 1px solid rgba(255, 255, 255, 0.1);
}

.result-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 80px;
}

.result-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.result-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 16px;
  background: var(--el-fill-color-lighter, #f5f7fa);
  flex-shrink: 0;
}

.result-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.result-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding: 0;
}

.result-tabs :deep(.el-tab-pane) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.query-result-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: hidden;
}

.result-table-wrap {
  flex: 1;
  min-height: 0;
}

.log-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
}

.log-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  flex-shrink: 0;
}

.log-label {
  width: 64px;
  color: var(--el-text-color-secondary, #909399);
  flex-shrink: 0;
}

.log-value {
  color: var(--el-text-color-primary, #303133);
}

.log-block {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  flex-shrink: 0;
  font-size: 13px;
}

.log-sql {
  flex: 1;
  min-width: 0;
  margin: 0;
  padding: 8px 12px;
  max-height: 200px;
  overflow: auto;
  border-radius: 4px;
  background: var(--el-fill-color-light, #f5f7fa);
  color: var(--el-text-color-primary, #303133);
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}

.log-error {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 13px;
  color: var(--el-color-danger, #f56c6c);
  background: var(--el-color-danger-light-9, #fef0f0);
  white-space: pre-wrap;
  word-break: break-all;
  flex-shrink: 0;
}

.resizer {
  height: 6px;
  cursor: row-resize;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-border-color-lighter, #e4e7ed);
  transition: background 0.2s;
}

.resizer:hover,
.resizer.dragging {
  background: var(--el-color-primary, #409eff);
}

.resizer-handle {
  width: 40px;
  height: 3px;
  border-radius: 2px;
  background: var(--el-text-color-placeholder, #a8abb2);
  transition: background 0.2s;
}

.resizer:hover .resizer-handle,
.resizer.dragging .resizer-handle {
  background: #fff;
}

.resizer.dragging {
  user-select: none;
}

.error-tag {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--el-color-danger, #f56c6c);
  font-weight: normal;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  font-weight: normal;
}

.result-content {
  flex: 1;
  overflow: auto;
  padding: 8px;
  min-height: 0;
}

.preview-pane {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.preview-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 6px 12px;
  background: var(--el-fill-color-lighter, #f5f7fa);
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
  flex-shrink: 0;
  flex-wrap: wrap;
}

.preview-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  overflow: hidden;
}

.preview-table-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-status {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  flex-shrink: 0;
}

.preview-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.limit-select {
  width: 96px;
}

.preview-basic {
  flex-shrink: 0;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
  background: var(--el-bg-color, #fff);
  max-height: 40%;
  overflow: auto;
  box-sizing: border-box;
}

.preview-subtabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.preview-subtabs :deep(.el-tabs__header) {
  margin: 0;
  flex-shrink: 0;
  padding: 0 8px;
}

.preview-subtabs :deep(.el-tabs__content) {
  flex: 1;
  overflow: hidden;
  min-height: 0;
}

.preview-subtabs :deep(.el-tab-pane) {
  height: 100%;
}

.preview-section {
  height: 100%;
  overflow: auto;
  padding: 12px;
  box-sizing: border-box;
}

.table-lineage-section {
  background: var(--el-bg-color, #fff);
}

.preview-data {
  height: 100%;
  overflow: auto;
  padding: 8px;
  box-sizing: border-box;
}

.table-health-section {
  background: var(--el-bg-color, #fff);
}

.health-overview {
  display: flex;
  gap: 18px;
  align-items: flex-start;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: 8px;
  background: var(--el-fill-color-lighter, #fafafa);
}

.health-score-box {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.health-score-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--el-text-color-primary, #303133);
  line-height: 1.2;
}

.health-score-label {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.health-overview-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.health-overview-title {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.health-overview-format {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-primary, #409eff);
}

.health-location {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  font-family: Menlo, Monaco, Consolas, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.health-warnings {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.health-warnings :deep(.el-alert) {
  padding: 6px 10px;
}

.health-ok {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--el-color-success, #67c23a);
}

.health-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 10px;
  margin: 12px 0;
}

.health-card {
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: 8px;
  background: var(--el-bg-color, #fff);
  min-width: 0;
}

.health-card.is-warning {
  border-color: var(--el-color-warning-light-5, #f3d19e);
  background: var(--el-color-warning-light-9, #fdf6ec);
}

.health-card.is-danger {
  border-color: var(--el-color-danger-light-5, #fab6b6);
  background: var(--el-color-danger-light-9, #fef0f0);
}

.health-card-label {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  margin-bottom: 4px;
}

.health-card-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
  line-height: 1.3;
}

.health-card-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.health-descriptions {
  margin-bottom: 12px;
}

.health-location-text {
  word-break: break-all;
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.health-block {
  margin-bottom: 14px;
}

.health-block-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.maintenance-dropdown-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.maintenance-menu-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 170px;
}

.maintenance-menu-icon {
  width: 14px;
  color: var(--el-text-color-secondary, #909399);
}

.maintenance-menu-unavailable {
  margin-left: auto;
  font-size: 11px;
  color: var(--el-text-color-disabled, #c0c4cc);
}

.maintenance-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.maintenance-description {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular, #606266);
}

.maintenance-form {
  margin-top: 4px;
}

.maintenance-param-desc {
  width: 100%;
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary, #909399);
}

.maintenance-result {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.maintenance-result-json {
  max-height: 240px;
  margin: 0;
  padding: 10px;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: 6px;
  background: var(--el-fill-color-light, #f5f7fa);
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}

@media (max-width: 1280px) {
  .health-cards {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .health-overview {
    flex-direction: column;
  }

  .health-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.pk-icon {
  color: var(--el-color-warning, #e6a23c);
}

.mr-1 {
  margin-right: 4px;
}

.ai-entry-btn {
  color: var(--el-color-primary, #409eff);
}

.ai-toolbar-btn {
  color: var(--el-color-primary, #409eff);
}
</style>

<style>
.table-context-menu {
  position: fixed;
  z-index: 3000;
  min-width: 200px;
  padding: 4px;
  background: var(--el-bg-color-overlay, #fff);
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  font-size: 13px;
}

.table-context-menu .ctx-header {
  padding: 4px 10px 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary, #909399);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-context-menu .ctx-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: 4px;
  color: var(--el-text-color-regular, #606266);
  cursor: pointer;
  transition: background 0.15s;
}

.table-context-menu .ctx-item:hover {
  background: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
}

.table-context-menu .ctx-item .fa-icon {
  width: 14px;
  font-size: 12px;
}

.table-context-menu .ctx-divider {
  height: 1px;
  margin: 4px 0;
  background: var(--el-border-color-lighter, #e4e7ed);
}
</style>
