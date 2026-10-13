import { type ComputedRef, type Ref, computed, defineComponent, inject, ref, watch } from 'vue';
import { useVuelidate } from '@vuelidate/core';
import { email, maxLength, minLength, required } from '@vuelidate/validators';
import axios from 'axios';
import { useRoute, useRouter } from 'vue-router';
import ChangePassword from '@/account/change-password/change-password.vue';
import { EMAIL_ALREADY_USED_TYPE } from '@/constants';
import { useStore } from '@/store';

const ACCOUNT_TAB = 'account';
const PASSWORD_TAB = 'password';

const resolveTab = (tab: unknown): string => (tab === PASSWORD_TAB ? PASSWORD_TAB : ACCOUNT_TAB);

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

    const success: Ref<string> = ref(null);
    const error: Ref<string> = ref(null);
    const errorEmailExists: Ref<string> = ref(null);
    const hasMcpToken: Ref<boolean> = ref(false);
    const generatedToken: Ref<string> = ref(null);
    const tokenCopied: Ref<boolean> = ref(false);
    const isSaving: Ref<boolean> = ref(false);
    // 支持 /account/settings?tab=password 直达「修改密码」
    const activeTab: Ref<string> = ref(resolveTab(route.query.tab));

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
      hasMcpToken,
      generatedToken,
      tokenCopied,
      isSaving,
      activeTab,
      settingsAccount,
      username,
      pageTitle,
      v$: useVuelidate(),
    };
  },
  created() {
    this.loadMcpTokenStatus();
  },
  methods: {
    /** 只在校验过且已改动时提示，避免一进页面满屏红字. */
    fieldError(field: any, message: string): string {
      return field?.$anyDirty && field?.$invalid ? message : '';
    },
    loadMcpTokenStatus() {
      return axios
        .get('api/account/mcp-token')
        .then(res => {
          this.hasMcpToken = res.data?.hasToken === true;
        })
        .catch(() => {
          this.hasMcpToken = false;
        });
    },
    generateMcpToken() {
      this.generatedToken = null;
      this.tokenCopied = false;
      return axios
        .post('api/account/mcp-token')
        .then(res => {
          this.hasMcpToken = true;
          this.generatedToken = res.data?.token || null;
        })
        .catch(() => {
          this.generatedToken = null;
        });
    },
    async copyToken() {
      if (!this.generatedToken) {
        return;
      }
      try {
        await navigator.clipboard.writeText(this.generatedToken);
        this.tokenCopied = true;
      } catch {
        this.tokenCopied = false;
      }
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
