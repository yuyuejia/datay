<template>
  <div class="tab-view">
    <div class="tab-scroll">
      <div
        v-for="tab in tabs"
        :key="tab.name"
        class="tab-item"
        :class="{ 'tab-item--active': tab.name === activeName }"
        @click="handleTabClick(tab.name)"
      >
        <span class="tab-item-title">{{ tab.title }}</span>
        <font-awesome-icon
          v-if="tab.closable"
          icon="times"
          class="tab-item-close"
          @click.stop="handleTabRemove(tab.name)"
        />
      </div>
    </div>
    <el-dropdown class="tab-actions" trigger="click" @command="handleCommand">
      <span class="tab-actions-trigger">
        <font-awesome-icon icon="ellipsis-vertical" />
      </span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="others">关闭其他</el-dropdown-item>
          <el-dropdown-item command="all">关闭全部</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script lang="ts" src="./tab-view.component.ts"></script>

<style scoped>
.tab-view {
  position: relative;
  display: flex;
  align-items: flex-end;
  height: 32px;
  background: #fff;
  padding: 0 8px;
  user-select: none;
}

.tab-view::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 1px;
  background: #dcdfe6;
}

.tab-scroll {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-end;
  gap: 2px;
  flex: 1;
  height: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
}

.tab-scroll::-webkit-scrollbar {
  display: none;
}

.tab-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 14px;
  margin-bottom: -1px;
  border: 1px solid transparent;
  border-bottom: none;
  border-radius: 4px 4px 0 0;
  background: #f2f3f5;
  color: #4e5969;
  font-size: 13px;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  transition:
    color 0.2s ease,
    background 0.2s ease,
    border-color 0.2s ease;
}

.tab-item:hover {
  color: #1677ff;
  background: #e8f1ff;
}

.tab-item--active {
  background: #fff;
  color: #1677ff;
  border-color: #dcdfe6;
}

.tab-item--active .tab-item-title {
  font-weight: 600;
}

.tab-item-close {
  font-size: 11px;
  color: #a9aeb8;
  border-radius: 50%;
  padding: 2px;
  transition:
    color 0.2s ease,
    background 0.2s ease;
}

.tab-item-close:hover {
  color: #fff;
  background: #f53f3f;
}

.tab-actions {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  align-self: center;
  margin-left: 8px;
  border-left: 1px solid #eef0f3;
  padding-left: 8px;
}

.tab-actions-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 3px;
  color: #4e5969;
  cursor: pointer;
  outline: none;
}

.tab-actions-trigger:hover {
  color: #1677ff;
  background: #f2f7ff;
}
</style>
