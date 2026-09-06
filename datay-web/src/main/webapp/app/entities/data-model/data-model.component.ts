import { type Ref, defineComponent, inject, nextTick, onMounted, ref, computed } from "vue";
import { useRoute, useRouter } from "vue-router";

import DataModelService from "./data-model.service";
import DataSourceService from "../data-source/data-source.service";
import ModelDirectoryService from "./model-directory.service";
import { type IDataModel } from "@/shared/model/data-model.model";
import {
  type IModelDirectory,
  ModelDirectory,
} from "@/shared/model/model-directory.model";
import { type IModelField } from "@/shared/model/model-field.model";
import { useDateFormat } from "@/shared/composables";
import { useAlertService } from "@/shared/alert/alert.service";

interface TreeNode {
  id: number;
  label: string;
  type: "directory" | "model";
  children?: TreeNode[];
  data?: any;
}

const FIELD_TYPE_LABELS: Record<string, string> = {
  VARCHAR: "字符串",
  TEXT: "大文本",
  BINARY: "二进制",
  BLOB: "大对象(BLOB)",
  INTEGER: "整数",
  LONG: "长整型",
  DOUBLE: "双精度",
  DECIMAL: "高精度数值",
  DATE: "日期",
  DATETIME: "日期时间",
  BOOLEAN: "布尔",
};

const FIELD_TYPES_WITH_LENGTH_ONLY = ["VARCHAR"];
const FIELD_TYPES_WITH_PRECISION_AND_SCALE = ["DECIMAL", "DOUBLE"];

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: "DataModel",
  setup() {
    const dateFormat = useDateFormat();
    const dataModelService = inject(
      "dataModelService",
      () => new DataModelService(),
    );
    const dataSourceService = inject(
      "dataSourceService",
      () => new DataSourceService(),
    );
    const modelDirectoryService = inject(
      "modelDirectoryService",
      () => new ModelDirectoryService(),
    );
    const alertService = inject("alertService", () => useAlertService(), true);

    const router = useRouter();
    const route = useRoute();

    const treeData: Ref<TreeNode[]> = ref([]);
    const treeProps = { children: "children", label: "label" };
    const treeRef = ref(null);

    const selectedModel: Ref<IDataModel | null> = ref(null);
    const modelFields: Ref<IModelField[]> = ref([]);
    const fieldsLoading = ref(false);

    const directoryDialogVisible = ref(false);
    const directoryDialogTitle = ref("新增目录");
    const directoryForm: Ref<IModelDirectory> = ref(new ModelDirectory());
    const editingDirectoryId: Ref<number | null> = ref(null);

    const deleteDialogVisible = ref(false);
    const deleteMessage = ref("");
    const deleteTarget: Ref<any> = ref(null);
    const deleteType: Ref<string> = ref("");

    const materializeDialogVisible = ref(false);
    const materializeLoading = ref(false);
    const dataSources: Ref<any[]> = ref([]);
    const materializeSchemas: Ref<string[]> = ref([]);
    const materializeTableExists = ref(false);
    const materializeDDLPreview = ref("");
    const materializePhysicalTypes: Ref<string[]> = ref([]);

    const materializeForm = ref({
      dataSourceId: null as number | null,
      schemaName: "",
      tableName: "",
    });

    const materializeFields: Ref<any[]> = ref([]);

    const canMaterialize = computed(() => {
      return (
        materializeForm.value.dataSourceId !== null &&
        materializeForm.value.tableName.trim() !== "" &&
        materializeFields.value.length > 0 &&
        materializeFields.value.every((f) => f.physicalType && f.physicalType.trim() !== "")
      );
    });

    const needsMaterializeLength = (logicalType: string) => {
      return FIELD_TYPES_WITH_LENGTH_ONLY.includes((logicalType || "").toUpperCase());
    };

    const needsMaterializePrecision = (logicalType: string) => {
      return FIELD_TYPES_WITH_PRECISION_AND_SCALE.includes((logicalType || "").toUpperCase());
    };

    const needsMaterializeScale = (logicalType: string) => {
      return FIELD_TYPES_WITH_PRECISION_AND_SCALE.includes((logicalType || "").toUpperCase());
    };

    const openMaterializeDialog = async () => {
      if (!selectedModel.value) {
        alertService.showWarning("请先选择一个模型");
        return;
      }
      materializeDialogVisible.value = true;
      materializeDDLPreview.value = "";
      materializeTableExists.value = false;
      materializeForm.value = {
        dataSourceId: selectedModel.value.dataSourceId ?? null,
        schemaName: selectedModel.value.schemaName || "",
        tableName: selectedModel.value.tableName || selectedModel.value.code || selectedModel.value.name || "",
      };
      materializeFields.value = [];
      materializeSchemas.value = [];
      materializePhysicalTypes.value = [];

      try {
        const dsRes = await dataSourceService().retrieve();
        dataSources.value = dsRes.data || [];
      } catch (err) {
        alertService.showHttpError(err.response);
      }

      if (materializeForm.value.dataSourceId) {
        await onDataSourceChange();
      }
    };

    const onDataSourceChange = async () => {
      const dsId = materializeForm.value.dataSourceId;
      if (!dsId) return;
      materializeLoading.value = true;
      materializeTableExists.value = false;
      materializeDDLPreview.value = "";

      try {
        const res = await dataModelService().getMaterializeFields(selectedModel.value!.id!, dsId);
        materializeFields.value = res.data || [];

        try {
          const typeRes = await dataModelService().getSupportedPhysicalTypes(dsId);
          materializePhysicalTypes.value = typeRes.data || [];
        } catch (e) {
          materializePhysicalTypes.value = [];
        }

        try {
          const schemaRes = await dataSourceService().getSchemas(dsId);
          materializeSchemas.value = schemaRes.data || [];
          if (materializeSchemas.value.length === 0) {
            materializeSchemas.value = ["public"];
          }
          const currentSchema = materializeForm.value.schemaName;
          if (currentSchema && materializeSchemas.value.includes(currentSchema)) {
            materializeForm.value.schemaName = currentSchema;
          } else {
            materializeForm.value.schemaName = materializeSchemas.value[0] || "";
          }
        } catch (e) {
          materializeSchemas.value = [];
          materializeForm.value.schemaName = "";
        }

        await checkMaterializeTableExists();
      } catch (err) {
        alertService.showHttpError(err.response);
      } finally {
        materializeLoading.value = false;
      }
    };

    const checkMaterializeTableExists = async () => {
      if (!materializeForm.value.dataSourceId || !materializeForm.value.tableName) return;

      try {
        const res = await dataModelService().checkMaterialize(selectedModel.value!.id!, {
          dataSourceId: materializeForm.value.dataSourceId,
          schemaName: materializeForm.value.schemaName,
          tableName: materializeForm.value.tableName,
        });
        materializeTableExists.value = res.data?.tableExists || false;
      } catch (err) {
        console.warn("检查表存在性失败", err);
      }
    };

    const previewMaterializeDDL = async () => {
      if (!canMaterialize.value) {
        alertService.showWarning("请先完善物化信息");
        return;
      }
      materializeLoading.value = true;
      try {
        const res = await dataModelService().generateMaterializeDDL(selectedModel.value!.id!, {
          dataSourceId: materializeForm.value.dataSourceId,
          schemaName: materializeForm.value.schemaName,
          tableName: materializeForm.value.tableName,
          fields: materializeFields.value,
        });
        materializeDDLPreview.value = res.data?.ddl || res.data || "";
      } catch (err) {
        alertService.showHttpError(err.response);
      } finally {
        materializeLoading.value = false;
      }
    };

    const confirmMaterialize = async () => {
      if (!canMaterialize.value) {
        alertService.showWarning("请先完善物化信息");
        return;
      }

      materializeLoading.value = true;
      try {
        const res = await dataModelService().materialize(selectedModel.value!.id!, {
          dataSourceId: materializeForm.value.dataSourceId,
          schemaName: materializeForm.value.schemaName,
          tableName: materializeForm.value.tableName,
          overwrite: true,
          fields: materializeFields.value,
        });

        const result = res.data;
        if (result?.success) {
          alertService.showSuccess(`物化成功！已生成表 ${materializeForm.value.schemaName ? materializeForm.value.schemaName + '.' : ''}${materializeForm.value.tableName}`);
          materializeDialogVisible.value = false;
          const updated = await dataModelService().find(selectedModel.value!.id!);
          selectedModel.value = updated;
          const updateNode = (nodes: TreeNode[]): boolean => {
            for (const node of nodes) {
              if (node.type === "model" && node.id === updated.id) {
                node.data = updated;
                return true;
              }
              if (node.children && updateNode(node.children)) {
                return true;
              }
            }
            return false;
          };
          updateNode(treeData.value);
        } else {
          alertService.showError(result?.message || "物化失败");
        }
      } catch (err: any) {
        alertService.showError(err?.response?.data?.message || "物化失败");
      } finally {
        materializeLoading.value = false;
      }
    };

    const getDataSourceName = (dataSourceId: number | null | undefined): string => {
      if (!dataSourceId) return "-";
      const ds = dataSources.value.find((d) => d.id === dataSourceId);
      return ds?.name || String(dataSourceId);
    };

    const getPhysicalTableName = (model: IDataModel | null): string => {
      if (!model) return "-";
      const parts: string[] = [];
      if (model.schemaName) parts.push(model.schemaName);
      if (model.tableName) parts.push(model.tableName);
      return parts.length > 0 ? parts.join(".") : "-";
    };

    const loadTree = async () => {
      try {
        const dirRes = await modelDirectoryService().retrieve();
        const directories: IModelDirectory[] = dirRes.data;
        const modelRes = await dataModelService().retrieve();
        const models: IDataModel[] = modelRes.data;

        const modelMap = new Map<number, IDataModel[]>();
        for (const model of models) {
          const dirId = model.directoryId || 0;
          if (!modelMap.has(dirId)) {
            modelMap.set(dirId, []);
          }
          modelMap.get(dirId)!.push(model);
        }

        const buildTree = (parentId: number | null): TreeNode[] => {
          const children: TreeNode[] = [];
          const dirs = directories.filter(
            (d) => (d.parentId || null) === parentId,
          );
          dirs.sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
          for (const dir of dirs) {
            const node: TreeNode = {
              id: dir.id!,
              label: dir.name || "",
              type: "directory",
              children: [],
              data: dir,
            };
            const subChildren = buildTree(dir.id!);
            const dirModels = modelMap.get(dir.id!) || [];
            for (const model of dirModels) {
              subChildren.push({
                id: model.id!,
                label: model.name || "",
                type: "model",
                data: model,
              });
            }
            node.children = subChildren.length > 0 ? subChildren : undefined;
            children.push(node);
          }
          return children;
        };

        const rootNodes = buildTree(null);
        const rootModels = modelMap.get(0) || [];
        for (const model of rootModels) {
          rootNodes.push({
            id: model.id!,
            label: model.name || "",
            type: "model",
            data: model,
          });
        }
        treeData.value = rootNodes;
      } catch (err) {
        alertService.showHttpError(err.response);
      }
    };

    const handleNodeClick = async (data: TreeNode) => {
      if (data.type === "model") {
        selectedModel.value = data.data;
        await loadModelFields(data.data.id!);
      } else {
        selectedModel.value = null;
        modelFields.value = [];
      }
    };

    const loadModelFields = async (modelId: number) => {
      fieldsLoading.value = true;
      try {
        const res = await dataModelService().getFields(modelId);
        const fields: IModelField[] = res.data;
        for (const field of fields) {
          if (field.dimensionModelId) {
            try {
              const dimRes = await dataModelService().find(
                field.dimensionModelId,
              );
              (field as any)._dimensionModelName = dimRes.name;
            } catch (e) {
              (field as any)._dimensionModelName = String(
                field.dimensionModelId,
              );
            }
          }
        }
        modelFields.value = fields;
      } catch (err) {
        alertService.showHttpError(err.response);
      } finally {
        fieldsLoading.value = false;
      }
    };

    const showAddDirectoryDialog = (parentId: number | null) => {
      editingDirectoryId.value = null;
      directoryDialogTitle.value = "新增目录";
      directoryForm.value = new ModelDirectory();
      directoryForm.value.parentId = parentId;
      directoryForm.value.sortOrder = 0;
      directoryDialogVisible.value = true;
    };

    const saveDirectory = async () => {
      try {
        if (editingDirectoryId.value) {
          directoryForm.value.id = editingDirectoryId.value;
          await modelDirectoryService().update(directoryForm.value);
        } else {
          await modelDirectoryService().create(directoryForm.value);
        }
        directoryDialogVisible.value = false;
        alertService.showSuccess("目录保存成功");
        await loadTree();
      } catch (err) {
        alertService.showHttpError(err.response);
      }
    };

    const showAddModelDialog = (directoryId: number) => {
      router.push({
        name: "DataModelCreate",
        query: { directoryId: String(directoryId) },
      });
    };

    const handleDeleteNode = (data: TreeNode) => {
      deleteTarget.value = data;
      deleteType.value = data.type;
      if (data.type === "directory") {
        deleteMessage.value = `确定要删除目录"${data.label}"吗？删除后该目录下的所有模型将移到根目录。`;
      } else {
        deleteMessage.value = `确定要删除数据模型"${data.label}"吗？删除后模型的所有字段也将被删除。`;
      }
      deleteDialogVisible.value = true;
    };

    const prepareRemove = (model: IDataModel) => {
      deleteTarget.value = { id: model.id, label: model.name, type: "model" };
      deleteType.value = "model";
      deleteMessage.value = `确定要删除数据模型"${model.name}"吗？删除后模型的所有字段也将被删除。`;
      deleteDialogVisible.value = true;
    };

    const confirmDelete = async () => {
      try {
        if (deleteType.value === "directory") {
          await modelDirectoryService().delete(deleteTarget.value.id);
          alertService.showSuccess("目录删除成功");
        } else {
          await dataModelService().delete(deleteTarget.value.id);
          if (selectedModel.value?.id === deleteTarget.value.id) {
            selectedModel.value = null;
            modelFields.value = [];
          }
          alertService.showSuccess("模型删除成功");
        }
        deleteDialogVisible.value = false;
        await loadTree();
      } catch (err) {
        alertService.showHttpError(err.response);
      }
    };

    const getFieldTypeLabel = (type: string) => {
      return FIELD_TYPE_LABELS[type] || type;
    };

    const needsLength = (fieldType: string | null | undefined): boolean => {
      return FIELD_TYPES_WITH_LENGTH_ONLY.includes((fieldType || "").toUpperCase());
    };

    const needsPrecision = (fieldType: string | null | undefined): boolean => {
      return FIELD_TYPES_WITH_PRECISION_AND_SCALE.includes((fieldType || "").toUpperCase());
    };

    const needsScale = (fieldType: string | null | undefined): boolean => {
      return FIELD_TYPES_WITH_PRECISION_AND_SCALE.includes((fieldType || "").toUpperCase());
    };

    onMounted(async () => {
      await loadTree();
      try {
        const dsRes = await dataSourceService().retrieve();
        dataSources.value = dsRes.data || [];
      } catch (err) {
        console.warn("加载数据源列表失败", err);
      }
      const modelId = route.query.modelId;
      if (modelId) {
        const findNode = (nodes: TreeNode[], id: number): TreeNode | null => {
          for (const node of nodes) {
            if (node.type === "model" && node.id === id) {
              return node;
            }
            if (node.children) {
              const found = findNode(node.children, id);
              if (found) return found;
            }
          }
          return null;
        };
        const targetNode = findNode(treeData.value, Number(modelId));
        if (targetNode) {
          await nextTick();
          handleNodeClick(targetNode);
          (treeRef.value as any)?.setCurrentKey(targetNode.id);
        }
      }
    });

    return {
      treeData,
      treeProps,
      treeRef,
      selectedModel,
      modelFields,
      fieldsLoading,
      directoryDialogVisible,
      directoryDialogTitle,
      directoryForm,
      deleteDialogVisible,
      deleteMessage,
      materializeDialogVisible,
      materializeLoading,
      dataSources,
      materializeSchemas,
      materializeForm,
      materializeFields,
      materializeTableExists,
      materializeDDLPreview,
      materializePhysicalTypes,
      canMaterialize,
      needsMaterializeLength,
      needsMaterializePrecision,
      needsMaterializeScale,
      handleNodeClick,
      showAddDirectoryDialog,
      saveDirectory,
      showAddModelDialog,
      handleDeleteNode,
      prepareRemove,
      confirmDelete,
      openMaterializeDialog,
      onDataSourceChange,
      checkMaterializeTableExists,
      previewMaterializeDDL,
      confirmMaterialize,
      getFieldTypeLabel,
      needsLength,
      needsPrecision,
      needsScale,
      getDataSourceName,
      getPhysicalTableName,
      ...dateFormat,
    };
  },
});