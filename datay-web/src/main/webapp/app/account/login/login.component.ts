import { defineComponent } from 'vue';

import LoginForm from '@/account/login-form/login-form.vue';

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: 'Login',
  components: {
    'login-form': LoginForm,
  },
  setup() {
    return {};
  },
});
