import { defineStore } from 'pinia';

export const useLoginModal = defineStore('login', () => {
  async function showLogin() {
    const { default: router } = await import('@/router');
    router.push({ name: 'Login' });
  }

  return {
    showLogin,
  };
});
