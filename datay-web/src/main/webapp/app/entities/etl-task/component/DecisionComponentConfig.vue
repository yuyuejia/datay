<template>
  <el-form name="editForm" label-position="top">
    <el-alert
      title="接口地址、API Key、模型等参数由「服务配置 · 决策模型」统一配置，任务中无需填写。"
      type="info"
      :closable="false"
      show-icon
      class="config-tip"
    />

    <div class="section-header">
      <span class="section-title">问题定义</span>
      <el-switch v-model="formData.advanced" active-text="直接编辑 JSON" @change="onAdvancedChange" />
    </div>

    <template v-if="!formData.advanced">
      <div v-for="(question, index) in questions" :key="question.uid" class="question-card">
        <div class="question-toolbar">
          <span class="question-index">问题 {{ index + 1 }}</span>
          <el-button type="danger" link @click="removeQuestion(index)">删除问题</el-button>
        </div>

        <el-row :gutter="12">
          <el-col :span="10">
            <el-form-item label="问题名" required>
              <el-input v-model="question.name" placeholder="如 team（将作为结果字段名）" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="类型" required>
              <el-select v-model="question.type" style="width: 100%" @change="onTypeChange(question)">
                <el-option label="choice（单选）" value="choice" />
                <el-option label="noul（是/否）" value="noul" />
                <el-option label="score（打分）" value="score" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="问题说明（instructions）" required>
          <el-input
            v-model="question.instructions"
            type="textarea"
            :rows="2"
            placeholder="用一句话说明要模型判断什么，如：这条工单该由哪个团队处理？"
          />
        </el-form-item>

        <div v-if="question.type === 'choice'" class="option-block">
          <div class="option-header">
            <span>选项（值 → 说明）</span>
            <el-button link type="primary" @click="addChoice(question)">+ 添加选项</el-button>
          </div>
          <div v-for="(choice, ci) in question.choices" :key="choice.uid" class="option-row">
            <el-input v-model="choice.value" placeholder="选项值，如 billing" class="option-key" />
            <el-input v-model="choice.label" placeholder="说明，如 支付与退款" class="option-label" />
            <el-button link type="danger" @click="removeChoice(question, ci)">删除</el-button>
          </div>
          <small class="form-text text-muted">模型只会从这些选项中选一个；选项值建议使用英文字母/数字/下划线。</small>
        </div>

        <div v-else-if="question.type === 'score'" class="option-block">
          <div class="option-header">
            <span>档位（从低到高）</span>
            <el-button link type="primary" @click="addScore(question)">+ 添加档位</el-button>
          </div>
          <div v-for="(score, si) in question.scores" :key="score.uid" class="option-row">
            <span class="score-index">{{ si }}</span>
            <el-input v-model="score.value" placeholder="档位名称，如 低 / 中 / 高" class="option-label" />
            <el-button link :disabled="si === 0" @click="moveScore(question, si, -1)">上移</el-button>
            <el-button link :disabled="si === question.scores.length - 1" @click="moveScore(question, si, 1)">下移</el-button>
            <el-button link type="danger" @click="removeScore(question, si)">删除</el-button>
          </div>
          <small class="form-text text-muted">模型返回一个分值，档位下标 0、1、2… 与上面的顺序一一对应。</small>
        </div>

        <div v-else class="option-block">
          <small class="form-text text-muted">noul 类型无需选项，模型返回 0~1 的是/否概率。</small>
        </div>
      </div>

      <el-button type="primary" plain class="add-question" @click="addQuestion">+ 添加问题</el-button>
      <small class="form-text text-muted">可添加多个问题，一次决策请求会同时给出全部问题的答案。</small>
    </template>

    <template v-else>
      <el-form-item label="questions（JSON）">
        <el-input
          type="textarea"
          v-model="formData.questionsJson"
          :rows="14"
          placeholder='{"team":{"type":"choice","instructions":"...","criteria":{"billing":"支付与退款"}}}'
        />
        <small class="form-text text-muted"
          >键为问题名，值为 <code>{ type, instructions, criteria }</code>；choice 的 criteria 为对象，score 为数组。</small
        >
      </el-form-item>
    </template>

    <el-divider />

    <el-form-item>
      <template #label>state 模板（可选） <DynamicParameterHelp /></template>
      <el-input
        v-model="formData.state"
        type="textarea"
        :rows="3"
        placeholder='留空则使用整行数据作为 state，例如：{"ticket":"${content}"}'
      />
      <small class="form-text text-muted"
        >留空时默认使用整行数据；支持动态参数 <code>${字段名}</code> / <code>${attr:属性名}</code>。</small
      >
    </el-form-item>

    <el-form-item>
      <template #label>state 字段子集（可选）</template>
      <el-input v-model="formData.stateFields" placeholder="ticket,content" />
      <small class="form-text text-muted">仅在未填写 state 模板时生效，逗号分隔；留空则使用整行全部字段。</small>
    </el-form-item>

    <el-form-item>
      <template #label>答案字段前缀（可选）</template>
      <el-input v-model="formData.answerPrefix" placeholder="decision_" />
      <small class="form-text text-muted">合并到行数据时给每个答案字段加前缀，避免与原始字段冲突。</small>
    </el-form-item>

    <el-form-item>
      <template #label>合并置信度（confidence）</template>
      <el-switch v-model="formData.includeConfidence" />
      <small class="form-text text-muted">开启后为 choice / score 追加 <code>&lt;问题名&gt;_confidence</code> 字段。</small>
    </el-form-item>

    <el-form-item>
      <template #label>合并概率分布（probabilities）</template>
      <el-switch v-model="formData.includeProbabilities" />
      <small class="form-text text-muted"
        >开启后追加 <code>&lt;问题名&gt;_probabilities</code>（score 另含 <code>_legend</code>），以 JSON 字符串写入。</small
      >
    </el-form-item>

    <el-form-item>
      <template #label>原始答案字段（可选）</template>
      <el-input v-model="formData.resultField" placeholder="decision_result" />
      <small class="form-text text-muted">填写后把完整的 answers JSON 写入该字段。</small>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';
import DynamicParameterHelp from '../DynamicParameterHelp.vue';

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save']);

const config = props.node?.data?.config || {};

let uidSeq = 0;
const nextUid = () => `q_${Date.now()}_${uidSeq++}`;

const parseQuestions = raw => {
  if (!raw || !String(raw).trim()) {
    return [];
  }
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return [];
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return [];
  }
  return Object.entries(parsed).map(([name, def]) => {
    const question = def && typeof def === 'object' ? def : {};
    const type = question.type === 'noul' || question.type === 'score' ? question.type : 'choice';
    const choices = [];
    if (type === 'choice' && question.criteria && typeof question.criteria === 'object' && !Array.isArray(question.criteria)) {
      for (const [value, label] of Object.entries(question.criteria)) {
        choices.push({ uid: nextUid(), value, label: label == null ? '' : String(label) });
      }
    }
    const scores = [];
    if (type === 'score' && Array.isArray(question.criteria)) {
      for (const item of question.criteria) {
        scores.push({ uid: nextUid(), value: item == null ? '' : String(item) });
      }
    }
    return {
      uid: nextUid(),
      name,
      type,
      instructions: question.instructions || '',
      choices: choices.length ? choices : [{ uid: nextUid(), value: '', label: '' }],
      scores: scores.length ? scores : [{ uid: nextUid(), value: '' }],
    };
  });
};

const questions = ref(parseQuestions(config.questions));

const formData = reactive({
  advanced: false,
  questionsJson: config.questions || '',
  state: config.state || '',
  stateFields: config.stateFields || '',
  answerPrefix: config.answerPrefix || '',
  includeConfidence: config.includeConfidence !== false,
  includeProbabilities: config.includeProbabilities === true,
  resultField: config.resultField || '',
});

const addQuestion = () => {
  questions.value.push({
    uid: nextUid(),
    name: '',
    type: 'choice',
    instructions: '',
    choices: [{ uid: nextUid(), value: '', label: '' }],
    scores: [{ uid: nextUid(), value: '' }],
  });
};

const removeQuestion = index => {
  questions.value.splice(index, 1);
};

const onTypeChange = question => {
  if (question.type === 'choice' && question.choices.length === 0) {
    question.choices.push({ uid: nextUid(), value: '', label: '' });
  }
  if (question.type === 'score' && question.scores.length === 0) {
    question.scores.push({ uid: nextUid(), value: '' });
  }
};

const addChoice = question => {
  question.choices.push({ uid: nextUid(), value: '', label: '' });
};

const removeChoice = (question, index) => {
  question.choices.splice(index, 1);
};

const addScore = question => {
  question.scores.push({ uid: nextUid(), value: '' });
};

const removeScore = (question, index) => {
  question.scores.splice(index, 1);
};

const moveScore = (question, index, offset) => {
  const target = index + offset;
  if (target < 0 || target >= question.scores.length) {
    return;
  }
  const [item] = question.scores.splice(index, 1);
  question.scores.splice(target, 0, item);
};

const serializeQuestions = () => {
  if (questions.value.length === 0) {
    throw new Error('请至少添加一个问题');
  }
  const names = new Set();
  const result = {};
  questions.value.forEach((question, index) => {
    const name = (question.name || '').trim();
    if (!name) {
      throw new Error(`问题 ${index + 1} 未填写问题名`);
    }
    if (names.has(name)) {
      throw new Error(`问题名重复：${name}`);
    }
    names.add(name);
    const definition = { type: question.type, instructions: question.instructions || '' };
    if (question.type === 'choice') {
      const criteria = {};
      let count = 0;
      for (const choice of question.choices) {
        const value = (choice.value || '').trim();
        if (!value) {
          continue;
        }
        criteria[value] = choice.label || '';
        count++;
      }
      if (count === 0) {
        throw new Error(`问题「${name}」至少需要一个选项`);
      }
      definition.criteria = criteria;
    } else if (question.type === 'score') {
      const criteria = question.scores.map(score => score.value || '').filter(value => value.trim() !== '');
      if (criteria.length === 0) {
        throw new Error(`问题「${name}」至少需要一个档位`);
      }
      definition.criteria = criteria;
    }
    result[name] = definition;
  });
  return JSON.stringify(result);
};

const validateQuestionsJson = value => {
  if (!value || !value.trim()) {
    throw new Error('请填写 questions');
  }
  let parsed;
  try {
    parsed = JSON.parse(value);
  } catch (e) {
    throw new Error('questions 不是合法的 JSON：' + e.message);
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed) || Object.keys(parsed).length === 0) {
    throw new Error('questions 必须是非空的 JSON 对象');
  }
  for (const [name, question] of Object.entries(parsed)) {
    if (!question || typeof question !== 'object' || !question.type) {
      throw new Error(`问题「${name}」缺少 type 字段（choice/noul/score）`);
    }
  }
  return parsed;
};

const onAdvancedChange = value => {
  if (value) {
    try {
      formData.questionsJson = JSON.stringify(JSON.parse(serializeQuestions()), null, 2);
    } catch {
      formData.questionsJson = '';
    }
    return;
  }
  if (!formData.questionsJson || !formData.questionsJson.trim()) {
    return;
  }
  try {
    validateQuestionsJson(formData.questionsJson);
    questions.value = parseQuestions(formData.questionsJson);
  } catch (e) {
    ElMessage.error(e.message);
    formData.advanced = true;
  }
};

const saveConfig = () => {
  let questionsJson;
  try {
    questionsJson = formData.advanced ? JSON.stringify(validateQuestionsJson(formData.questionsJson)) : serializeQuestions();
  } catch (e) {
    ElMessage.error(e.message);
    return;
  }
  emits('save', {
    questions: questionsJson,
    state: formData.state,
    stateFields: formData.stateFields,
    answerPrefix: formData.answerPrefix,
    includeConfidence: formData.includeConfidence,
    includeProbabilities: formData.includeProbabilities,
    resultField: formData.resultField,
  });
};

defineExpose({ saveConfig });
</script>

<style scoped>
.config-tip {
  margin-bottom: 16px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-title {
  font-weight: 600;
}

.question-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 16px 16px 4px;
  margin-bottom: 16px;
  background-color: #fafcff;
}

.question-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.question-index {
  font-weight: 600;
  color: #409eff;
}

.option-block {
  margin-bottom: 12px;
}

.option-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  color: #606266;
}

.option-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.option-key {
  width: 200px;
  flex-shrink: 0;
}

.option-label {
  flex: 1;
}

.score-index {
  width: 24px;
  text-align: center;
  color: #909399;
  flex-shrink: 0;
}

.add-question {
  margin-bottom: 8px;
}

.form-text {
  display: block;
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}

code {
  padding: 1px 5px;
  border-radius: 3px;
  background-color: #f5f7fa;
  color: #409eff;
  font-family: Menlo, Monaco, Consolas, monospace;
}
</style>
