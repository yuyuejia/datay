import { type PropType, computed, defineComponent, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import ShellJobService from './shell-job.service';
import ETLTaskDirectoryService from '@/entities/etl-task/etl-task-directory.service';
import { type IJob } from '@/shared/model/job.model';
import { type IETLTaskDirectory } from '@/shared/model/etl-task-directory.model';
import { useAlertService } from '@/shared/alert/alert.service';

import { EditorState } from '@codemirror/state';
import { EditorView, keymap, lineNumbers, highlightActiveLine, highlightActiveLineGutter } from '@codemirror/view';
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands';
import { StreamLanguage } from '@codemirror/language';
import { shell } from '@codemirror/legacy-modes/mode/shell';
import { oneDark } from '@codemirror/theme-one-dark';

/** Shell 任务目录在公共目录表中的类型标识。 */
const DIRECTORY_TASK_TYPE = 'SHELL';

interface IDirectoryOption {
  id: string;
  label: string;
  children?: IDirectoryOption[];
}

export default defineComponent({
  name: 'ShellJobEditor',
  props: {
    jobId: {
      type: String,
      default: null,
    },
    directoryId: {
      type: String,
      default: null,
    },
    directories: {
      type: Array as PropType<IETLTaskDirectory[]>,
      default: () => [],
    },
    standalone: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['saved'],
  setup(props, { emit }) {
    const shellJobService = inject('shellJobService', () => new ShellJobService());
    const etlTaskDirectoryService = inject('etlTaskDirectoryService', () => new ETLTaskDirectoryService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const isEditMode = computed(() => !!props.jobId);
    const isSaving = ref(false);

    const shellJob = ref<IJob>({
      jobName: '',
      cron: '',
      status: 'OFFLINE',
      directoryId: props.directoryId || null,
    });
    const localDirectories = ref<IETLTaskDirectory[]>([]);
    const directories = computed<IETLTaskDirectory[]>(() =>
      props.directories && props.directories.length > 0 ? props.directories : localDirectories.value,
    );

    const directoryTreeOptions = computed<IDirectoryOption[]>(() => {
      const byParent = new Map<string, IETLTaskDirectory[]>();
      directories.value.forEach(directory => {
        const parentId = directory.parentId || '';
        if (!byParent.has(parentId)) {
          byParent.set(parentId, []);
        }
        byParent.get(parentId)!.push(directory);
      });
      const build = (parentId: string): IDirectoryOption[] =>
        (byParent.get(parentId) || [])
          .slice()
          .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0) || String(a.name || '').localeCompare(String(b.name || '')))
          .map(directory => {
            const children = build(directory.id!);
            return {
              id: directory.id!,
              label: directory.name || '',
              children: children.length ? children : undefined,
            };
          });
      return build('');
    });

    const shellScript = ref('');
    const editorContainer = ref<HTMLElement | null>(null);
    let editorView: EditorView | null = null;

    const initEditor = () => {
      if (!editorContainer.value) return;

      const updateListener = EditorView.updateListener.of(v => {
        if (v.docChanged) {
          shellScript.value = v.state.doc.toString();
        }
      });

      const saveKeymap = keymap.of([
        {
          key: 'Mod-s',
          preventDefault: true,
          run: () => {
            save();
            return true;
          },
        },
      ]);

      const state = EditorState.create({
        doc: shellScript.value,
        extensions: [
          lineNumbers(),
          highlightActiveLine(),
          highlightActiveLineGutter(),
          history(),
          keymap.of([...defaultKeymap, ...historyKeymap]),
          saveKeymap,
          StreamLanguage.define(shell),
          oneDark,
          updateListener,
          EditorState.tabSize.of(2),
        ],
      });

      editorView = new EditorView({
        state,
        parent: editorContainer.value,
      });
    };

    const destroyEditor = () => {
      if (editorView) {
        editorView.destroy();
        editorView = null;
      }
    };

    const loadDirectories = async () => {
      if (props.directories && props.directories.length > 0) {
        return;
      }
      try {
        const res = await etlTaskDirectoryService().retrieve(DIRECTORY_TASK_TYPE);
        localDirectories.value = res.data || [];
      } catch (err: any) {
        alertService.showHttpError(err.response);
      }
    };

    watch(
      () => props.directories,
      value => {
        localDirectories.value = (value || []) as IETLTaskDirectory[];
      },
    );

    // 新建任务时，左侧目录切换后同步更新任务归属目录。
    watch(
      () => props.directoryId,
      value => {
        if (!props.jobId) {
          shellJob.value.directoryId = value || null;
        }
      },
    );

    const loadShellJob = async () => {
      if (!props.jobId) {
        shellJob.value = {
          jobName: '',
          cron: '',
          status: 'OFFLINE',
          directoryId: props.directoryId || null,
        };
        shellScript.value = '';
        return;
      }
      try {
        const job = await shellJobService().find(String(props.jobId));
        shellJob.value = {
          ...job,
          directoryId: job.directoryId ?? null,
        };
        shellScript.value = job.jobContext || '';
      } catch (error: any) {
        alertService.showHttpError(error.response);
      }
    };

    const save = async () => {
      if (!shellJob.value.jobName || !shellJob.value.jobName.trim()) {
        alertService.showError('请输入任务名称');
        return;
      }
      if (!shellScript.value.trim()) {
        alertService.showError('请输入 Shell 脚本');
        return;
      }

      isSaving.value = true;
      const jobContext = shellScript.value;
      try {
        let saved: IJob;
        if (isEditMode.value) {
          const entity: IJob = {
            ...shellJob.value,
            jobName: shellJob.value.jobName.trim(),
            type: 'SHELL',
            cron: shellJob.value.cron || '',
            directoryId: shellJob.value.directoryId || null,
            jobContext,
            updateTime: new Date(),
          };
          saved = await shellJobService().update(entity);
          alertService.showSuccess('Shell 任务保存成功');
        } else {
          const entity: IJob = {
            jobName: shellJob.value.jobName.trim(),
            jobGroup: 'datafusion',
            type: 'SHELL',
            cron: shellJob.value.cron || '',
            status: 'OFFLINE',
            directoryId: shellJob.value.directoryId || null,
            jobContext,
            createTime: new Date(),
            updateTime: new Date(),
          };
          saved = await shellJobService().create(entity);
          alertService.showSuccess('Shell 任务创建成功');
        }
        emit('saved', saved);
      } catch (error: any) {
        alertService.showHttpError(error.response);
      } finally {
        isSaving.value = false;
      }
    };

    onMounted(async () => {
      await Promise.all([loadDirectories(), loadShellJob()]);
      initEditor();
    });

    onBeforeUnmount(() => {
      destroyEditor();
    });

    return {
      isEditMode,
      isSaving,
      shellJob,
      directoryTreeOptions,
      shellScript,
      editorContainer,
      save,
    };
  },
});
