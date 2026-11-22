import {
  type Ref,
  computed,
  defineComponent,
  inject,
  onMounted,
  ref,
} from "vue";
import { useRoute, useRouter } from "vue-router";

import DataModelService from "./data-model.service";
import ModelDirectoryService from "./model-directory.service";
import DataSourceService from "../data-source/data-source.service";
import { DataModel, type IDataModel } from "@/shared/model/data-model.model";
import { ModelField, type IModelField } from "@/shared/model/model-field.model";
import { DimensionRole, type IDimensionRole } from "@/shared/model/dimension-role.model";
import { type IModelDirectory } from "@/shared/model/model-directory.model";
import { type IDataSource } from "@/shared/model/data-source.model";
import { useAlertService } from "@/shared/alert/alert.service";

interface TreeNode {
  id: string;
  name: string;
  children?: TreeNode[];
}

interface FlatDirectoryOption {
  id: string;
  name: string;
  level: number;
}

export default defineComponent({
  name: "DataModelUpdate",
  setup() {
    const dataModelService = inject(
      "dataModelService",
      () => new DataModelService(),
    );
    const modelDirectoryService = inject(
      "modelDirectoryService",
      () => new ModelDirectoryService(),
    );
    const dataSourceService = inject(
      "dataSourceService",
      () => new DataSourceService(),
    );
    const alertService = inject("alertService", () => useAlertService(), true);

    const route = useRoute();
    const router = useRouter();

    const dataModel: Ref<IDataModel> = ref(new DataModel());
    const isSaving = ref(false);
    const isEdit = ref(false);

    const modelMode = ref<"normal" | "register">("normal");
    const registerAvailableDataSources = ref<IDataSource[]>([]);
    const registerSelectedDataSourceId = ref<string | null>(null);
    const registerSchemas = ref<string[]>([]);
    const registerSelectedSchema = ref<string | null>(null);
    const registerTables = ref<string[]>([]);
    const registerSelectedTable = ref<string | null>(null);
    const registerFieldsLoading = ref(false);
    const registerAutoAdded = ref(false);

    const fields: Ref<IModelField[]> = ref([]);
    const originalFields: Ref<IModelField[]> = ref([]);

    const timeFieldOptions = computed(() => {
      const seen = new Set<string>();
      const result: Array<{ fieldName: string; fieldType: string }> = [];
      for (const field of fields.value) {
        const name = field.fieldName;
        if (name && !seen.has(name)) {
          seen.add(name);
          result.push({ fieldName: name, fieldType: field.fieldType || "" });
        }
      }
      return result;
    });

    // 维度默认显示字段候选：来自当前字段列表（去重）
    const displayFieldOptions = computed(() => {
      const seen = new Set<string>();
      const result: Array<{ fieldName: string; fieldType: string }> = [];
      for (const field of fields.value) {
        const name = field.fieldName;
        if (name && !seen.has(name)) {
          seen.add(name);
          result.push({ fieldName: name, fieldType: field.fieldType || "" });
        }
      }
      return result;
    });

    const directoryTreeData: Ref<TreeNode[]> = ref([]);
    const dimensionModels: Ref<IDataModel[]> = ref([]);
    const dimensionFieldsCache: Ref<Record<string, IModelField[]>> = ref({});
    const dimensionRolesCache: Ref<Record<string, IDimensionRole[]>> = ref({});
    const dimensionRoles: Ref<IDimensionRole[]> = ref([]);
    const dimensionRolesLoading = ref(false);

    const quickRoleDialogVisible = ref(false);
    const quickRoleSuggestions = ref<
      Array<{
        fieldIndex: number;
        fieldName: string;
        dimensionModelId: string;
        dimensionModelName: string;
        code: string;
        name: string;
        selected: boolean;
      }>
    >([]);

    const logicalTypes: Ref<{ type: string; label: string }[]> = ref([]);

    const previousState = () => router.go(-1);

    const saveDimensionRoleList = async (modelId: string) => {
      if (dataModel.value.modelType !== "DIMENSION") {
        return;
      }
      const normalized: IDimensionRole[] = [];
      const seen = new Set<string>();
      for (let i = 0; i < dimensionRoles.value.length; i++) {
        const role = dimensionRoles.value[i];
        const code = normalizeRoleCode(role.code);
        const name = (role.name || "").trim() || code;
        if (!code) {
          throw new Error("维度角色编码不能为空");
        }
        if (!/^[A-Z][A-Z0-9_]*$/.test(code)) {
          throw new Error(
            `维度角色编码只能包含大写字母、数字和下划线，且必须以字母开头：${code}`,
          );
        }
        if (seen.has(code)) {
          throw new Error(`维度角色编码重复：${code}`);
        }
        seen.add(code);
        normalized.push({
          id: role.id,
          dimensionModelId: modelId,
          code,
          name,
          description: role.description,
          sortOrder: i,
        });
      }
      const saved = await dataModelService().saveDimensionRoles(
        modelId,
        normalized,
      );
      dimensionRolesCache.value[modelId] = saved;
      dimensionRoles.value = saved.map(
        (role) =>
          new DimensionRole(
            role.id,
            role.dimensionModelId,
            role.code,
            role.name,
            role.description,
            role.sortOrder,
          ),
      );
    };

    const loadDirectoryTree = async () => {
      try {
        const res = await modelDirectoryService().retrieve();
        const directories: IModelDirectory[] = res.data;
        const buildTree = (parentId: string | null): TreeNode[] => {
          return directories
            .filter((d) => (d.parentId || null) === parentId)
            .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
            .map((d) => ({
              id: d.id!,
              name: d.name || "",
              children: buildTree(d.id!),
            }));
        };
        directoryTreeData.value = buildTree(null);
      } catch (err) {
        alertService.showHttpError(err.response);
      }
    };

    const flatDirectoryOptions = computed(() => {
      const result: FlatDirectoryOption[] = [];
      const flatten = (nodes: TreeNode[], level: number) => {
        for (const node of nodes) {
          result.push({ id: node.id, name: node.name, level });
          if (node.children && node.children.length > 0) {
            flatten(node.children, level + 1);
          }
        }
      };
      flatten(directoryTreeData.value, 0);
      return result;
    });

    const loadDimensionModels = async () => {
      try {
        const res = await dataModelService().retrieveByType("DIMENSION");
        dimensionModels.value = res.data;
      } catch (err) {
        alertService.showHttpError(err.response);
      }
    };

    const loadDimensionFields = async (modelId: string) => {
      if (dimensionFieldsCache.value[modelId]) {
        return;
      }
      try {
        const res = await dataModelService().getFields(modelId);
        dimensionFieldsCache.value[modelId] = res.data;
      } catch (err) {
        alertService.showHttpError(err.response);
      }
    };

    const loadDimensionRoles = async (modelId: string) => {
      if (dimensionRolesCache.value[modelId]) {
        return dimensionRolesCache.value[modelId];
      }
      try {
        const roles = await dataModelService().getDimensionRoles(modelId);
        dimensionRolesCache.value[modelId] = roles;
        return roles;
      } catch (err) {
        alertService.showHttpError(err.response);
        return [];
      }
    };

    const getDimensionRoles = (modelId?: string | null) => {
      if (!modelId) {
        return [];
      }
      if (!dimensionRolesCache.value[modelId]) {
        loadDimensionRoles(modelId);
      }
      return dimensionRolesCache.value[modelId] || [];
    };

    const getDimensionFields = (modelId: string) => {
      if (!dimensionFieldsCache.value[modelId]) {
        loadDimensionFields(modelId);
      }
      return dimensionFieldsCache.value[modelId] || [];
    };

    const handleDimensionModelChange = async (
      row: IModelField,
      modelId: string | null,
    ) => {
      row.dimensionModelId = modelId || null;
      row.dimensionFieldId = null;
      row.dimensionRole = null;
      if (!modelId) {
        return;
      }
      const dimensionModel = dimensionModels.value.find((m) => m.id === modelId);
      if (dimensionModel?.dimensionKind === "DEGENERATE") {
        row.dimensionFieldId = null;
        await loadDimensionRoles(modelId);
        return;
      }
      await Promise.all([loadDimensionFields(modelId), loadDimensionRoles(modelId)]);
      const fields = dimensionFieldsCache.value[modelId] || [];
      const pk = fields.find((f) => f.isPrimaryKey);
      if (pk) {
        row.dimensionFieldId = pk.id;
      }
    };

    const onDimensionRoleChange = (row: IModelField, roleCode: string | null) => {
      row.dimensionRole = roleCode || null;
    };

    const addDimensionRole = () => {
      dimensionRoles.value.push(
        new DimensionRole(
          undefined,
          dataModel.value.id,
          "",
          "",
          "",
          dimensionRoles.value.length,
        ),
      );
    };

    const removeDimensionRole = (index: number) => {
      dimensionRoles.value.splice(index, 1);
      dimensionRoles.value.forEach((role, i) => {
        role.sortOrder = i;
      });
    };

    const moveDimensionRole = (index: number, offset: number) => {
      const target = index + offset;
      if (target < 0 || target >= dimensionRoles.value.length) {
        return;
      }
      const roles = dimensionRoles.value;
      [roles[index], roles[target]] = [roles[target], roles[index]];
      roles.forEach((role, i) => {
        role.sortOrder = i;
      });
    };

    const normalizeRoleCode = (value?: string | null) =>
      (value || "").trim().toUpperCase();

    const roleCodeFromFieldName = (fieldName?: string | null) => {
      const raw = (fieldName || "").trim();
      if (!raw) {
        return "";
      }
      return raw
        .replace(/_(key|sk|id|fk|no)$/i, "")
        .replace(/[^A-Za-z0-9]+/g, "_")
        .replace(/^_+|_+$/g, "")
        .toUpperCase();
    };

    const ROLE_NAME_HINTS: Record<string, string> = {
      ORDER_DATE: "下单日期",
      PAY_DATE: "支付日期",
      SHIP_DATE: "发货日期",
      RECEIVE_DATE: "签收日期",
      CREATE_DATE: "创建日期",
      UPDATE_DATE: "更新日期",
      REFUND_DATE: "退款日期",
      CUSTOMER: "客户",
      PRODUCT: "商品",
      STORE: "门店",
      REGION: "区域",
    };

    const openQuickRoleDialog = async () => {
      const groups = new Map<string, { name: string; rows: Array<{ row: IModelField; index: number }> }>();
      fields.value.forEach((field, index) => {
        if (!field.dimensionModelId) {
          return;
        }
        const group = groups.get(field.dimensionModelId) || {
          name:
            dimensionModels.value.find((m) => m.id === field.dimensionModelId)
              ?.name || field.dimensionModelId,
          rows: [],
        };
        group.rows.push({ row: field, index });
        groups.set(field.dimensionModelId, group);
      });
      const suggestions: typeof quickRoleSuggestions.value = [];
      for (const [dimensionModelId, group] of groups) {
        const roles = await loadDimensionRoles(dimensionModelId);
        const existing = new Set(
          (roles || []).map((role) => normalizeRoleCode(role.code)),
        );
        group.rows.forEach(({ row, index }) => {
          if (row.dimensionRole) {
            return;
          }
          let code = roleCodeFromFieldName(row.fieldName);
          if (!code) {
            return;
          }
          let candidate = code;
          let suffix = 2;
          while (existing.has(candidate)) {
            candidate = `${code}_${suffix++}`;
          }
          existing.add(candidate);
          suggestions.push({
            fieldIndex: index,
            fieldName: row.fieldName || "",
            dimensionModelId,
            dimensionModelName: group.name,
            code: candidate,
            name: ROLE_NAME_HINTS[candidate] || candidate,
            selected: true,
          });
        });
      }
      if (!suggestions.length) {
        alertService.showInfo("没有需要生成角色的维度关联");
        return;
      }
      quickRoleSuggestions.value = suggestions;
      quickRoleDialogVisible.value = true;
    };

    const confirmQuickRoles = async () => {
      const selected = quickRoleSuggestions.value.filter(
        (item) => item.selected && item.code.trim(),
      );
      if (!selected.length) {
        quickRoleDialogVisible.value = false;
        return;
      }
      try {
        const byDimension = new Map<string, typeof selected>();
        selected.forEach((item) => {
          const list = byDimension.get(item.dimensionModelId) || [];
          list.push(item);
          byDimension.set(item.dimensionModelId, list);
        });
        for (const [dimensionModelId, items] of byDimension) {
          await dataModelService().batchCreateDimensionRoles(
            dimensionModelId,
            items.map((item) => ({
              code: normalizeRoleCode(item.code),
              name: item.name || item.code,
              sortOrder: (dimensionRolesCache.value[dimensionModelId] || [])
                .length,
            })),
          );
          const roles = await dataModelService().getDimensionRoles(
            dimensionModelId,
          );
          dimensionRolesCache.value[dimensionModelId] = roles;
          const byCode = new Map(
            roles.map((role) => [normalizeRoleCode(role.code), role]),
          );
          for (const item of items) {
            const role = byCode.get(normalizeRoleCode(item.code));
            fields.value[item.fieldIndex].dimensionRole = role?.code || null;
          }
        }
        quickRoleDialogVisible.value = false;
        alertService.showSuccess("维度角色已生成");
      } catch (err) {
        alertService.showHttpError(err.response);
      }
    };

    const retrieveDataModel = async (modelId: string) => {
      try {
        const res = await dataModelService().find(modelId);
        if (res.modelType === "DIMENSION" && !res.dimensionKind) {
          res.dimensionKind = "NORMAL";
        }
        dataModel.value = res;
        const fieldsRes = await dataModelService().getFields(modelId);
        fields.value = fieldsRes.data;
        originalFields.value = JSON.parse(JSON.stringify(fieldsRes.data));
        await Promise.all(
          Array.from(
            new Set(
              fields.value
                .map((f) => f.dimensionModelId)
                .filter((id): id is string => !!id),
            ),
          ).map((dimensionId) => loadDimensionRoles(dimensionId)),
        );
        if (res.modelType === "DIMENSION") {
          const roles = await loadDimensionRoles(modelId);
          dimensionRoles.value = (roles || []).map(
            (role) =>
              new DimensionRole(
                role.id,
                role.dimensionModelId,
                role.code,
                role.name,
                role.description,
                role.sortOrder,
              ),
          );
        } else {
          dimensionRoles.value = [];
        }
      } catch (err) {
        alertService.showHttpError(err.response);
      }
    };

    const FIELD_TYPES_WITH_LENGTH_ONLY = ["VARCHAR"];
    const FIELD_TYPES_WITH_PRECISION_AND_SCALE = ["DECIMAL", "DOUBLE"];

    const needsLength = (fieldType: string | null | undefined): boolean => {
      return FIELD_TYPES_WITH_LENGTH_ONLY.includes(
        (fieldType || "").toUpperCase(),
      );
    };

    const needsPrecision = (fieldType: string | null | undefined): boolean => {
      return FIELD_TYPES_WITH_PRECISION_AND_SCALE.includes(
        (fieldType || "").toUpperCase(),
      );
    };

    const needsScale = (fieldType: string | null | undefined): boolean => {
      return FIELD_TYPES_WITH_PRECISION_AND_SCALE.includes(
        (fieldType || "").toUpperCase(),
      );
    };

    const clearLengthAndPrecisionIfUnneeded = (row: IModelField) => {
      if (!needsLength(row.fieldType)) {
        row.fieldLength = null;
      }
      if (!needsPrecision(row.fieldType)) {
        row.fieldPrecision = null;
        row.fieldScale = null;
      }
    };

    const FIELD_ROLE_LABELS: Record<string, string> = {
      LEVEL_ID: "层级ID",
      LEVEL_NAME: "层级名称",
      MEMBER_ID: "成员ID",
      MEMBER_NAME: "成员名称",
      HIERARCHY: "层级字段",
    };

    const TIME_GRANULARITY_ORDER = ["YEAR", "QUARTER", "MONTH", "WEEK", "DAY"];
    const TIME_GRANULARITY_LABELS: Record<string, string> = {
      YEAR: "年",
      QUARTER: "季",
      MONTH: "月",
      WEEK: "周",
      DAY: "日",
    };

    const fieldRoleLabel = (role?: string | null) => {
      return role ? FIELD_ROLE_LABELS[role] || role : "-";
    };

    const isGeneratedField = (row: IModelField) => {
      return !!(row && row.fieldRole);
    };

    const reorderGeneratedLast = () => {
      const normal = fields.value.filter((f) => !f.fieldRole);
      const generated = fields.value.filter((f) => f.fieldRole);
      fields.value = [...normal, ...generated];
      fields.value.forEach((f, index) => {
        f.sortOrder = index;
      });
    };

    const makeGeneratedField = (
      name: string,
      role: string,
      levelIndex: number | null,
      description: string,
      isPrimaryKey: boolean,
    ): IModelField => {
      const field = new ModelField();
      field.modelId = dataModel.value.id || undefined;
      field.fieldName = name;
      field.fieldType = "VARCHAR";
      field.fieldLength = 255;
      field.fieldRole = role;
      field.levelIndex = levelIndex;
      field.description = description;
      field.isPartitionKey = false;
      field.isPrimaryKey = isPrimaryKey;
      return field;
    };

    const syncHierarchyFields = () => {
      const normalFields = fields.value.filter((f) => !f.fieldRole);
      const generated: IModelField[] = [];
      if (
        dataModel.value.modelType === "DIMENSION" &&
        dataModel.value.dimensionKind === "HIERARCHY"
      ) {
        const count = Number(dataModel.value.levelCount) || 0;
        for (let i = 1; i <= count; i++) {
          generated.push(
            makeGeneratedField(
              `level${i}_id`,
              "LEVEL_ID",
              i,
              `${i}级层级ID`,
              false,
            ),
          );
          generated.push(
            makeGeneratedField(
              `level${i}_name`,
              "LEVEL_NAME",
              i,
              `${i}级层级名称`,
              false,
            ),
          );
        }
        generated.push(
          makeGeneratedField(
            "member_id",
            "MEMBER_ID",
            null,
            "成员ID(末级)",
            true,
          ),
        );
        generated.push(
          makeGeneratedField(
            "member_name",
            "MEMBER_NAME",
            null,
            "成员名称(末级)",
            false,
          ),
        );
        generated.push(
          makeGeneratedField("hierarchy", "HIERARCHY", null, "层级字段", false),
        );
      }
      fields.value = [...normalFields, ...generated];
      fields.value.forEach((f, index) => {
        f.sortOrder = index;
      });
    };

    const timeGranularityOptions = TIME_GRANULARITY_ORDER.map((code) => ({
      code,
      label: TIME_GRANULARITY_LABELS[code],
    }));

    const selectedTimeLevels = computed<string[]>(() => {
      const raw = dataModel.value.timeLevels || "";
      const selected = new Set(
        raw
          .split(",")
          .map((s) => s.trim().toUpperCase())
          .filter(Boolean),
      );
      return TIME_GRANULARITY_ORDER.filter((code) => selected.has(code));
    });

    const isTimeLevelSelected = (code: string) => {
      return selectedTimeLevels.value.includes(code);
    };

    const toggleTimeLevel = (code: string) => {
      const current = selectedTimeLevels.value;
      const next = current.includes(code)
        ? current.filter((c) => c !== code)
        : [...current, code];
      dataModel.value.timeLevels = TIME_GRANULARITY_ORDER.filter((c) =>
        next.includes(c),
      ).join(",");
      syncTimeFields();
    };

    const syncTimeFields = () => {
      const normalFields = fields.value.filter((f) => !f.fieldRole);
      const generated: IModelField[] = [];
      if (
        dataModel.value.modelType === "DIMENSION" &&
        dataModel.value.dimensionKind === "TIME"
      ) {
        const levels = selectedTimeLevels.value;
        levels.forEach((code, index) => {
          const levelIndex = index + 1;
          const label = TIME_GRANULARITY_LABELS[code] || code;
          generated.push(
            makeGeneratedField(
              `${code.toLowerCase()}_id`,
              "LEVEL_ID",
              levelIndex,
              `${label}ID`,
              false,
            ),
          );
          generated.push(
            makeGeneratedField(
              `${code.toLowerCase()}_name`,
              "LEVEL_NAME",
              levelIndex,
              `${label}名称`,
              false,
            ),
          );
        });
        generated.push(
          makeGeneratedField(
            "date_key",
            "MEMBER_ID",
            null,
            "日期主键(与最末级粒度一致)",
            true,
          ),
        );
        generated.push(
          makeGeneratedField("hierarchy", "HIERARCHY", null, "层级路径", false),
        );
      }
      fields.value = [...normalFields, ...generated];
      fields.value.forEach((f, index) => {
        f.sortOrder = index;
      });
    };

    const syncDimensionFields = () => {
      if (dataModel.value.dimensionKind === "TIME") {
        syncTimeFields();
      } else {
        syncHierarchyFields();
      }
    };

    const onModelTypeChange = () => {
      if (dataModel.value.modelType === "DIMENSION") {
        if (!dataModel.value.dimensionKind) {
          dataModel.value.dimensionKind = "NORMAL";
        }
      } else {
        dataModel.value.dimensionKind = "NORMAL";
        dataModel.value.levelCount = null;
        dataModel.value.timeLevels = null;
        dataModel.value.timeStart = null;
        dataModel.value.timeEnd = null;
        dataModel.value.displayFieldName = null;
        dimensionRoles.value = [];
      }
      if (dataModel.value.modelType !== "DWD") {
        dataModel.value.timeFieldName = null;
      }
      syncDimensionFields();
    };

    const onDimensionKindChange = () => {
      if (dataModel.value.dimensionKind === "HIERARCHY") {
        if (!dataModel.value.levelCount) {
          dataModel.value.levelCount = 1;
        }
      } else if (dataModel.value.dimensionKind === "TIME") {
        dataModel.value.levelCount = null;
        if (!dataModel.value.timeLevels) {
          dataModel.value.timeLevels = "YEAR,QUARTER,MONTH,DAY";
        }
        if (!dataModel.value.timeStart) {
          dataModel.value.timeStart = "2024-01-01";
        }
        if (!dataModel.value.timeEnd) {
          dataModel.value.timeEnd = "2024-12-31";
        }
      } else if (dataModel.value.dimensionKind === "DEGENERATE") {
        dataModel.value.levelCount = null;
        dataModel.value.timeLevels = null;
        dataModel.value.timeStart = null;
        dataModel.value.timeEnd = null;
        dataModel.value.displayFieldName = null;
      }
      syncDimensionFields();
    };

    const onLevelCountChange = () => {
      syncHierarchyFields();
    };

    const addField = () => {
      const newField = new ModelField();
      newField.modelId = dataModel.value.id || undefined;
      newField.sortOrder = fields.value.length;
      newField.isPartitionKey = false;
      newField.isPrimaryKey = false;
      fields.value.push(newField);
      reorderGeneratedLast();
    };

    const handlePrimaryKeyChange = (row: IModelField) => {
      if (row.isPrimaryKey) {
        for (const f of fields.value) {
          if (f !== row) {
            f.isPrimaryKey = false;
          }
        }
      }
    };

    const removeField = (index: number) => {
      fields.value.splice(index, 1);
      for (let i = 0; i < fields.value.length; i++) {
        fields.value[i].sortOrder = i;
      }
    };

    const movableFields = (): IModelField[] =>
      fields.value.filter((f) => !f.fieldRole);

    const canMoveField = (row: IModelField, offset: number): boolean => {
      const normals = movableFields();
      const index = normals.indexOf(row);
      const targetIndex = index + offset;
      return index >= 0 && targetIndex >= 0 && targetIndex < normals.length;
    };

    /** 在普通字段内上移 / 下移，重排 sortOrder 保持自动生成字段始终在末尾。 */
    const moveField = (row: IModelField, offset: number) => {
      const normals = movableFields();
      const generated = fields.value.filter((f) => f.fieldRole);
      const index = normals.indexOf(row);
      const targetIndex = index + offset;
      if (index < 0 || targetIndex < 0 || targetIndex >= normals.length) {
        return;
      }
      [normals[index], normals[targetIndex]] = [
        normals[targetIndex],
        normals[index],
      ];
      fields.value = [...normals, ...generated];
      fields.value.forEach((f, i) => {
        f.sortOrder = i;
      });
    };

    const importDialogVisible = ref(false);
    const importSelectedDataSourceId = ref<string | null>(null);
    const importAvailableDataSources = ref<IDataSource[]>([]);
    const importSchemas = ref<string[]>([]);
    const importSelectedSchema = ref<string | null>(null);
    const importTables = ref<string[]>([]);
    const importSelectedTable = ref<string | null>(null);
    const importSourceFields = ref<any[]>([]);
    const importSelectedFields = ref<any[]>([]);
    const importFieldsLoading = ref(false);

    const mapJdbcTypeToFieldType = (jdbcType: string): string => {
      const type = (jdbcType || "").toUpperCase();
      if (type.includes("TEXT") || type.includes("CLOB")) {
        return "TEXT";
      }
      if (
        type.includes("VARCHAR") ||
        type.includes("CHAR") ||
        type.includes("STRING")
      ) {
        return "VARCHAR";
      }
      if (
        type.includes("INT") ||
        type.includes("TINYINT") ||
        type.includes("SMALLINT") ||
        type.includes("MEDIUMINT")
      ) {
        return "INTEGER";
      }
      if (type.includes("BIGINT") || type.includes("SERIAL")) {
        return "LONG";
      }
      if (
        type.includes("DOUBLE") ||
        type.includes("FLOAT") ||
        type.includes("REAL")
      ) {
        return "DOUBLE";
      }
      if (
        type.includes("DECIMAL") ||
        type.includes("NUMERIC") ||
        type.includes("NUMBER")
      ) {
        return "DECIMAL";
      }
      if (
        type.includes("DATE") &&
        !type.includes("DATETIME") &&
        !type.includes("TIMESTAMP")
      ) {
        return "DATE";
      }
      if (
        type.includes("DATETIME") ||
        type.includes("TIMESTAMP") ||
        type.includes("TIME")
      ) {
        return "DATETIME";
      }
      if (
        type.includes("BYTEA") ||
        type.includes("BINARY") ||
        type.includes("VARBINARY") ||
        type.includes("LONG_RAW")
      ) {
        return "BINARY";
      }
      if (
        type.includes("BLOB") ||
        type.includes("LONGBLOB") ||
        type.includes("MEDIUMBLOB") ||
        type.includes("TINYBLOB")
      ) {
        return "BLOB";
      }
      if (type.includes("BOOL") || type.includes("BIT")) {
        return "BOOLEAN";
      }
      return "VARCHAR";
    };

    const loadImportAvailableDataSources = async () => {
      try {
        const res = await dataSourceService().retrieve();
        importAvailableDataSources.value = res.data || [];
      } catch (err) {
        alertService.showHttpError(err.response);
      }
    };

    const showImportDialog = () => {
      importSelectedDataSourceId.value = null;
      importSelectedSchema.value = null;
      importSelectedTable.value = null;
      importSchemas.value = [];
      importTables.value = [];
      importSourceFields.value = [];
      importSelectedFields.value = [];
      importDialogVisible.value = true;
      loadImportAvailableDataSources();
    };

    const onImportDataSourceChange = async () => {
      importSelectedSchema.value = null;
      importSelectedTable.value = null;
      importSchemas.value = [];
      importTables.value = [];
      importSourceFields.value = [];
      importSelectedFields.value = [];
      const dataSourceId = importSelectedDataSourceId.value;
      if (!dataSourceId) {
        return;
      }
      try {
        const res = await dataSourceService().getSchemas(dataSourceId);
        importSchemas.value = res.data || res || [];
      } catch (err) {
        alertService.showHttpError(err.response);
      }
    };

    const onImportSchemaChange = async () => {
      importSelectedTable.value = null;
      importTables.value = [];
      importSourceFields.value = [];
      importSelectedFields.value = [];
      const schema = importSelectedSchema.value;
      const dataSourceId = importSelectedDataSourceId.value;
      if (!schema || !dataSourceId) {
        return;
      }
      try {
        const res = await dataSourceService().getTables(dataSourceId, schema);
        const tables = res.data || res || [];
        importTables.value = tables.map((t: any) =>
          typeof t === "string" ? t : (t.table ?? t.tableName ?? t.name ?? t),
        );
      } catch (err) {
        alertService.showHttpError(err.response);
      }
    };

    const onImportTableChange = async () => {
      importSourceFields.value = [];
      importSelectedFields.value = [];
      const table = importSelectedTable.value;
      const dataSourceId = importSelectedDataSourceId.value;
      const schema = importSelectedSchema.value;
      if (!table || !dataSourceId || !schema) {
        return;
      }
      importFieldsLoading.value = true;
      try {
        const res = await dataSourceService().getFields(
          dataSourceId,
          schema,
          table,
        );
        const columns = res.data || res || [];
        importSourceFields.value = columns.map((col: any, index: number) => ({
          tempId: `import_${index}`,
          fieldName: col.columnName || col.name || col.field || "",
          fieldType: mapJdbcTypeToFieldType(
            col.dataType || col.type || col.typeName || "",
          ),
          _rawType: col.dataType || col.type || col.typeName || "",
          fieldLength: col.length ?? col.columnSize ?? col.size ?? null,
          fieldPrecision: col.precision ?? null,
          fieldScale: col.scale ?? null,
          description: col.remarks || col.comment || col.description || "",
          isPrimaryKey: !!(col.primaryKey ?? col.isPrimaryKey),
        }));
      } catch (err) {
        alertService.showHttpError(err.response);
      } finally {
        importFieldsLoading.value = false;
      }
    };

    const isAllFieldsSelected = computed(() => {
      return (
        importSourceFields.value.length > 0 &&
        importSelectedFields.value.length === importSourceFields.value.length
      );
    });

    const toggleAllFields = () => {
      if (isAllFieldsSelected.value) {
        importSelectedFields.value = [];
      } else {
        importSelectedFields.value = [...importSourceFields.value];
      }
    };

    const isFieldSelected = (field: any) => {
      return importSelectedFields.value.some((s) => s.tempId === field.tempId);
    };

    const toggleField = (field: any) => {
      if (isFieldSelected(field)) {
        importSelectedFields.value = importSelectedFields.value.filter(
          (s) => s.tempId !== field.tempId,
        );
      } else {
        importSelectedFields.value.push(field);
      }
    };

    const confirmImportFields = () => {
      const newFields = importSelectedFields.value.map((f) => {
        const newField = new ModelField();
        newField.modelId = dataModel.value.id || undefined;
        newField.fieldName = f.fieldName;
        newField.fieldType = f.fieldType;
        newField.fieldLength = f.fieldLength;
        newField.fieldPrecision = f.fieldPrecision;
        newField.fieldScale = f.fieldScale;
        newField.description = f.description;
        newField.sortOrder = fields.value.length;
        newField.isPartitionKey = false;
        newField.isPrimaryKey = !!f.isPrimaryKey;
        return newField;
      });
      const hasExistingPrimaryKey = fields.value.some((f) => f.isPrimaryKey);
      if (hasExistingPrimaryKey) {
        for (const nf of newFields) {
          nf.isPrimaryKey = false;
        }
      } else {
        let primaryKeyFound = false;
        for (const nf of newFields) {
          if (nf.isPrimaryKey) {
            if (primaryKeyFound) {
              nf.isPrimaryKey = false;
            } else {
              primaryKeyFound = true;
            }
          }
        }
      }
      fields.value.push(...newFields);
      reorderGeneratedLast();
      importDialogVisible.value = false;
    };

    const loadRegisterDataSources = async () => {
      try {
        const res = await dataSourceService().retrieve();
        registerAvailableDataSources.value = res.data || [];
      } catch (err) {
        alertService.showHttpError(err.response);
      }
    };

    const onModelModeChange = async () => {
      if (modelMode.value === "register") {
        await loadRegisterDataSources();
        let dataSourceId = dataModel.value.dataSourceId ?? null;
        let schemaName = dataModel.value.schemaName || null;
        if (!dataSourceId) {
          try {
            const res = await dataSourceService().getDefaultWarehouse();
            dataSourceId = res.data?.dataSourceId ?? null;
          } catch (err) {
            dataSourceId = null;
          }
          if (dataSourceId && !schemaName) {
            const defaultDs = registerAvailableDataSources.value.find(
              (d) => d.id === dataSourceId,
            );
            schemaName = defaultDs?.schemaName || null;
          }
        }
        registerSelectedDataSourceId.value = dataSourceId;
        registerSelectedSchema.value = schemaName;
        registerSelectedTable.value = dataModel.value.tableName || null;
        if (registerSelectedDataSourceId.value) {
          await onRegisterDataSourceChange();
        }
      } else {
        dataModel.value.isRegistered = false;
        dataModel.value.dataSourceId = null;
        dataModel.value.schemaName = null;
        dataModel.value.tableName = null;
        registerSelectedDataSourceId.value = null;
        registerSchemas.value = [];
        registerSelectedSchema.value = null;
        registerTables.value = [];
        registerSelectedTable.value = null;
      }
    };

    const onRegisterDataSourceChange = async () => {
      const previousSchema = registerSelectedSchema.value;
      registerSelectedSchema.value = null;
      registerTables.value = [];
      registerSelectedTable.value = null;
      const dataSourceId = registerSelectedDataSourceId.value;
      if (!dataSourceId) {
        registerSchemas.value = [];
        return;
      }
      try {
        const res = await dataSourceService().getSchemas(dataSourceId);
        registerSchemas.value = res.data || res || [];
        if (registerSchemas.value.length === 0) {
          registerSchemas.value = ["public"];
        }
        const selectedDs = registerAvailableDataSources.value.find(
          (d) => d.id === dataSourceId,
        );
        const candidates = [
          previousSchema,
          dataModel.value.schemaName,
          selectedDs?.schemaName,
        ];
        const matched = candidates.find(
          (s) => s && registerSchemas.value.includes(s),
        );
        registerSelectedSchema.value =
          matched || registerSchemas.value[0] || null;
        if (registerSelectedSchema.value) {
          await onRegisterSchemaChange();
        }
      } catch (err) {
        alertService.showHttpError(err.response);
      }
    };

    const onRegisterSchemaChange = async () => {
      registerSelectedTable.value = null;
      const schema = registerSelectedSchema.value;
      const dataSourceId = registerSelectedDataSourceId.value;
      if (!schema || !dataSourceId) {
        registerTables.value = [];
        return;
      }
      try {
        const res = await dataSourceService().getTables(dataSourceId, schema);
        const tables = res.data || res || [];
        registerTables.value = tables.map((t: any) =>
          typeof t === "string" ? t : (t.table ?? t.tableName ?? t.name ?? t),
        );
        if (
          dataModel.value.tableName &&
          registerTables.value.includes(dataModel.value.tableName)
        ) {
          registerSelectedTable.value = dataModel.value.tableName;
          await onRegisterTableChange(true);
        }
      } catch (err) {
        alertService.showHttpError(err.response);
      }
    };

    const onRegisterTableChange = async (preserveFields = false) => {
      const table = registerSelectedTable.value;
      const dataSourceId = registerSelectedDataSourceId.value;
      const schema = registerSelectedSchema.value;
      if (!table || !dataSourceId || !schema) {
        return;
      }
      // 编辑已注册模型时会重新读取物理表结构，需保留原有的维度关联等配置
      const previousFields = preserveFields ? [...fields.value] : [];
      registerFieldsLoading.value = true;
      try {
        const res = await dataSourceService().getFields(
          dataSourceId,
          schema,
          table,
        );
        const columns = res.data || res || [];
        const newFields = columns.map((col: any, index: number) => {
          const newField = new ModelField();
          newField.modelId = dataModel.value.id || undefined;
          newField.fieldName = col.columnName || col.name || col.field || "";
          newField.fieldType = mapJdbcTypeToFieldType(
            col.dataType || col.type || col.typeName || "",
          );
          newField.fieldLength =
            col.length ?? col.columnSize ?? col.size ?? null;
          newField.fieldPrecision = col.precision ?? null;
          newField.fieldScale = col.scale ?? null;
          newField.description =
            col.remarks || col.comment || col.description || "";
          newField.sortOrder = index;
          newField.isPartitionKey = false;
          newField.isPrimaryKey = !!(col.primaryKey ?? col.isPrimaryKey);
          return newField;
        });
        if (!preserveFields) {
          fields.value = newFields;
          registerAutoAdded.value = true;
        } else {
          const previousFieldByName = new Map<string, IModelField>();
          for (const previous of previousFields) {
            if (previous.fieldName) {
              previousFieldByName.set(previous.fieldName, previous);
            }
          }
          for (const field of newFields) {
            const previous = field.fieldName
              ? previousFieldByName.get(field.fieldName)
              : undefined;
            if (previous) {
              field.dimensionModelId = previous.dimensionModelId ?? null;
              field.dimensionFieldId = previous.dimensionFieldId ?? null;
            }
          }
          fields.value = newFields;
        }
        for (let i = 0; i < fields.value.length; i++) {
          fields.value[i].sortOrder = i;
        }
      } catch (err) {
        alertService.showHttpError(err.response);
      } finally {
        registerFieldsLoading.value = false;
      }
    };

    onMounted(async () => {
      await loadDirectoryTree();
      await loadDimensionModels();
      try {
        const typesRes = await dataModelService().getLogicalTypes();
        logicalTypes.value = typesRes.data || [];
      } catch (err) {
        logicalTypes.value = [];
      }
      if (route.params?.dataModelId) {
        isEdit.value = true;
        await retrieveDataModel(String(route.params.dataModelId));
        if (dataModel.value.isRegistered === true) {
          modelMode.value = "register";
          await loadRegisterDataSources();
          if (dataModel.value.dataSourceId) {
            registerSelectedDataSourceId.value = dataModel.value.dataSourceId;
            await onRegisterDataSourceChange();
          }
        }
      } else if (route.query?.directoryId) {
        dataModel.value.directoryId = String(route.query.directoryId);
        dataModel.value.modelType = "DWD";
      }
    });

    return {
      dataModel,
      isSaving,
      isEdit,
      modelMode,
      registerAvailableDataSources,
      registerSelectedDataSourceId,
      registerSchemas,
      registerSelectedSchema,
      registerTables,
      registerSelectedTable,
      registerFieldsLoading,
      registerAutoAdded,
      fields,
      originalFields,
      timeFieldOptions,
      displayFieldOptions,
      directoryTreeData,
      flatDirectoryOptions,
      dimensionModels,
      dimensionFieldsCache,
      dimensionRolesCache,
      dimensionRoles,
      dimensionRolesLoading,
      quickRoleDialogVisible,
      quickRoleSuggestions,
      getDimensionRoles,
      loadDimensionRoles,
      onDimensionRoleChange,
      addDimensionRole,
      removeDimensionRole,
      moveDimensionRole,
      openQuickRoleDialog,
      confirmQuickRoles,
      saveDimensionRoleList,
      logicalTypes,
      previousState,
      getDimensionFields,
      handleDimensionModelChange,
      fieldRoleLabel,
      isGeneratedField,
      onModelTypeChange,
      onDimensionKindChange,
      onLevelCountChange,
      timeGranularityOptions,
      selectedTimeLevels,
      isTimeLevelSelected,
      toggleTimeLevel,
      addField,
      removeField,
      canMoveField,
      moveField,
      handlePrimaryKeyChange,
      importDialogVisible,
      importSelectedDataSourceId,
      importAvailableDataSources,
      importSchemas,
      importSelectedSchema,
      importTables,
      importSelectedTable,
      importSourceFields,
      importSelectedFields,
      importFieldsLoading,
      mapJdbcTypeToFieldType,
      isAllFieldsSelected,
      toggleAllFields,
      isFieldSelected,
      toggleField,
      needsLength,
      needsPrecision,
      needsScale,
      clearLengthAndPrecisionIfUnneeded,
      showImportDialog,
      onImportDataSourceChange,
      onImportSchemaChange,
      onImportTableChange,
      confirmImportFields,
      loadRegisterDataSources,
      onModelModeChange,
      onRegisterDataSourceChange,
      onRegisterSchemaChange,
      onRegisterTableChange,
      dataModelService,
      modelDirectoryService,
      dataSourceService,
      alertService,
    };
  },
  methods: {
    async save() {
      this.isSaving = true;
      try {
        if (this.modelMode === "register") {
          if (
            !this.registerSelectedDataSourceId ||
            !this.registerSelectedSchema ||
            !this.registerSelectedTable
          ) {
            this.alertService.showWarning(
              "注册模式下请选择完整的数据源、Schema和数据表",
            );
            this.isSaving = false;
            return;
          }
          this.dataModel.isRegistered = true;
          this.dataModel.dataSourceId = this.registerSelectedDataSourceId;
          this.dataModel.schemaName = this.registerSelectedSchema;
          this.dataModel.tableName = this.registerSelectedTable;
        } else {
          this.dataModel.isRegistered = false;
          this.dataModel.dataSourceId = null;
          this.dataModel.schemaName = null;
          this.dataModel.tableName = null;
        }

        const fieldNames = this.fields.map((f) => f.fieldName);
        if (
          this.dataModel.timeFieldName &&
          !fieldNames.includes(this.dataModel.timeFieldName)
        ) {
          this.dataModel.timeFieldName = null;
        }
        if (
          this.dataModel.displayFieldName &&
          !fieldNames.includes(this.dataModel.displayFieldName)
        ) {
          this.dataModel.displayFieldName = null;
        }

        if (this.dataModel.dimensionKind === "TIME") {
          if (!this.dataModel.timeLevels) {
            this.alertService.showWarning("请至少选择一个时间粒度");
            this.isSaving = false;
            return;
          }
          const start = this.dataModel.timeStart;
          const end = this.dataModel.timeEnd;
          if (start && end && start > end) {
            this.alertService.showWarning("起始日期不能晚于结束日期");
            this.isSaving = false;
            return;
          }
        } else {
          this.dataModel.timeLevels = null;
          this.dataModel.timeStart = null;
          this.dataModel.timeEnd = null;
        }
        if (this.dataModel.dimensionKind !== "HIERARCHY") {
          this.dataModel.levelCount = null;
        }

        const duplicateRole = new Map<string, string>();
        for (const field of this.fields) {
          if (!field.dimensionModelId) {
            field.dimensionRole = null;
            continue;
          }
          const key = `${field.dimensionModelId}|${field.dimensionRole || ""}`;
          if (duplicateRole.has(key)) {
            this.alertService.showWarning(
              `同一事实表对同一维度的角色不能重复：${field.dimensionModelId}`,
            );
            this.isSaving = false;
            return;
          }
          duplicateRole.set(key, field.fieldName || "");
        }

        if (this.dataModel.id) {
          await this.dataModelService().update(this.dataModel);
          await this.saveDimensionRoleList(this.dataModel.id!);
          if (this.fields.length > 0 || this.originalFields.length > 0) {
            const fieldsToSave = this.fields.map((f, index) => ({
              ...f,
              sortOrder: index,
            }));
            await this.dataModelService().saveFields(
              this.dataModel.id!,
              fieldsToSave,
            );
          }
          this.alertService.showSuccess(
            `数据模型"${this.dataModel.name}"更新成功`,
          );
          this.$router.push({
            name: "DataModel",
            query: { modelId: String(this.dataModel.id) },
          });
        } else {
          const created = await this.dataModelService().create(this.dataModel);
          this.dataModel.id = created.id;
          if (this.fields.length > 0) {
            const fieldsToSave = this.fields.map((f, index) => ({
              ...f,
              modelId: created.id,
              sortOrder: index,
            }));
            await this.dataModelService().saveFields(created.id!, fieldsToSave);
          }
          this.alertService.showSuccess(
            `数据模型"${this.dataModel.name}"创建成功`,
          );
          this.$router.push({
            name: "DataModel",
            query: { modelId: String(created.id) },
          });
        }
        this.isSaving = false;
      } catch (err) {
        this.isSaving = false;
        if (err instanceof Error && !(err as any).response) {
          this.alertService.showWarning(err.message);
          return;
        }
        this.alertService.showHttpError(err.response);
      }
    },
  },
});
