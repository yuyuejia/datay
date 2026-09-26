import {
  defineComponent,
  inject,
  onMounted,
  ref,
  onBeforeUnmount,
  computed,
} from "vue";
import { useRoute, useRouter } from "vue-router";
import ShellJobService from "./shell-job.service";
import { type IJob } from "@/shared/model/job.model";
import { useAlertService } from "@/shared/alert/alert.service";
import CronExpressionSelector from "@/components/CronExpressionSelector.vue";

import { EditorState } from "@codemirror/state";
import {
  EditorView,
  keymap,
  lineNumbers,
  highlightActiveLine,
  highlightActiveLineGutter,
} from "@codemirror/view";
import { defaultKeymap, history, historyKeymap } from "@codemirror/commands";
import { StreamLanguage } from "@codemirror/language";
import { shell } from "@codemirror/legacy-modes/mode/shell";
import { oneDark } from "@codemirror/theme-one-dark";

export default defineComponent({
  name: "ShellJobUpdate",
  components: {
    CronExpressionSelector,
  },
  setup() {
    const route = useRoute();
    const router = useRouter();
    const shellJobService = inject(
      "shellJobService",
      () => new ShellJobService(),
    );
    const alertService = inject("alertService", () => useAlertService(), true);

    const isEditMode = computed(() => !!route.params.jobId);
    const isSaving = ref(false);

    const shellJob = ref<IJob>({ jobName: "", cron: "", status: "OFFLINE" });
    const shellScript = ref("");
    const editorContainer = ref<HTMLElement | null>(null);
    let editorView: EditorView | null = null;

    const initEditor = () => {
      if (!editorContainer.value) return;

      const updateListener = EditorView.updateListener.of((v) => {
        if (v.docChanged) {
          shellScript.value = v.state.doc.toString();
        }
      });

      const saveKeymap = keymap.of([
        {
          key: "Mod-s",
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

    const loadShellJob = async () => {
      if (!isEditMode.value) {
        shellJob.value = { jobName: "", cron: "", status: "OFFLINE" };
        shellScript.value = "";
        return;
      }
      try {
        const job = await shellJobService().find(Number(route.params.jobId));
        shellJob.value = job;
        shellScript.value = job.jobContext || "";
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const save = async () => {
      if (!shellJob.value.jobName || !shellJob.value.jobName.trim()) {
        alertService.showError("请输入任务名称");
        return;
      }
      if (!shellScript.value.trim()) {
        alertService.showError("请输入 Shell 脚本");
        return;
      }

      isSaving.value = true;
      const jobContext = shellScript.value;
      try {
        if (isEditMode.value) {
          const entity: IJob = {
            ...shellJob.value,
            jobName: shellJob.value.jobName.trim(),
            type: "SHELL",
            cron: shellJob.value.cron || "",
            jobContext,
            updateTime: new Date(),
          };
          await shellJobService().update(entity);
          alertService.showSuccess("Shell 任务保存成功");
        } else {
          const entity: IJob = {
            jobName: shellJob.value.jobName.trim(),
            jobGroup: "datafusion",
            type: "SHELL",
            cron: shellJob.value.cron || "",
            status: "OFFLINE",
            jobContext,
            createTime: new Date(),
            updateTime: new Date(),
          };
          await shellJobService().create(entity);
          alertService.showSuccess("Shell 任务创建成功");
        }
        router.push({ name: "ShellJob" });
      } catch (error) {
        alertService.showHttpError(error.response);
      } finally {
        isSaving.value = false;
      }
    };

    onMounted(async () => {
      await loadShellJob();
      initEditor();
    });

    onBeforeUnmount(() => {
      destroyEditor();
    });

    return {
      isEditMode,
      isSaving,
      shellJob,
      shellScript,
      editorContainer,
      save,
    };
  },
});
