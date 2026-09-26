import { type ComputedRef, type Ref, computed, defineComponent, inject, ref } from 'vue';
import { useVuelidate } from '@vuelidate/core';
import { email, maxLength, minLength, required } from '@vuelidate/validators';
import axios from 'axios';
import { EMAIL_ALREADY_USED_TYPE } from '@/constants';
import { useStore } from '@/store';

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
  setup() {
    const store = useStore();

    const success: Ref<string> = ref(null);
    const error: Ref<string> = ref(null);
    const errorEmailExists: Ref<string> = ref(null);
    const hasMcpToken: Ref<boolean> = ref(false);
    const generatedToken: Ref<string> = ref(null);
    const tokenCopied: Ref<boolean> = ref(false);

    const settingsAccount = computed(() => store.account);
    const username = inject<ComputedRef<string>>('currentUsername', () => computed(() => store.account?.login), true);

    return {
      success,
      error,
      errorEmailExists,
      hasMcpToken,
      generatedToken,
      tokenCopied,
      settingsAccount,
      username,
      v$: useVuelidate(),
    };
  },
  created() {
    this.loadMcpTokenStatus();
  },
  methods: {
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
        });
    },
  },
});
