<template>
  <el-dialog
    :model-value="visible"
    :title="hasHeader ? undefined : title"
    :width="dialogWidth"
    :close-on-click-modal="closeOnBackdrop"
    append-to-body
    @update:model-value="onUpdateModelValue"
    @closed="onClosed"
  >
    <template v-if="hasHeader" #header>
      <slot name="modal-title" />
    </template>
    <slot />
    <template v-if="hasFooter" #footer>
      <slot name="modal-footer" />
    </template>
    <template v-else-if="!okOnly" #footer>
      <el-button @click="hide">{{ cancelTitle || '取消' }}</el-button>
      <el-button type="primary" :disabled="okDisabled" @click="onOk">{{ okTitle || '确定' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, useSlots } from 'vue';

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: undefined,
  },
  title: {
    type: String,
    default: '',
  },
  size: {
    type: String,
    default: 'md',
  },
  scrollable: {
    type: Boolean,
    default: false,
  },
  okOnly: {
    type: Boolean,
    default: false,
  },
  okTitle: {
    type: String,
    default: '',
  },
  cancelTitle: {
    type: String,
    default: '',
  },
  okDisabled: {
    type: Boolean,
    default: false,
  },
  closeOnBackdrop: {
    type: Boolean,
    default: true,
  },
});

const emit = defineEmits(['update:modelValue', 'ok', 'hidden']);
const slots = useSlots();

const innerVisible = ref(false);
const hasHeader = computed(() => !!slots['modal-title']);
const hasFooter = computed(() => !!slots['modal-footer']);
const visible = computed(() => (props.modelValue !== undefined ? props.modelValue : innerVisible.value));
const dialogWidth = computed(() => {
  switch (props.size) {
    case 'sm':
      return '400px';
    case 'lg':
      return '700px';
    case 'xl':
      return '900px';
    default:
      return '500px';
  }
});

function show() {
  if (props.modelValue !== undefined) {
    emit('update:modelValue', true);
  } else {
    innerVisible.value = true;
  }
}

function hide() {
  if (props.modelValue !== undefined) {
    emit('update:modelValue', false);
  } else {
    innerVisible.value = false;
  }
}

function onUpdateModelValue(value: boolean) {
  if (props.modelValue !== undefined) {
    emit('update:modelValue', value);
  } else {
    innerVisible.value = value;
  }
}

function onOk() {
  emit('ok');
  hide();
}

function onClosed() {
  emit('hidden');
}

defineExpose({ show, hide });
</script>

<style scoped>
:deep(.el-dialog__body) {
  max-height: 70vh;
  overflow-y: auto;
}
</style>
