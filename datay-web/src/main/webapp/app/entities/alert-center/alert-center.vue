<template>
  <div class="alert-center">
    <div class="page-heading">
      <div>
        <h2 id="alert-center-heading">告警中心</h2>
        <p class="page-subtitle">任务失败、超时自动告警；恢复后自动闭环。</p>
      </div>
      <el-button :loading="loading" @click="refreshAll">
        <font-awesome-icon icon="sync" />
        <span>刷新</span>
      </el-button>
    </div>

    <div class="summary-grid">
      <el-card shadow="never" class="summary-card firing-card">
        <div class="summary-label">待处理</div>
        <div class="summary-value">{{ summary.firing ?? 0 }}</div>
      </el-card>
      <el-card shadow="never" class="summary-card acknowledged-card">
        <div class="summary-label">已认领</div>
        <div class="summary-value">{{ summary.acknowledged ?? 0 }}</div>
      </el-card>
      <el-card shadow="never" class="summary-card resolved-card">
        <div class="summary-label">已恢复</div>
        <div class="summary-value">{{ summary.resolved ?? 0 }}</div>
      </el-card>
    </div>

    <el-tabs v-model="activeTab" class="alert-tabs list-page-tabs">
      <el-tab-pane label="告警事件" name="events">
        <div class="toolbar">
          <el-select v-model="eventStatus" placeholder="全部状态" clearable style="width: 140px" @change="onSearch">
            <el-option v-for="item in alertStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="eventSeverity" placeholder="全部级别" clearable style="width: 140px" @change="onSearch">
            <el-option v-for="item in alertSeverityOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-input
            v-model="eventSearch"
            placeholder="搜索告警标题 / 任务 / 实例"
            clearable
            style="width: 300px"
            @keyup.enter="onSearch"
            @clear="onSearch"
          />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>

        <div class="list-page-scroll">
          <el-table v-loading="loading" :data="events" row-key="id" style="width: 100%">
            <el-table-column label="级别" width="90" align="center">
              <template #default="scope">
                <el-tag :type="severityTagType(scope.row.severity)" size="small">{{ scope.row.severity || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="告警" min-width="260">
              <template #default="scope">
                <el-link type="primary" :underline="false" @click="openEvent(scope.row)">{{ scope.row.title }}</el-link>
                <div class="sub-text">{{ scope.row.jobName }}（{{ scope.row.jobCode }}）</div>
              </template>
            </el-table-column>
            <el-table-column label="事件" width="100" align="center">
              <template #default="scope">{{ eventTypeLabel(scope.row.eventType) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="scope">
                <el-tag :type="statusTagType(scope.row.status)" size="small">{{ statusLabel(scope.row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="eventCount" label="次数" width="70" align="center" />
            <el-table-column label="最近触发" width="160">
              <template #default="scope">{{ dateFormat.formatDateShort(scope.row.lastSeen) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="scope">
                <el-button link type="primary" size="small" @click="openEvent(scope.row)">详情</el-button>
                <el-button v-if="scope.row.status === 'FIRING'" link type="warning" size="small" @click="acknowledgeEvent(scope.row)">
                  认领
                </el-button>
                <el-button v-if="scope.row.status !== 'RESOLVED'" link type="success" size="small" @click="resolveEvent(scope.row)">
                  恢复
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="pagination-bar">
          <el-pagination
            background
            layout="total, prev, pager, next"
            :total="eventTotal"
            :page-size="eventSize"
            :current-page="eventPage"
            @current-change="onEventPageChange"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="告警规则" name="rules">
        <div class="toolbar toolbar-between">
          <span class="hint-text">站内信默认开启；邮件、Webhook 需要在规则中选择已启用渠道。自定义规则命中时不会重复触发默认规则。</span>
          <el-button type="primary" @click="openCreateRule">
            <font-awesome-icon icon="plus" />
            <span>新建规则</span>
          </el-button>
        </div>
        <div class="list-page-scroll">
          <el-table :data="rules" row-key="id" style="width: 100%">
            <el-table-column label="规则名称" min-width="220">
              <template #default="scope">
                <span>{{ scope.row.name }}</span>
                <el-tag v-if="scope.row.defaultRule" type="info" size="small" class="ml-2">默认规则</el-tag>
                <div class="sub-text">{{ scope.row.code }}</div>
              </template>
            </el-table-column>
            <el-table-column label="事件" width="150">
              <template #default="scope">{{ (scope.row.eventType || '').split(',').map(eventTypeLabel).join('、') }}</template>
            </el-table-column>
            <el-table-column label="级别" width="80" align="center">
              <template #default="scope">
                <el-tag :type="severityTagType(scope.row.severity)" size="small">{{ scope.row.severity }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="范围" min-width="160">
              <template #default="scope">
                <div>类型：{{ scope.row.jobType || '全部' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="静默" width="90" align="center">
              <template #default="scope">{{ scope.row.silenceMinutes }} 分钟</template>
            </el-table-column>
            <el-table-column label="站内信接收人" min-width="180">
              <template #default="scope">{{ ruleRecipients(scope.row) }}</template>
            </el-table-column>
            <el-table-column label="渠道" min-width="150">
              <template #default="scope">{{ ruleChannels(scope.row) }}</template>
            </el-table-column>
            <el-table-column label="启用" width="80" align="center">
              <template #default="scope">
                <el-switch v-model="scope.row.enabled" @change="toggleRule(scope.row)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="scope">
                <el-button link type="primary" size="small" @click="openEditRule(scope.row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="deleteRule(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="通知渠道" name="channels">
        <div class="toolbar toolbar-between">
          <span class="hint-text">站内信始终发送，无需配置。这里维护邮件和 Webhook 渠道。</span>
          <el-button type="primary" @click="openCreateChannel">
            <font-awesome-icon icon="plus" />
            <span>新建渠道</span>
          </el-button>
        </div>
        <div class="list-page-scroll">
          <el-table :data="channels" row-key="id" style="width: 100%">
            <el-table-column prop="name" label="渠道名称" min-width="180">
              <template #default="scope">
                <div>{{ scope.row.name }}</div>
                <div class="sub-text">{{ scope.row.code }}</div>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="100" align="center">
              <template #default="scope">
                <el-tag :type="scope.row.type === 'EMAIL' ? 'primary' : 'success'" size="small">
                  {{ channelTypeLabel(scope.row.type) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="目标" min-width="260">
              <template #default="scope">{{ channelTarget(scope.row) }}</template>
            </el-table-column>
            <el-table-column label="启用" width="80" align="center">
              <template #default="scope">
                <el-switch v-model="scope.row.enabled" @change="toggleChannel(scope.row)" />
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="160">
              <template #default="scope">{{ dateFormat.formatDateShort(scope.row.updateTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="210" fixed="right">
              <template #default="scope">
                <el-button link type="success" size="small" :loading="testingChannelId === scope.row.id" @click="testChannel(scope.row)">
                  测试
                </el-button>
                <el-button link type="primary" size="small" @click="openEditChannel(scope.row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="deleteChannel(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="eventDrawer" title="告警详情" size="560px">
      <div v-if="selectedEvent" class="event-detail">
        <h3>{{ selectedEvent.title }}</h3>
        <div class="detail-tags">
          <el-tag :type="severityTagType(selectedEvent.severity)" size="small">{{ selectedEvent.severity }}</el-tag>
          <el-tag :type="statusTagType(selectedEvent.status)" size="small">{{ statusLabel(selectedEvent.status) }}</el-tag>
          <el-tag type="info" size="small">{{ eventTypeLabel(selectedEvent.eventType) }}</el-tag>
        </div>
        <el-descriptions :column="1" border size="small" class="detail-descriptions">
          <el-descriptions-item label="任务">{{ selectedEvent.jobName }}（{{ selectedEvent.jobCode }}）</el-descriptions-item>
          <el-descriptions-item label="实例">{{ selectedEvent.instanceCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="首次触发">{{ dateFormat.formatDateShort(selectedEvent.firstSeen) }}</el-descriptions-item>
          <el-descriptions-item label="最近触发">{{ dateFormat.formatDateShort(selectedEvent.lastSeen) }}</el-descriptions-item>
          <el-descriptions-item label="触发次数">{{ selectedEvent.eventCount }}</el-descriptions-item>
          <el-descriptions-item v-if="selectedEvent.ackBy" label="认领人">{{ selectedEvent.ackBy }}</el-descriptions-item>
          <el-descriptions-item v-if="selectedEvent.resolveBy" label="恢复人">{{ selectedEvent.resolveBy }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-section-title">告警内容</div>
        <pre class="alert-content">{{ selectedEvent.content }}</pre>

        <div class="detail-section-title">投递记录</div>
        <el-table v-if="deliveries.length > 0" :data="deliveries" size="small" style="width: 100%">
          <el-table-column label="渠道" width="90">
            <template #default="scope">{{ channelTypeLabel(scope.row.channelType) }}</template>
          </el-table-column>
          <el-table-column prop="target" label="目标" min-width="150" show-overflow-tooltip />
          <el-table-column label="状态" width="80">
            <template #default="scope">
              <el-tag :type="scope.row.status === 'SUCCESS' ? 'success' : scope.row.status === 'FAILED' ? 'danger' : 'info'" size="small">
                {{ deliveryStatusLabel(scope.row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="attempt" label="次数" width="60" align="center" />
          <el-table-column prop="errorMessage" label="错误" min-width="160" show-overflow-tooltip />
        </el-table>
        <div v-else class="empty-hint">本次告警没有外部渠道投递记录，或站内信已直接送达。</div>

        <div class="drawer-actions">
          <el-button v-if="selectedEvent.status === 'FIRING'" type="warning" @click="acknowledgeEvent(selectedEvent)"> 认领 </el-button>
          <el-button v-if="selectedEvent.status !== 'RESOLVED'" type="success" @click="resolveEvent(selectedEvent)"> 标记恢复 </el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="ruleDialog" :title="ruleForm.id ? '编辑告警规则' : '新建告警规则'" width="680px">
      <el-form :model="ruleForm" label-width="110px">
        <el-form-item label="规则名称" required>
          <el-input v-model="ruleForm.name" placeholder="例如：核心任务失败告警" />
        </el-form-item>
        <el-form-item label="规则编码">
          <el-input v-model="ruleForm.code" placeholder="留空自动生成，仅支持字母、数字、下划线、中划线" />
        </el-form-item>
        <el-form-item label="告警事件" required>
          <el-select v-model="ruleForm.eventTypes" multiple style="width: 100%">
            <el-option v-for="item in alertEventTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="告警级别" required>
          <el-select v-model="ruleForm.severity" style="width: 100%">
            <el-option v-for="item in alertSeverityOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务类型">
          <el-input v-model="ruleForm.jobType" placeholder="例如 SQL、SHELL、DAG，留空表示全部" />
        </el-form-item>
        <el-form-item label="静默时长">
          <el-input-number v-model="ruleForm.silenceMinutes" :min="0" :max="1440" />
          <span class="form-hint">分钟内重复失败只累计次数，不重复发送</span>
        </el-form-item>
        <el-form-item label="站内信接收人">
          <div class="recipient-field">
            <div class="recipient-selected">
              <template v-if="ruleForm.recipients.length">
                <el-tag
                  v-for="recipient in ruleForm.recipients"
                  :key="recipient"
                  :type="recipientTagType(recipient)"
                  closable
                  @close="removeRecipient(recipient)"
                >
                  {{ recipientLabel(recipient) }}
                </el-tag>
              </template>
              <span v-else class="recipient-selected-empty">未选择，保存时默认发送给租户管理员</span>
            </div>
            <el-button @click="openRecipientDialog">选择接收人</el-button>
          </div>
          <div class="form-tip">默认发送给租户管理员；邮件 / Webhook 的目标在渠道中配置。</div>
        </el-form-item>
        <el-form-item label="通知渠道">
          <el-select v-model="ruleForm.channelIds" multiple clearable style="width: 100%" placeholder="不选则只发站内信">
            <el-option v-for="item in channelOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <div class="form-tip">站内信始终发送，无需选择。</div>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="ruleForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="recipientDialog" title="选择站内信接收人" width="720px" append-to-body>
      <div v-loading="recipientOptionsLoading" class="recipient-picker">
        <el-input v-model="recipientSearch" clearable placeholder="搜索用户或角色" class="recipient-search" />

        <div v-if="filteredRecipientRoles.length" class="recipient-section">
          <div class="recipient-section-title">角色</div>
          <el-checkbox-group v-model="recipientDraft" class="recipient-grid">
            <el-checkbox
              v-for="role in filteredRecipientRoles"
              :key="`role:${role.name}`"
              :value="`role:${role.name}`"
              class="recipient-option"
            >
              {{ role.name }}
            </el-checkbox>
          </el-checkbox-group>
        </div>

        <div v-if="filteredRecipientUsers.length" class="recipient-section">
          <div class="recipient-section-title">用户</div>
          <el-checkbox-group v-model="recipientDraft" class="recipient-grid">
            <el-checkbox
              v-for="user in filteredRecipientUsers"
              :key="`user:${user.login}`"
              :value="`user:${user.login}`"
              class="recipient-option"
            >
              {{ user.login }}
            </el-checkbox>
          </el-checkbox-group>
        </div>

        <div v-if="missingRecipientExpressions.length" class="recipient-section">
          <div class="recipient-section-title">当前租户未找到的接收人</div>
          <div class="recipient-selected">
            <el-tag
              v-for="recipient in missingRecipientExpressions"
              :key="recipient"
              type="info"
              closable
              @close="removeDraftRecipient(recipient)"
            >
              {{ recipientLabel(recipient) }}
            </el-tag>
          </div>
        </div>

        <el-empty
          v-if="
            !recipientOptionsLoading &&
            filteredRecipientRoles.length === 0 &&
            filteredRecipientUsers.length === 0 &&
            missingRecipientExpressions.length === 0
          "
          description="暂无可选接收人"
          :image-size="72"
        />
      </div>
      <template #footer>
        <div class="recipient-dialog-footer">
          <span class="recipient-selected-count">已选 {{ recipientDraft.length }} 项</span>
          <el-button @click="recipientDialog = false">取消</el-button>
          <el-button type="primary" @click="confirmRecipients">确定</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="channelDialog" :title="channelForm.id ? '编辑通知渠道' : '新建通知渠道'" width="620px">
      <el-form :model="channelForm" label-width="110px">
        <el-form-item label="渠道名称" required>
          <el-input v-model="channelForm.name" placeholder="例如：运维邮件组" />
        </el-form-item>
        <el-form-item label="渠道编码">
          <el-input v-model="channelForm.code" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="渠道类型" required>
          <el-select v-model="channelForm.type" style="width: 100%">
            <el-option v-for="item in alertChannelTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>

        <template v-if="channelForm.type === 'EMAIL'">
          <el-form-item label="收件人" required>
            <el-input v-model="channelForm.to" placeholder="多个邮箱用逗号分隔" />
          </el-form-item>
          <el-form-item label="抄送">
            <el-input v-model="channelForm.cc" placeholder="可选，多个邮箱用逗号分隔" />
          </el-form-item>
          <el-form-item label="邮件前缀">
            <el-input v-model="channelForm.subjectPrefix" placeholder="[DataY告警]" />
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="Webhook 地址" required>
            <el-input v-model="channelForm.url" placeholder="https://example.com/hook" />
          </el-form-item>
          <el-form-item label="签名密钥">
            <el-input v-model="channelForm.secret" placeholder="可选，启用 HMAC-SHA256 签名" show-password />
          </el-form-item>
          <el-form-item label="自定义请求头">
            <el-input v-model="channelForm.headersText" type="textarea" :rows="3" placeholder='可选，JSON 对象，例如 {"X-Token":"xxx"}' />
          </el-form-item>
        </template>

        <el-form-item label="启用">
          <el-switch v-model="channelForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="channelDialog = false">取消</el-button>
        <el-button type="primary" @click="saveChannel">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" src="./alert-center.component.ts"></script>

<style scoped>
.alert-center {
  padding: 4px 2px 24px;
}

.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;
}

.page-heading h2 {
  margin: 0 0 4px;
  font-size: 22px;
}

.page-subtitle {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.summary-card {
  border: none;
  border-radius: 10px;
  background: #f7f8fa;
}

.summary-card :deep(.el-card__body) {
  padding: 18px 22px;
}

.summary-label {
  color: #909399;
  font-size: 13px;
  margin-bottom: 6px;
}

.summary-value {
  font-size: 28px;
  font-weight: 600;
  line-height: 1;
}

.firing-card .summary-value {
  color: #f56c6c;
}

.acknowledged-card .summary-value {
  color: #e6a23c;
}

.resolved-card .summary-value {
  color: #67c23a;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.toolbar-between {
  justify-content: space-between;
}

.hint-text,
.form-tip {
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}

.form-tip {
  margin-top: 4px;
}

.form-hint {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}

.recipient-field {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
}

.recipient-selected {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 32px;
  padding: 4px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
}

.recipient-selected-empty {
  color: #c0c4cc;
  font-size: 13px;
  line-height: 24px;
}

.recipient-picker {
  min-height: 260px;
}

.recipient-search {
  margin-bottom: 14px;
}

.recipient-section {
  margin-bottom: 18px;
}

.recipient-section-title {
  margin-bottom: 8px;
  color: #303133;
  font-weight: 600;
}

.recipient-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 14px;
}

.recipient-option {
  width: 100%;
  height: auto;
  margin-right: 0;
  padding: 6px 0;
}

.recipient-dialog-footer {
  display: flex;
  align-items: center;
  width: 100%;
}

.recipient-selected-count {
  margin-right: auto;
  color: #909399;
  font-size: 13px;
}

.sub-text {
  margin-top: 3px;
  color: #909399;
  font-size: 12px;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.ml-2 {
  margin-left: 6px;
}

.event-detail h3 {
  margin: 4px 0 10px;
  font-size: 18px;
}

.detail-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}

.detail-descriptions {
  margin-bottom: 18px;
}

.detail-section-title {
  margin: 18px 0 8px;
  font-weight: 600;
  color: #303133;
}

.alert-content {
  margin: 0;
  padding: 12px 14px;
  border-radius: 6px;
  background: #f5f7fa;
  color: #606266;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.empty-hint {
  padding: 12px 0;
  color: #909399;
  font-size: 13px;
}

.drawer-actions {
  display: flex;
  gap: 10px;
  margin-top: 24px;
}

@media (max-width: 900px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
