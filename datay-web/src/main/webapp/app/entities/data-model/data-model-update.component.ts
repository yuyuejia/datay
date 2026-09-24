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
import { type IModelDirectory } from "@/shared/model/model-directory.model";
import { type IDataSource } from "@/shared/model/data-source.model";
import { useAlertService } from "@/shared/alert/alert.service";

interface TreeNode {
  id: number;
  name: string;
  children?: TreeNode[];
}

interface FlatDirectoryOption {
  id: number;
  name: string;
  level: number;
}

export default defineComponent({
  compatConfig: { MODE: 3 },
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
    const registerSelectedDataSourceId = ref<number | null>(null);
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

    const directoryTreeData: Ref<TreeNode[]> = ref([]);
    const dimensionModels: Ref<IDataModel[]> = ref([]);
    const dimensionFieldsCache: Ref<Record<number, IModelField[]>> = ref({});

    const logicalTypes: Ref<{ type: string; label: string }[]> = ref([]);

    const previousState = () => router.go(-1);

    const loadDirectoryTree = async () => {
      try {
        const res = await modelDirectoryService().retrieve();
        const directories: IModelDirectory[] = res.data;
        const buildTree = (parentId: number | null): TreeNode[] => {
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

    const loadDimensionFields = async (modelId: number) => {
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

    const getDimensionFields = (modelId: number) => {
      if (!dimensionFieldsCache.value[modelId]) {
        loadDimensionFields(modelId);
      }
      return dimensionFieldsCache.value[modelId] || [];
    };

    const handleDimensionModelChange = async (
      row: IModelField,
      modelId: number | null,
    ) => {
      row.dimensionFieldId = null;
      if (modelId) {
        await loadDimensionFields(modelId);
        const fields = dimensionFieldsCache.value[modelId] || [];
        const pk = fields.find((f) => f.isPrimaryKey);
        if (pk) {
          row.dimensionFieldId = pk.id;
        }
      }
    };

    const retrieveDataModel = async (modelId: number) => {
      try {
        const res = await dataModelService().find(modelId);
        if (res.modelType === "DIMENSION" && !res.dimensionKind) {
          res.dimensionKind = "NORMAL";
        }
        dataModel.value = res;
        const fieldsRes = await dataModelService().getFields(modelId);
        fields.value = fieldsRes.data;
        originalFields.value = JSON.parse(JSON.stringify(fieldsRes.data));
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

    const onModelTypeChange = () => {
      if (dataModel.value.modelType === "DIMENSION") {
        if (!dataModel.value.dimensionKind) {
          dataModel.value.dimensionKind = "NORMAL";
        }
      } else {
        dataModel.value.dimensionKind = "NORMAL";
        dataModel.value.levelCount = null;
      }
      if (dataModel.value.modelType !== "DWD") {
        dataModel.value.timeFieldName = null;
      }
      syncHierarchyFields();
    };

    const onDimensionKindChange = () => {
      if (
        dataModel.value.dimensionKind === "HIERARCHY" &&
        !dataModel.value.levelCount
      ) {
        dataModel.value.levelCount = 1;
      }
      syncHierarchyFields();
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

    const importDialogVisible = ref(false);
    const importSelectedDataSourceId = ref<number | null>(null);
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
        await retrieveDataModel(Number(route.params.dataModelId));
        if (dataModel.value.isRegistered === true) {
          modelMode.value = "register";
          await loadRegisterDataSources();
          if (dataModel.value.dataSourceId) {
            registerSelectedDataSourceId.value = dataModel.value.dataSourceId;
            await onRegisterDataSourceChange();
          }
        }
      } else if (route.query?.directoryId) {
        dataModel.value.directoryId = Number(route.query.directoryId);
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
      timeFieldOptions,
      directoryTreeData,
      flatDirectoryOptions,
      dimensionModels,
      dimensionFieldsCache,
      logicalTypes,
      previousState,
      getDimensionFields,
      handleDimensionModelChange,
      fieldRoleLabel,
      isGeneratedField,
      onModelTypeChange,
      onDimensionKindChange,
      onLevelCountChange,
      addField,
      removeField,
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

        if (this.dataModel.id) {
          await this.dataModelService().update(this.dataModel);
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
        this.alertService.showHttpError(err.response);
      }
    },
  },
});
