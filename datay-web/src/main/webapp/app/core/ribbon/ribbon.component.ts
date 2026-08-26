import { computed, defineComponent } from 'vue';
import { useStore } from '@/store';

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: 'Ribbon',
  setup() {
    const store = useStore();
    const ribbonEnv = computed(() => store.ribbonOnProfiles);
    const ribbonEnabled = computed(() => store.ribbonOnProfiles && store.activeProfiles.indexOf(store.ribbonOnProfiles) > -1);

    return {
      ribbonEnv,
      ribbonEnabled,
    };
  },
});
