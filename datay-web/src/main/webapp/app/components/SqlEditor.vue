<template>
  <div class="sql-editor-shell" :class="{ 'is-fullscreen': isFullscreen }" @keydown="handleKeydown">
    <div class="sql-editor-toolbar">
      <div class="sql-editor-toolbar-left">
        <span v-if="maxlength > 0" class="sql-editor-counter" :class="{ 'is-limit': currentLength >= maxlength }">
          {{ currentLength }} / {{ maxlength }}
        </span>
        <span class="sql-editor-tip">{{ isFullscreen ? 'Esc 退出全屏' : 'Ctrl+Shift+F 格式化' }}</span>
      </div>
      <div class="sql-editor-toolbar-right">
        <el-button size="small" text title="复制 SQL" @click="copySql">
          <font-awesome-icon icon="copy" />
          <span class="sql-editor-btn-label">复制</span>
        </el-button>
        <el-button size="small" text title="格式化 SQL (Ctrl+Shift+F)" @click="formatSql">
          <font-awesome-icon icon="indent" />
          <span class="sql-editor-btn-label">格式化</span>
        </el-button>
        <el-button v-if="showFullscreen" size="small" text :title="isFullscreen ? '退出全屏 (Esc)' : '全屏编辑'" @click="toggleFullscreen">
          <font-awesome-icon :icon="isFullscreen ? 'compress' : 'expand'" />
          <span class="sql-editor-btn-label">{{ isFullscreen ? '退出全屏' : '全屏' }}</span>
        </el-button>
      </div>
    </div>
    <div ref="editorContainer" class="sql-editor-body" :style="bodyStyle"></div>
  </div>
</template>

<script lang="ts" setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';

import { Compartment, EditorState } from '@codemirror/state';
import { EditorView, keymap, placeholder as placeholderExtension } from '@codemirror/view';
import { defaultKeymap, historyKeymap, indentWithTab } from '@codemirror/commands';
import { MSSQL, MySQL, PLSQL, PostgreSQL, StandardSQL, sql, type SQLDialect } from '@codemirror/lang-sql';
import { foldKeymap } from '@codemirror/language';
import { format, type SqlLanguage } from 'sql-formatter';
import { sqlEditorBaseExtensions } from '@/shared/sql/sql-editor-theme';
import type { SqlEditorDialect } from '@/shared/sql/sql-dialect';

const props = withDefaults(
  defineProps<{
    modelValue?: string;
    dialect?: SqlEditorDialect;
    rows?: number;
    maxlength?: number;
    placeholder?: string;
    readonly?: boolean;
    showFullscreen?: boolean;
  }>(),
  {
    modelValue: '',
    dialect: 'sql',
    rows: 8,
    maxlength: 0,
    placeholder: '',
    readonly: false,
    showFullscreen: true,
  },
);

const emit = defineEmits<{
  'update:modelValue': [value: string];
  change: [value: string];
}>();

const editorContainer = ref<HTMLElement | null>(null);
const isFullscreen = ref(false);
const currentLength = ref((props.modelValue || '').length);

let editorView: EditorView | null = null;
const dialectCompartment = new Compartment();

const DEFAULT_LINE_HEIGHT = 21;
const EDITOR_VERTICAL_PADDING = 24;

const editorHeight = computed(() => `${Math.max(props.rows, 3) * DEFAULT_LINE_HEIGHT + EDITOR_VERTICAL_PADDING}px`);

const bodyStyle = computed(() => (isFullscreen.value ? { flex: '1 1 auto', minHeight: '0' } : { height: editorHeight.value }));

/** CodeMirror 语言方言：DuckDB 使用最接近的 PostgreSQL 方言。 */
const languageDialect = computed<SQLDialect>(() => {
  switch (props.dialect) {
    case 'mysql':
    case 'mariadb':
      return MySQL;
    case 'duckdb':
    case 'postgresql':
      return PostgreSQL;
    case 'transactsql':
      return MSSQL;
    case 'plsql':
      return PLSQL;
    default:
      return StandardSQL;
  }
});

/** sql-formatter 格式化方言。 */
const formatterLanguage = computed<SqlLanguage>(() => {
  switch (props.dialect) {
    case 'duckdb':
      return 'duckdb';
    case 'mysql':
      return 'mysql';
    case 'mariadb':
      return 'mariadb';
    case 'postgresql':
      return 'postgresql';
    case 'transactsql':
      return 'transactsql';
    case 'plsql':
      return 'plsql';
    default:
      return 'sql';
  }
});

/** 限制输入长度，但允许删除已有内容；不静默截断历史配置。 */
const maxLengthFilter = EditorState.transactionFilter.of(transaction => {
  if (!props.maxlength || !transaction.docChanged) {
    return transaction;
  }
  const nextLength = transaction.newDoc.length;
  if (nextLength <= props.maxlength) {
    return transaction;
  }
  if (nextLength <= transaction.startState.doc.length) {
    return transaction;
  }
  return [];
});

const updateListener = EditorView.updateListener.of(update => {
  if (!update.docChanged) {
    return;
  }
  const value = update.state.doc.toString();
  currentLength.value = value.length;
  if (value !== (props.modelValue || '')) {
    emit('update:modelValue', value);
    emit('change', value);
  }
});

const initEditor = () => {
  if (!editorContainer.value || editorView) {
    return;
  }

  const extensions = [
    ...sqlEditorBaseExtensions(),
    dialectCompartment.of(sql({ dialect: languageDialect.value })),
    keymap.of([
      indentWithTab,
      {
        key: 'Mod-Shift-f',
        preventDefault: true,
        run: () => {
          formatSql();
          return true;
        },
      },
      ...defaultKeymap,
      ...historyKeymap,
      ...foldKeymap,
    ]),
    updateListener,
    maxLengthFilter,
  ];

  if (props.placeholder) {
    extensions.push(placeholderExtension(props.placeholder));
  }
  if (props.readonly) {
    extensions.push(EditorState.readOnly.of(true), EditorView.editable.of(false));
  }

  editorView = new EditorView({
    state: EditorState.create({
      doc: props.modelValue || '',
      extensions,
    }),
    parent: editorContainer.value,
  });
};

const destroyEditor = () => {
  editorView?.destroy();
  editorView = null;
};

watch(
  () => props.modelValue,
  value => {
    const nextValue = value || '';
    currentLength.value = nextValue.length;
    if (!editorView) {
      return;
    }
    if (nextValue !== editorView.state.doc.toString()) {
      editorView.dispatch({
        changes: { from: 0, to: editorView.state.doc.length, insert: nextValue },
      });
    }
  },
);

watch(
  () => props.dialect,
  () => {
    editorView?.dispatch({
      effects: dialectCompartment.reconfigure(sql({ dialect: languageDialect.value })),
    });
  },
);

const PLACEHOLDER_PATTERN = /\$\{[^}]*\}|#\{[^}]*\}/g;
const MASKED_PLACEHOLDER_PATTERN = /'__DF_SQL_PARAM_(\d+)__'/g;

/**
 * SqlSink、SqlInput 的 SQL 里包含 ${字段}、${attr:xxx}、#{...} 等占位符，
 * sql-formatter 无法直接解析，先替换成字符串字面量，格式化后再还原。
 */
const maskPlaceholders = (text: string): { masked: string; placeholders: string[] } => {
  const placeholders: string[] = [];
  const masked = text.replace(PLACEHOLDER_PATTERN, match => {
    placeholders.push(match);
    return `'__DF_SQL_PARAM_${placeholders.length - 1}__'`;
  });
  return { masked, placeholders };
};

const restorePlaceholders = (text: string, placeholders: string[]): string =>
  text.replace(MASKED_PLACEHOLDER_PATTERN, (_, index: string) => placeholders[Number(index)] ?? '');

const getActiveRange = (): { from: number; to: number; text: string } | null => {
  if (!editorView) {
    return null;
  }
  const selection = editorView.state.selection.main;
  if (!selection.empty) {
    return {
      from: selection.from,
      to: selection.to,
      text: editorView.state.doc.sliceString(selection.from, selection.to),
    };
  }
  return {
    from: 0,
    to: editorView.state.doc.length,
    text: editorView.state.doc.toString(),
  };
};

/** 复制当前 SQL，便于在其它工具中复用。 */
const copySql = async () => {
  const text = editorView?.state.doc.toString() ?? (props.modelValue || '');
  if (!text.trim()) {
    ElMessage({ type: 'warning', message: '请输入 SQL 语句', zIndex: 4000 });
    return;
  }
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
    } else {
      const textarea = document.createElement('textarea');
      textarea.value = text;
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand('copy');
      document.body.removeChild(textarea);
    }
    ElMessage({ type: 'success', message: 'SQL 已复制', zIndex: 4000 });
  } catch {
    ElMessage({ type: 'error', message: '复制失败，请手动选择复制', zIndex: 4000 });
  }
};

const formatSql = () => {
  if (!editorView) {
    return;
  }
  const range = getActiveRange();
  if (!range || !range.text.trim()) {
    ElMessage({ type: 'warning', message: '请输入 SQL 语句', zIndex: 4000 });
    return;
  }

  const { masked, placeholders } = maskPlaceholders(range.text);
  try {
    const formatted = format(masked, {
      language: formatterLanguage.value,
      keywordCase: 'upper',
      linesBetweenQueries: 2,
    });
    editorView.dispatch({
      changes: {
        from: range.from,
        to: range.to,
        insert: restorePlaceholders(formatted, placeholders),
      },
    });
    editorView.focus();
  } catch {
    ElMessage({ type: 'error', message: 'SQL 格式化失败，请检查语法', zIndex: 4000 });
  }
};

const toggleFullscreen = async () => {
  isFullscreen.value = !isFullscreen.value;
  await nextTick();
  editorView?.requestMeasure();
  editorView?.focus();
};

const exitFullscreen = async () => {
  if (!isFullscreen.value) {
    return;
  }
  isFullscreen.value = false;
  await nextTick();
  editorView?.requestMeasure();
};

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape' && isFullscreen.value) {
    event.preventDefault();
    event.stopPropagation();
    void exitFullscreen();
  }
};

onMounted(initEditor);

onBeforeUnmount(destroyEditor);
</script>

<style scoped>
.sql-editor-shell {
  display: flex;
  flex-direction: column;
  width: 100%;
  overflow: hidden;
  background: #ffffff;
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: 4px;
}

.sql-editor-shell.is-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 3000;
  padding: 12px 16px 16px;
  border: none;
  border-radius: 0;
}

.sql-editor-toolbar {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 4px 12px;
  padding: 2px 8px;
  background: var(--el-fill-color-lighter, #f5f7fa);
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
}

.sql-editor-toolbar-left,
.sql-editor-toolbar-right {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.sql-editor-toolbar-right :deep(.el-button) {
  margin-left: 0;
}

.sql-editor-counter {
  color: var(--el-text-color-secondary, #909399);
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
}

.sql-editor-counter.is-limit {
  color: var(--el-color-warning, #e6a23c);
}

.sql-editor-tip {
  color: var(--el-text-color-placeholder, #a8abb2);
  font-size: 12px;
}

.sql-editor-btn-label {
  margin-left: 4px;
}

.sql-editor-body {
  min-height: 0;
  overflow: hidden;
}

.sql-editor-body :deep(.cm-editor) {
  height: 100%;
}

.sql-editor-body :deep(.cm-editor.cm-focused) {
  outline: none;
}

.sql-editor-body :deep(.cm-scroller) {
  overflow: auto;
}
</style>
