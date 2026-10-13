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
            title="访问令牌用于身份认证，可连接本系统的 MCP 服务（如 Claude Desktop、Cursor），也可供程序调用本系统服务使用。"
          />
          <p v-if="hasMcpToken" class="text-muted mt-2">您已配置访问令牌。重新生成后旧令牌将立即失效。</p>
          <p v-else class="text-muted mt-2">您尚未配置访问令牌，请先点击下方按钮生成。</p>

          <el-alert v-if="generatedToken" type="success" :closable="false" title="请立即复制并妥善保存您的令牌，关闭页面后将无法再次查看：">
            <div class="input-group mt-2">
              <input type="text" class="form-control" :value="generatedToken" readonly data-cy="mcp-token-value" />
              <el-button plain @click="copyToken()" data-cy="mcp-token-copy">
                {{ tokenCopied ? '已复制' : '复制' }}
              </el-button>
            </div>
          </el-alert>

          <el-button type="primary" plain @click="generateMcpToken()" data-cy="mcp-token-generate">
            {{ hasMcpToken ? '重新生成令牌' : '生成令牌' }}
          </el-button>
        </el-tab-pane>

        <el-tab-pane label="修改密码" name="password" lazy>
          <change-password />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script lang="ts" src="./settings.component.ts"></script>
