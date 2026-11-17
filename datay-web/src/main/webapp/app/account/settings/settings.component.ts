import { type ComputedRef, type Ref, computed, defineComponent, inject, ref, watch } from 'vue';
import { useVuelidate } from '@vuelidate/core';
import { email, maxLength, minLength, required } from '@vuelidate/validators';
import axios from 'axios';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRoute, useRouter } from 'vue-router';
import ChangePassword from '@/account/change-password/change-password.vue';
import { EMAIL_ALREADY_USED_TYPE } from '@/constants';
import { useStore } from '@/store';
import AccessTokenService, { type IAccessToken, type ICreatedAccessToken } from './access-token.service';

const ACCOUNT_TAB = 'account';
const PASSWORD_TAB = 'password';

const MAX_ACCESS_TOKENS = 20;

const resolveTab = (tab: unknown): string => (tab === PASSWORD_TAB ? PASSWORD_TAB : ACCOUNT_TAB);

const tokenErrorMessage = (error: any): string => {
  const data = error?.response?.data;
  return data?.detail || data?.message || data?.title || error?.message || '操作失败，请稍后重试';
};

const validations = {
  settingsAccount: {
    firstName: {
      required,
      minLength: minLength(1),
      maxLength: maxLength(50),
    },
    lastName: {
      required,
      minLength: minLength(1),
      maxLength: maxLength(50),
    },
    email: {
      required,
      email,
      minLength: minLength(5),
      maxLength: maxLength(254),
    },
  },
};

export default defineComponent({
  name: 'Settings',
  validations,
  components: {
    ChangePassword,
  },
  setup() {
    const store = useStore();
    const route = useRoute();
    const router = useRouter();
    const accessTokenService = inject('accessTokenService', () => new AccessTokenService());

    const success: Ref<string> = ref(null);
    const error: Ref<string> = ref(null);
    const errorEmailExists: Ref<string> = ref(null);
    const isSaving: Ref<boolean> = ref(false);
    // 支持 /account/settings?tab=password 直达「修改密码」
    const activeTab: Ref<string> = ref(resolveTab(route.query.tab));

    const accessTokens: Ref<IAccessToken[]> = ref([]);
    const tokensLoading = ref(false);
    const tokenDialogVisible = ref(false);
    const tokenSaving = ref(false);
    const editingToken: Ref<IAccessToken | null> = ref(null);
    const tokenForm = ref({ name: '', description: '' });
    const createdToken: Ref<string | null> = ref(null);
    const createdTokenName = ref('');
    const tokenCopied = ref(false);
    const tokenDisplayVisible = ref(false);

    const settingsAccount = computed(() => store.account);
    const username = inject<ComputedRef<string>>('currentUsername', () => computed(() => store.account?.login), true);
    const pageTitle = computed(() => (username.value ? `[${username.value}] 的个人设置` : '个人设置'));

    watch(
      () => route.query.tab,
      tab => {
        activeTab.value = resolveTab(tab);
      },
    );
    watch(activeTab, tab => {
      router.replace({ path: route.path, query: { ...route.query, tab } });
    });

    return {
      success,
      error,
      errorEmailExists,
      isSaving,
      activeTab,
      settingsAccount,
      username,
      pageTitle,
      accessTokenService,
      accessTokens,
      tokensLoading,
      maxAccessTokens: MAX_ACCESS_TOKENS,
      tokenDialogVisible,
      tokenSaving,
      editingToken,
      tokenForm,
      createdToken,
      createdTokenName,
      tokenCopied,
      tokenDisplayVisible,
      v$: useVuelidate(),
    };
  },
  created() {
    this.loadAccessTokens();
  },
  methods: {
    /** 只在校验过且已改动时提示，避免一进页面满屏红字. */
    fieldError(field: any, message: string): string {
      return field?.$anyDirty && field?.$invalid ? message : '';
    },
    async loadAccessTokens() {
      this.tokensLoading = true;
      try {
        const tokens = await this.accessTokenService().list();
        this.accessTokens = Array.isArray(tokens) ? tokens : [];
      } catch (error) {
        this.accessTokens = [];
        ElMessage.error(tokenErrorMessage(error));
      } finally {
        this.tokensLoading = false;
      }
    },
    openCreateToken() {
      this.editingToken = null;
      this.tokenForm = { name: '', description: '' };
      this.tokenDialogVisible = true;
    },
    openEditToken(token: IAccessToken) {
      this.editingToken = token;
      this.tokenForm = { name: token.name || '', description: token.description || '' };
      this.tokenDialogVisible = true;
    },
    showCreatedToken(result: ICreatedAccessToken) {
      this.createdToken = result.token;
      this.createdTokenName = result.accessToken?.name || '';
      this.tokenCopied = false;
      this.tokenDisplayVisible = true;
    },
    async saveToken() {
      const name = (this.tokenForm.name || '').trim();
      const description = (this.tokenForm.description || '').trim();
      if (!name) {
        ElMessage.warning('请输入访问令牌名称');
        return;
      }
      if (name.length > 50) {
        ElMessage.warning('访问令牌名称不能超过 50 个字符');
        return;
      }
      if (description.length > 255) {
        ElMessage.warning('访问令牌描述不能超过 255 个字符');
        return;
      }
      this.tokenSaving = true;
      try {
        if (this.editingToken) {
          await this.accessTokenService().update(this.editingToken.id, {
            name,
            description,
          });
          ElMessage.success('访问令牌已更新');
        } else {
          const created = await this.accessTokenService().create({
            name,
            description,
          });
          this.showCreatedToken(created);
          ElMessage.success('访问令牌已创建');
        }
        this.tokenDialogVisible = false;
        await this.loadAccessTokens();
      } catch (error) {
        ElMessage.error(tokenErrorMessage(error));
      } finally {
        this.tokenSaving = false;
      }
    },
    async onTokenStatusChange(token: IAccessToken, enabled: boolean) {
      const status = enabled ? 'ENABLED' : 'DISABLED';
      if (token.status === status) {
        return;
      }
      try {
        await this.accessTokenService().update(token.id, { status });
        token.status = status;
        ElMessage.success(status === 'ENABLED' ? '访问令牌已启用' : '访问令牌已停用');
      } catch (error) {
        ElMessage.error(tokenErrorMessage(error));
        await this.loadAccessTokens();
      }
    },
    async rotateToken(token: IAccessToken) {
      try {
        await ElMessageBox.confirm(`确定重新生成访问令牌“${token.name}”吗？旧令牌将立即失效。`, '重新生成访问令牌', {
          type: 'warning',
          confirmButtonText: '重新生成',
          cancelButtonText: '取消',
        });
      } catch {
        return;
      }
      try {
        const created = await this.accessTokenService().rotate(token.id);
        this.showCreatedToken(created);
        ElMessage.success('访问令牌已重新生成');
        await this.loadAccessTokens();
      } catch (error) {
        ElMessage.error(tokenErrorMessage(error));
      }
    },
    async deleteToken(token: IAccessToken) {
      try {
        await ElMessageBox.confirm(`确定删除访问令牌“${token.name}”吗？删除后使用该令牌的客户端将无法继续访问。`, '删除访问令牌', {
          type: 'warning',
          confirmButtonText: '删除',
          cancelButtonText: '取消',
        });
      } catch {
        return;
      }
      try {
        await this.accessTokenService().remove(token.id);
        ElMessage.success('访问令牌已删除');
        await this.loadAccessTokens();
      } catch (error) {
        ElMessage.error(tokenErrorMessage(error));
      }
    },
    async copyCreatedToken() {
      if (!this.createdToken) {
        return;
      }
      try {
        await navigator.clipboard.writeText(this.createdToken);
        this.tokenCopied = true;
        ElMessage.success('已复制');
      } catch {
        this.tokenCopied = false;
        ElMessage.error('复制失败，请手动选择复制');
      }
    },
    formatTokenHint(token: IAccessToken): string {
      return token.tokenHint ? `mcp_••••${token.tokenHint}` : '—';
    },
    formatTokenTime(value: string | null | undefined, fallback: string): string {
      if (!value) {
        return fallback;
      }
      const date = new Date(value);
      return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
    },
    save() {
      this.error = null;
      this.errorEmailExists = null;
      this.isSaving = true;
      return axios
        .post('api/account', this.settingsAccount)
        .then(() => {
          this.error = null;
          this.success = 'OK';
          this.errorEmailExists = null;
        })
        .catch(ex => {
          this.success = null;
          this.error = 'ERROR';
          if (ex.response.status === 400 && ex.response.data.type === EMAIL_ALREADY_USED_TYPE) {
            this.errorEmailExists = 'ERROR';
            this.error = null;
          }
        })
        .finally(() => {
          this.isSaving = false;
        });
    },
  },
});
