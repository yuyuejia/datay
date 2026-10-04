<template>
  <div>
    <h2 id="page-heading" data-cy="AppPackageHeading" class="d-flex align-items-center justify-content-between flex-wrap-nowrap">
      <span id="app-package-heading">数据服务应用市场</span>
      <div class="d-flex align-items-center">
        <el-button class="mr-2" @click="triggerUpload">
          <font-awesome-icon icon="file-import"></font-awesome-icon>
          <span>导入资产包文件</span>
        </el-button>
        <router-link :to="{ name: 'AppPackageExport' }" custom v-slot="{ navigate }">
          <el-button type="primary" class="jh-create-entity create-app-package" data-cy="entityCreateButton" @click="navigate">
            <font-awesome-icon icon="box-open"></font-awesome-icon>
            <span>导出数据应用资产包</span>
          </el-button>
        </router-link>
      </div>
    </h2>
    <br />

    <el-tabs v-model="activeTab">
      <el-tab-pane label="资产包" name="packages">
        <div class="d-flex align-items-center mb-3">
          <el-input class="mr-2" style="width: 280px" v-model="search" placeholder="按名称 / 编码 / 说明 / 场景搜索" clearable />
          <el-select class="mr-2" style="width: 150px" v-model="source">
            <el-option label="全部来源" value="ALL" />
            <el-option label="系统预制" value="SYSTEM" />
            <el-option label="本租户导出" value="TENANT" />
          </el-select>
          <el-select style="width: 180px" v-model="category" placeholder="全部业务场景" clearable>
            <el-option v-for="item in categories" :key="item" :label="item" :value="item" />
          </el-select>
        </div>

        <div class="alert alert-warning" v-if="!isFetching && packages && packages.length === 0">
          <span>未找到数据应用资产包</span>
        </div>

        <div v-if="packages && packages.length > 0">
          <el-table :data="packages" style="width: 100%">
            <el-table-column label="资产包" min-width="220">
              <template #default="scope">
                <div class="d-flex align-items-center">
                  <strong>{{ scope.row.name }}</strong>
                  <el-tag v-if="scope.row.packageType === 'SYSTEM'" class="ml-2" size="small" type="success">系统预制</el-tag>
                </div>
                <div class="text-muted small">{{ scope.row.code }}</div>
                <div class="text-muted small" v-if="scope.row.description">{{ scope.row.description }}</div>
              </template>
            </el-table-column>
            <el-table-column label="业务场景" width="120" align="center">
              <template #default="scope">
                <el-tag v-if="scope.row.category" size="small" type="info">{{ scope.row.category }}</el-tag>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="资产构成" min-width="260">
              <template #default="scope">
                <span class="small">{{ summaryText(scope.row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="版本" width="90" align="center">
              <template #default="scope">{{ scope.row.version || '-' }}</template>
            </el-table-column>
            <el-table-column label="大小" width="90" align="center">
              <template #default="scope">{{ sizeText(scope.row) }}</template>
            </el-table-column>
            <el-table-column label="来源" width="110" align="center">
              <template #default="scope">
                <span class="small">{{ sourceLabel(scope.row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="150">
              <template #default="scope">{{ formatDateShort(scope.row.createTime) || '' }}</template>
            </el-table-column>
            <el-table-column label="操作" fixed="right" width="320">
              <template #default="scope">
                <div class="btn-group">
                  <el-button type="primary" size="small" @click="openInit(scope.row)" data-cy="entityInitButton">
                    <span>初始化</span>
                  </el-button>
                  <router-link :to="{ name: 'AppPackageView', params: { packageId: scope.row.id } }" custom v-slot="{ navigate }">
                    <el-button type="info" size="small" @click="navigate" data-cy="entityDetailsButton">
                      <span>详情</span>
                    </el-button>
                  </router-link>
                  <el-button size="small" @click="download(scope.row)">
                    <span>下载</span>
                  </el-button>
                  <el-button
                    v-if="scope.row.packageType !== 'SYSTEM'"
                    size="small"
                    type="danger"
                    @click="prepareRemove(scope.row)"
                    data-cy="entityDeleteButton"
                  >
                    <span>删除</span>
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-show="packages && packages.length > 0">
          <div class="list-pagination">
            <jhi-item-count :page="page" :total="queryCount" :items-per-page="itemsPerPage"></jhi-item-count>
            <el-pagination
              background
              layout="sizes, prev, pager, next, jumper"
              :total="totalItems"
              :page-sizes="[10, 20, 50, 100]"
              :pager-count="7"
              v-model:current-page="page"
              v-model:page-size="itemsPerPage"
            />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="初始化记录" name="instances">
        <div class="alert alert-warning" v-if="instances && instances.length === 0">
          <span>当前租户还没有数据应用初始化记录</span>
        </div>
        <el-table v-if="instances && instances.length > 0" :data="instances" style="width: 100%">
          <el-table-column prop="packageName" label="资产包" min-width="180">
            <template #default="scope">
              <div>{{ scope.row.packageName || '-' }}</div>
              <div class="text-muted small">{{ scope.row.packageCode }}</div>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="100" align="center">
            <template #default="scope">
              <el-tag size="small" :type="scope.row.status === 'SUCCESS' ? 'success' : 'danger'">
                {{ scope.row.status === 'SUCCESS' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="240" show-overflow-tooltip />
          <el-table-column prop="createUser" label="操作人" width="120" />
          <el-table-column label="时间" width="160">
            <template #default="scope">{{ formatDateShort(scope.row.createTime) || '' }}</template>
          </el-table-column>
        </el-table>
        <div v-show="instances && instances.length > 0">
          <div class="list-pagination">
            <el-pagination
              background
              layout="sizes, prev, pager, next"
              :total="instanceTotal"
              :page-sizes="[10, 20, 50]"
              :pager-count="7"
              v-model:current-page="instancePage"
              v-model:page-size="instanceItemsPerPage"
            />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <input ref="fileInput" type="file" accept="application/json,.json" style="display: none" @change="onFileSelected" />

    <app-package-init v-model="initVisible" :pkg="initPackage" :content="initContent" @initialized="onInitialized" />

    <app-modal ref="removeEntity" id="removeEntity">
      <template #modal-title>
        <span data-cy="appPackageDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p>确定要删除资产包 {{ removeId }} 吗？该操作只删除市场里的资产包定义，不影响已经初始化出来的数据应用。</p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="closeDialog()">取消</el-button>
          <el-button type="primary" data-cy="entityConfirmDeleteButton" @click="removePackage()">删除</el-button>
        </div>
      </template>
    </app-modal>
  </div>
</template>

<script lang="ts" src="./app-package.component.ts"></script>
