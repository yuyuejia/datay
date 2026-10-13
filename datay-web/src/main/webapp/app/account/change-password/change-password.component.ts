import { type ComputedRef, type Ref, defineComponent, inject, ref } from 'vue';
import { useVuelidate } from '@vuelidate/core';
import { maxLength, minLength, required, sameAs } from '@vuelidate/validators';
import axios from 'axios';

export default defineComponent({
  name: 'ChangePassword',
  validations() {
    return {
      resetPassword: {
        currentPassword: {
          required,
        },
        newPassword: {
          required,
          minLength: minLength(4),
          maxLength: maxLength(100),
        },
        confirmPassword: {
          sameAsPassword: sameAs(this.resetPassword.newPassword),
        },
      },
    };
  },
  setup() {
    const username = inject<ComputedRef<string>>('currentUsername');
    const success: Ref<string | null> = ref(null);
    const error: Ref<string | null> = ref(null);
    const doNotMatch: Ref<string | null> = ref(null);
    const isSaving: Ref<boolean> = ref(false);
    const resetPassword: Ref<any> = ref({
      currentPassword: null,
      newPassword: null,
      confirmPassword: null,
    });

    return {
      username,
      success,
      error,
      doNotMatch,
      isSaving,
      resetPassword,
      v$: useVuelidate(),
    };
  },
  methods: {
    /** 只在校验过且已改动时提示，避免一进页面满屏红字. */
    fieldError(field: any, message: string): string {
      return field?.$anyDirty && field?.$invalid ? message : '';
    },
    clearForm(): void {
      this.resetPassword = {
        currentPassword: null,
        newPassword: null,
        confirmPassword: null,
      };
      this.v$.resetPassword?.$reset?.();
    },
    /** 清空输入并复位提示信息. */
    resetForm(): void {
      this.clearForm();
      this.success = null;
      this.error = null;
      this.doNotMatch = null;
    },
    changePassword(): void {
      if (this.resetPassword.newPassword !== this.resetPassword.confirmPassword) {
        this.error = null;
        this.success = null;
        this.doNotMatch = 'ERROR';
        return;
      }
      this.doNotMatch = null;
      this.error = null;
      this.success = null;
      this.isSaving = true;
      axios
        .post('api/account/change-password', {
          currentPassword: this.resetPassword.currentPassword,
          newPassword: this.resetPassword.newPassword,
        })
        .then(() => {
          this.success = 'OK';
          this.error = null;
          // 修改成功后清空密码输入，避免明文密码停留在页面上
          this.clearForm();
        })
        .catch(() => {
          this.success = null;
          this.error = 'ERROR';
        })
        .finally(() => {
          this.isSaving = false;
        });
    },
  },
});
