<template>
  <div>
    <h2 id="page-heading" class="d-flex align-items-center justify-content-between flex-wrap-nowrap">
      <span id="file-management-heading">文件管理</span>
      <div class="d-flex align-items-center">
        <el-button plain @click="showMkdirModal = true" class="mr-2">
          <font-awesome-icon icon="folder-plus"></font-awesome-icon>
          <span>新建目录</span>
        </el-button>
        <el-button type="primary" @click="showUploadModal = true">
          <font-awesome-icon icon="upload"></font-awesome-icon>
          <span>上传文件</span>
        </el-button>
      </div>
    </h2>

    <div class="breadcrumb-bar mb-3">
      <template v-for="(crumb, index) in breadcrumbs" :key="index">
        <span v-if="index === 0" class="crumb-home" @click="navigateTo(crumb.path)" title="根目录">
          <font-awesome-icon icon="home"></font-awesome-icon>
        </span>
        <template v-else>
          <span class="crumb-sep">/</span>
          <a
            v-if="index < breadcrumbs.length - 1"
            class="crumb-link"
            href="javascript:void(0)"
            @click="navigateTo(crumb.path)"
          >{{ crumb.name }}</a>
          <span v-else class="crumb-current">{{ crumb.name }}</span>
        </template>
      </template>
    </div>

    <div class="alert alert-warning" v-if="!loading && files && files.length === 0">
      <font-awesome-icon icon="info-circle" class="mr-1"></font-awesome-icon>
      <span>当前目录为空</span>
    </div>

    <div v-if="loading" class="text-center py-4">
      <font-awesome-icon icon="spinner" spin class="fa-2x text-muted"></font-awesome-icon>
    </div>

    <div v-if="!loading && files && files.length > 0">
      <el-table :data="files" style="width: 100%">
        <el-table-column label="名称" min-width="300">
          <template #default="scope">
            <div
              class="d-flex align-items-center"
              :class="{ 'clickable': scope.row.isDirectory }"
              @click="clickItem(scope.row)"
            >
              <font-awesome-icon
                :icon="getFileIcon(scope.row)"
                :class="scope.row.isDirectory ? 'text-warning' : 'text-muted'"
                class="mr-2 fa-lg"
              ></font-awesome-icon>
              <span>{{ scope.row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="130" align="center">
          <template #default="scope">{{ formatSize(scope.row.size) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.isDirectory" type="warning" size="small">目录</el-tag>
            <el-tag v-else type="success" size="small">文件</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="修改时间" width="180">
          <template #default="scope">{{ formatDate(scope.row.lastModified) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="scope">
            <div class="btn-group">
              <el-button
                v-if="!scope.row.isDirectory"
                @click="handleDownload(scope.row)"
                size="small"
                type="primary"
              >
                下载
              </el-button>
              <el-button
                v-if="scope.row.isDirectory"
                @click="navigateTo(scope.row.path)"
                size="small"
                type="primary"
              >
                进入
              </el-button>
              <el-button
                @click="handleDelete(scope.row)"
                size="small"
                type="danger"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <app-modal v-model="showUploadModal" title="上传文件" size="md">
      <div class="modal-body">
        <div class="mb-3">
          <label class="form-label">当前路径</label>
          <el-input :value="currentPath || '(根目录)'" disabled/>
        </div>
        <div class="mb-3">
          <label class="form-label">选择文件</label>
          <el-upload
            :auto-upload="false"
            :limit="1"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
          >
            <el-button>浏览</el-button>
          </el-upload>
        </div>
      </div>
      <template #modal-footer>
        <el-button @click="showUploadModal = false">取消</el-button>
        <el-button type="primary" :disabled="!selectedFile" @click="handleUpload">
          确认上传
        </el-button>
      </template>
    </app-modal>

    <app-modal v-model="showMkdirModal" title="新建目录" size="sm">
      <div class="modal-body">
        <div class="mb-3">
          <label class="form-label">当前路径</label>
          <el-input :value="currentPath || '(根目录)'" disabled/>
        </div>
        <div class="mb-3">
          <label class="form-label">目录名称</label>
          <el-input v-model="newDirName" placeholder="请输入目录名称" @keyup.enter="handleMkdir" clearable/>
        </div>
      </div>
      <template #modal-footer>
        <el-button @click="showMkdirModal = false">取消</el-button>
        <el-button type="primary" :disabled="!newDirName.trim()" @click="handleMkdir">
          确认创建
        </el-button>
      </template>
    </app-modal>

    <app-modal v-model="showDeleteDialog" title="确认删除" size="md">
      <div class="modal-body">
        <p>确定要删除 {{ pendingDeleteFile?.isDirectory ? '目录' : '文件' }} <strong>"{{ pendingDeleteFile?.name }}"</strong> 吗？</p>
        <p v-if="pendingDeleteFile?.isDirectory" class="text-muted mb-0">目录内所有内容将被一并删除</p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="showDeleteDialog = false">取消</el-button>
          <el-button type="primary" @click="confirmDelete">删除</el-button>
        </div>
      </template>
    </app-modal>
  </div>
</template>

<script lang="ts" src="./file-management.component.ts"></script>

<style scoped>
.clickable {
  cursor: pointer;
}
.breadcrumb-bar {
  background: transparent;
  padding: 4px 0;
  font-size: 0.9rem;
  color: #6c757d;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.crumb-home {
  cursor: pointer;
  color: #6c757d;
  padding: 2px 4px;
  border-radius: 4px;
  transition: color 0.15s, background-color 0.15s;
}
.crumb-home:hover {
  color: #0d6efd;
  background-color: #e9ecef;
}
.crumb-sep {
  color: #adb5bd;
}
.crumb-link {
  color: #6c757d;
  text-decoration: none;
  padding: 2px 4px;
  border-radius: 4px;
  transition: color 0.15s, background-color 0.15s;
}
.crumb-link:hover {
  color: #0d6efd;
  background-color: #e9ecef;
  text-decoration: none;
}
.crumb-current {
  color: #212529;
  font-weight: 500;
}
</style>