<template>
  <div class="row justify-content-center">
    <div class="col-md-9 toastify-container">
      <h2 id="settings-title">
        {{ pageTitle }}
      </h2>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="账号信息" name="account">
          <div class="alert alert-success" role="alert" v-if="success"><strong>设置保存成功!</strong></div>
          <div class="alert alert-danger" role="alert" v-if="errorEmailExists"><strong>邮件已经被注册!</strong> 请选择其它邮件.</div>

          <el-form label-width="100px" v-if="settingsAccount" @submit.prevent="save()">
            <el-form-item label="名字" :error="fieldError(v$.settingsAccount.firstName, '名字长度为 1 - 50 个字符')">
              <el-input
                name="firstName"
                placeholder="您的名字"
                maxlength="50"
                clearable
                v-model="v$.settingsAccount.firstName.$model"
                data-cy="firstname"
              />
            </el-form-item>
            <el-form-item label="姓氏" :error="fieldError(v$.settingsAccount.lastName, '姓氏长度为 1 - 50 个字符')">
              <el-input
                name="lastName"
                placeholder="您的姓氏"
                maxlength="50"
                clearable
                v-model="v$.settingsAccount.lastName.$model"
                data-cy="lastname"
              />
            </el-form-item>
            <el-form-item label="电子邮件" :error="fieldError(v$.settingsAccount.email, '请输入 5 - 254 个字符的有效邮箱')">
              <el-input
                name="email"
                type="email"
                placeholder="您的电子邮件"
                maxlength="254"
                clearable
                v-model="v$.settingsAccount.email.$model"
                data-cy="email"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" native-type="submit" :loading="isSaving" :disabled="v$.settingsAccount.$invalid" data-cy="submit"
                >保存</el-button
              >
            </el-form-item>
          </el-form>

          <el-divider content-position="left">访问令牌</el-divider>
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="访问令牌用于身份认证，可连接本系统的 MCP 服务（如 Claude Desktop、Cursor），也可供程序调用本系统服务使用。可为不同客户端或场景创建独立令牌，并单独停用或删除。"
          />
          <p class="text-muted mt-2">升级后旧版单令牌已失效；如果现有客户端无法连接，请重新创建令牌并更新客户端配置。</p>

          <div class="token-toolbar">
            <span class="token-count">已创建 {{ accessTokens.length }} / {{ maxAccessTokens }} 个令牌</span>
            <el-button
              type="primary"
              plain
              :disabled="accessTokens.length >= maxAccessTokens"
              @click="openCreateToken()"
              data-cy="access-token-create"
            >
              <font-awesome-icon icon="plus" class="mr-1"></font-awesome-icon>
              <span>新增访问令牌</span>
            </el-button>
          </div>

          <el-table :data="accessTokens" v-loading="tokensLoading" class="token-table" size="small" border>
            <el-table-column label="名称" min-width="170">
              <template #default="{ row }">
                <div class="token-name">{{ row.name }}</div>
                <div v-if="row.description" class="token-description">{{ row.description }}</div>
              </template>
            </el-table-column>
            <el-table-column label="令牌" width="150">
              <template #default="{ row }">
                <code class="token-hint">{{ formatTokenHint(row) }}</code>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.status === 'ENABLED'"
                  @change="value => onTokenStatusChange(row, value)"
                  data-cy="access-token-status"
                />
              </template>
            </el-table-column>
            <el-table-column label="最近使用" width="170">
              <template #default="{ row }">{{ formatTokenTime(row.lastUsedTime, '从未使用') }}</template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ formatTokenTime(row.createTime, '-') }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEditToken(row)">编辑</el-button>
                <el-button link type="primary" @click="rotateToken(row)">重新生成</el-button>
                <el-button link type="danger" @click="deleteToken(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无访问令牌，点击右上角新增" />
            </template>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="修改密码" name="password" lazy>
          <change-password />
        </el-tab-pane>
      </el-tabs>

      <el-dialog v-model="tokenDialogVisible" :title="editingToken ? '编辑访问令牌' : '新增访问令牌'" width="480px">
        <el-form label-width="80px" @submit.prevent="saveToken()">
          <el-form-item label="名称" required>
            <el-input
              v-model="tokenForm.name"
              maxlength="50"
              show-word-limit
              clearable
              placeholder="例如：Claude Desktop、BI 报表"
              data-cy="access-token-name"
            />
          </el-form-item>
          <el-form-item label="描述">
            <el-input
              v-model="tokenForm.description"
              type="textarea"
              :rows="3"
              maxlength="255"
              show-word-limit
              placeholder="用途说明（选填）"
              data-cy="access-token-description"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="tokenDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="tokenSaving" @click="saveToken()">保存</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="tokenDisplayVisible" title="访问令牌已生成" width="560px">
        <el-alert type="warning" :closable="false" show-icon title="请立即复制并妥善保存，关闭后将无法再次查看完整令牌。" />
        <p v-if="createdTokenName" class="token-created-name">名称：{{ createdTokenName }}</p>
        <div class="token-copy-row">
          <el-input :model-value="createdToken || ''" readonly data-cy="access-token-value" />
          <el-button type="primary" plain @click="copyCreatedToken()" data-cy="access-token-copy">
            {{ tokenCopied ? '已复制' : '复制' }}
          </el-button>
        </div>
        <template #footer>
          <el-button type="primary" @click="tokenDisplayVisible = false">我已保存</el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script lang="ts" src="./settings.component.ts"></script>

<style scoped>
.token-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 16px 0 12px;
}

.token-count {
  color: #909399;
  font-size: 13px;
}

.token-table {
  width: 100%;
  margin-bottom: 16px;
}

.token-name {
  font-weight: 600;
  color: #303133;
}

.token-description {
  margin-top: 2px;
  color: #909399;
  font-size: 12px;
  line-height: 18px;
}

.token-hint {
  color: #606266;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
}

.token-created-name {
  margin: 14px 0 8px;
  color: #606266;
  font-size: 13px;
}

.token-copy-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
