<template>
  <form name="editForm" novalidate>
    <!-- Java脚本代码输入区域 -->
    <div class="form-group">
      <label for="scriptCode">Java脚本代码</label>
      <textarea
        class="form-control"
        id="scriptCode"
        name="scriptCode"
        v-model="formData.scriptCode"
        :rows="15"
        :maxlength="10000"
        placeholder='请输入Java脚本代码，例如：
import com.data.job.FlowFile;
import com.data.job.component.javascript.ScriptContext.LogFunction;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.util.Map;

public class UserScript {
    public FlowFile process(FlowFile flowFile, Map<String, Object> context) {
        LogFunction log = (LogFunction) context.get("log");
        // 处理 FlowFile 中的 JSON 数组数据
        if (flowFile.getJsonArray() != null) {
            JSONArray records = flowFile.getJsonArray();
            JSONArray result = new JSONArray();
            for (Object item : records) {
                JSONObject record = (JSONObject) item;
                record.put("processed", true);
                result.add(record);
            }
            flowFile.setJsonArray(result);
            log.info("JSON数组处理完成，输出 " + result.size() + " 条");
        }
        return flowFile;
    }
}'
      ></textarea>
      <small class="form-text text-muted"> 支持Java语法，可以包含自定义的数据处理逻辑 </small>
    </div>

    <!-- 操作按钮 -->
    <div class="form-actions">
      <el-button type="primary" @click="saveConfig">保存</el-button>
      <el-button @click="cancelConfig">取消</el-button>
      <el-button type="info" @click="loadDefaultTemplate">加载默认模板</el-button>
      <el-button type="warning" @click="validateScript">验证语法</el-button>
    </div>
  </form>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save', 'cancel']);

// 配置项定义
const configItems = [
  {
    key: 'scriptCode',
    label: 'Java脚本代码',
    defaultValue: '',
    required: true,
    description: '输入Java脚本代码进行自定义数据处理',
    show: true,
    maxLength: 10000,
    rows: 15,
    controlType: 'TEXTAREA',
    parameterType: 'STRING',
  },
];

const formRef = ref(null);
const formData = reactive({
  scriptCode: props.node?.data?.config?.scriptCode || '',
});

// 默认脚本模板
const defaultScriptTemplate = `import com.data.job.FlowFile;
import com.data.job.component.javascript.ScriptContext.LogFunction;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.util.Map;

public class UserScript {
    public FlowFile process(FlowFile flowFile, Map<String, Object> context) {
        LogFunction log = (LogFunction) context.get("log");

        // 1. 处理 JSON 数组：逐条读取、修改并输出
        if (flowFile.getJsonArray() != null) {
            JSONArray records = flowFile.getJsonArray();
            JSONArray result = new JSONArray();
            for (Object item : records) {
                JSONObject record = (JSONObject) item;
                // CDC 场景：UPDATE/DELETE 事件包含变更前数据 __before，先处理它
                JSONObject before = record.getJSONObject("__before");
                if (before != null) {
                    before.put("__processed", true);
                    result.add(before);
                }
                // 处理当前记录：新增/修改字段
                record.put("processed", true);
                record.put("process_time", System.currentTimeMillis());
                result.add(record);
            }
            flowFile.setJsonArray(result);
            log.info("JSON数组处理完成，输出 " + result.size() + " 条");
        }

        // 2. 处理单个 JSON 对象
        if (flowFile.getJsonObject() != null) {
            JSONObject record = flowFile.getJsonObject();
            record.put("processed", true);
            record.put("process_time", System.currentTimeMillis());
            log.info("JSON对象处理完成");
        }

        // 3. 其它格式（CSV/TEXT/BINARY）原样透传
        return flowFile;
    }
}`;

// 加载默认模板
const loadDefaultTemplate = () => {
  formData.scriptCode = defaultScriptTemplate;
  ElMessage.success('已加载默认脚本模板');
};

// 脚本语法验证
const validateScript = () => {
  const scriptCode = formData.scriptCode.trim();

  if (!scriptCode) {
    ElMessage.warning('请输入Java脚本代码');
    return;
  }

  // 基础Java语法检查
  if (!scriptCode.includes('public class UserScript')) {
    ElMessage.warning('脚本应该包含 public class UserScript 类定义');
    return;
  }

  if (!scriptCode.includes('process(FlowFile flowFile, Map<String, Object> context)')) {
    ElMessage.warning('脚本应该包含 process 方法定义');
    return;
  }

  if (!scriptCode.includes('return flowFile')) {
    ElMessage.warning('process 方法应该返回 FlowFile 对象');
    return;
  }

  // 检查必要的导入
  const requiredImports = ['import com.data.job.FlowFile', 'import java.util.Map'];

  for (const requiredImport of requiredImports) {
    if (!scriptCode.includes(requiredImport)) {
      ElMessage.warning(`脚本应该包含必要的导入: ${requiredImport}`);
      return;
    }
  }

  ElMessage.success('Java脚本语法验证通过');
};

// 保存配置
const saveConfig = async () => {
  const scriptCode = formData.scriptCode.trim();

  //   if (!scriptCode) {
  //     ElMessage.error('请输入Java脚本代码');
  //     return;
  //   }

  //   // 验证脚本基本结构
  //   if (!scriptCode.includes('public class UserScript') ||
  //       !scriptCode.includes('process(FlowFile flowFile, Map<String, Object> context)')) {
  //     ElMessage.error('脚本格式不正确，请检查类和方法定义');
  //     return;
  //   }

  emits('save', formData);
};

// 取消配置
const cancelConfig = () => {
  emits('cancel');
};

defineExpose({ saveConfig });
</script>

<style scoped>
.form-group {
  margin-bottom: 20px;
}

.form-control {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
  font-family: 'Courier New', monospace;
}

.form-control:focus {
  border-color: #409eff;
  outline: none;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.form-text {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}

.form-actions {
  margin-top: 30px;
  text-align: right;
}

textarea {
  resize: vertical;
  min-height: 300px;
  font-family: 'Courier New', monospace;
  line-height: 1.4;
}
</style>
