<template>
  <li v-if="authenticated" class="nav-item notification-nav">
    <el-popover
      v-model:visible="popoverVisible"
      placement="bottom-end"
      :width="360"
      trigger="click"
      popper-class="notification-popover"
      @show="loadNotifications"
    >
      <template #reference>
        <a href="javascript:void(0)" class="nav-link notification-link" title="站内信">
          <el-badge :value="unread" :max="99" :hidden="unread === 0">
            <font-awesome-icon icon="bell" />
          </el-badge>
        </a>
      </template>

      <div class="notification-panel" v-loading="loading">
        <div class="notification-header">
          <span class="notification-title">站内信</span>
          <el-button v-if="unread > 0" link type="primary" size="small" @click="markAllRead">全部已读</el-button>
        </div>
        <div v-if="notifications.length === 0" class="notification-empty">暂无站内信</div>
        <div v-else class="notification-list">
          <div
            v-for="item in notifications"
            :key="item.id"
            class="notification-item"
            :class="{ unread: !item.readFlag }"
            @click="markRead(item)"
          >
            <div class="notification-item-title">
              <span class="unread-dot" v-if="!item.readFlag"></span>
              {{ item.title }}
            </div>
            <div class="notification-item-content">{{ item.content }}</div>
            <div class="notification-item-time">{{ formatTime(item.createTime) }}</div>
          </div>
        </div>
        <div class="notification-footer">
          <el-button link type="primary" size="small" @click="goToAlertCenter">查看告警中心</el-button>
        </div>
      </div>
    </el-popover>
  </li>
</template>

<script lang="ts" src="./notification-bell.component.ts"></script>

<style scoped>
.notification-nav {
  display: flex;
  align-items: center;
}

.notification-link {
  font-size: 16px;
  padding: 0 4px !important;
}

.notification-link :deep(.el-badge__content) {
  font-size: 10px;
  height: 16px;
  line-height: 16px;
  padding: 0 4px;
}

.notification-panel {
  max-height: 460px;
  display: flex;
  flex-direction: column;
}

.notification-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}

.notification-title {
  font-weight: 600;
  color: #303133;
}

.notification-list {
  overflow-y: auto;
  max-height: 350px;
}

.notification-item {
  padding: 10px 4px;
  border-bottom: 1px solid #f2f3f5;
  cursor: pointer;
}

.notification-item:hover {
  background: #f5f7fa;
}

.notification-item-title {
  font-size: 13px;
  color: #303133;
  line-height: 1.5;
  word-break: break-word;
}

.notification-item-content {
  margin-top: 3px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.notification-item-time {
  margin-top: 4px;
  color: #c0c4cc;
  font-size: 11px;
}

.unread-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 5px;
  border-radius: 50%;
  background: #f56c6c;
  vertical-align: middle;
}

.notification-empty {
  padding: 28px 0;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

.notification-footer {
  padding-top: 8px;
  text-align: center;
  border-top: 1px solid #ebeef5;
}
</style>
