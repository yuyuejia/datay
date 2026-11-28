import { EditorState, type Extension } from '@codemirror/state';
import { EditorView, highlightActiveLine, highlightActiveLineGutter, lineNumbers } from '@codemirror/view';
import { history } from '@codemirror/commands';
import {
  bracketMatching,
  codeFolding,
  defaultHighlightStyle,
  foldGutter,
  indentOnInput,
  indentUnit,
  syntaxHighlighting,
} from '@codemirror/language';

/**
 * 统一的 SQL 编辑器浅色主题，和 Element Plus 表单风格保持一致。
 */
export const sqlEditorLightTheme: Extension = [
  EditorView.theme(
    {
      '&': {
        color: '#303133',
        backgroundColor: '#ffffff',
        fontSize: '13px',
      },
      '.cm-content': {
        caretColor: '#409eff',
        fontFamily: "'Menlo', 'Monaco', 'Courier New', monospace",
      },
      '.cm-cursor, .cm-dropCursor': {
        borderLeftColor: '#409eff',
      },
      '.cm-content::selection': {
        backgroundColor: '#a0cfff',
        color: '#1f2937',
      },
      '.cm-content ::selection': {
        backgroundColor: '#a0cfff',
        color: '#1f2937',
      },
      '.cm-gutters': {
        backgroundColor: '#f5f7fa',
        color: '#a8abb2',
        border: 'none',
        borderRight: '1px solid #e4e7ed',
      },
      '.cm-activeLine': {
        backgroundColor: '#f5f7fa',
      },
      '.cm-activeLineGutter': {
        backgroundColor: '#eef1f6',
        color: '#606266',
      },
      '.cm-foldPlaceholder': {
        backgroundColor: '#eef1f6',
        border: '1px solid #dcdfe6',
        color: '#909399',
      },
      '.cm-scroller': {
        fontFamily: "'Menlo', 'Monaco', 'Courier New', monospace",
        lineHeight: '1.5',
      },
      '.cm-tooltip': {
        border: '1px solid #e4e7ed',
        backgroundColor: '#ffffff',
      },
    },
    { dark: false },
  ),
  syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
];

/**
 * SQL 编辑器通用扩展：行号、当前行、历史、自动缩进、括号匹配、代码折叠、自动换行和浅色主题。
 *
 * <p>调用方只需再追加语言、快捷键、补全和自定义监听扩展。</p>
 */
export function sqlEditorBaseExtensions(): Extension[] {
  return [
    lineNumbers(),
    highlightActiveLine(),
    highlightActiveLineGutter(),
    history(),
    indentOnInput(),
    indentUnit.of('  '),
    EditorState.tabSize.of(2),
    bracketMatching(),
    codeFolding(),
    foldGutter(),
    EditorView.lineWrapping,
    sqlEditorLightTheme,
  ];
}
