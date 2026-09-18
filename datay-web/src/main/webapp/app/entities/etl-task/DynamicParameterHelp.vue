<script setup>
import { ref } from 'vue';

const visible = ref(false);

const builtinParams = [
  { expr: '#{RANDOM}', desc: '0 ~ 999 之间的随机整数' },
  { expr: '#{RANDOM_LONG}', desc: '随机长整数' },
  { expr: '#{RANDOM_DOUBLE}', desc: '0 ~ 1 之间的随机小数' },
  { expr: '#{TIMESTAMP}', desc: '当前时间戳（毫秒）' },
  { expr: '#{UUID}', desc: '随机 UUID' },
];

const timeUnits = [
  { unit: 'd', desc: '天' },
  { unit: 'M', desc: '月' },
  { unit: 'y', desc: '年' },
  { unit: 'H', desc: '小时' },
  { unit: 'm', desc: '分钟' },
  { unit: 's', desc: '秒' },
];

const timeExamples = [
  { expr: '#{NOW - 1d}', desc: '昨天当前时刻，默认格式 yyyy-MM-dd HH:mm:ss' },
  { expr: '#{NOW + 2H}', desc: '2 小时后的时刻' },
  { expr: '#{NOW - 1d, yyyy-MM-dd HH:mm:ss}', desc: '昨天当前时刻，自定义格式 yyyy-MM-dd HH:mm:ss' },
  { expr: '#{NOW - 1d, timestamp}', desc: '昨天当前时刻的毫秒时间戳' },
  { expr: '#{NOW - 7d, yyyy-MM-dd}', desc: '7 天前的日期' },
  { expr: '#{NOW + 0d, yyyyMMdd}', desc: '当前时间，自定义格式' },
];

const dateKeywords = [
  { expr: 'YESTERDAY', desc: '昨天（yyyy-MM-dd）' },
  { expr: 'TOMORROW', desc: '明天（yyyy-MM-dd）' },
  { expr: 'WEEK_AGO', desc: '一周前（yyyy-MM-dd）' },
  { expr: 'NEXT_WEEK', desc: '一周后（yyyy-MM-dd）' },
  { expr: 'THIS_MONTH_FIRST_DAY', desc: '本月第一天（yyyy-MM-dd）' },
  { expr: 'THIS_MONTH_LAST_DAY', desc: '本月最后一天（yyyy-MM-dd）' },
  { expr: 'LAST_MONTH', desc: '上个月（yyyy-MM）' },
  { expr: 'NEXT_MONTH', desc: '下个月（yyyy-MM）' },
  { expr: 'LAST_YEAR', desc: '去年（yyyy）' },
  { expr: 'NEXT_YEAR', desc: '明年（yyyy）' },
];
</script>

<template>
  <span class="dynamic-param-help">
    <button type="button" class="dp-trigger" title="动态参数说明" @click="visible = true">
      <font-awesome-icon icon="circle-question" />
    </button>
    <b-modal v-model="visible" id="dynamicParamHelpModal" title="动态参数说明" size="lg" scrollable ok-only ok-title="关闭">
      <div class="dp-body">
        <p class="dp-intro">
          组件的文本类配置项支持动态参数，运行时会被自动替换为实际值。使用 <code class="dp-code">#{...}</code> 引用内置参数，
          使用 <code class="dp-code">${attr:名称}</code> 引用上游传递的属性。
        </p>

        <div class="dp-section-title">一、内置参数 #{...}</div>
        <table class="dp-table">
          <thead>
            <tr>
              <th style="width: 260px">表达式</th>
              <th>说明</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in builtinParams" :key="item.expr">
              <td><code class="dp-code">{{ item.expr }}</code></td>
              <td>{{ item.desc }}</td>
            </tr>
          </tbody>
        </table>

        <div class="dp-section-title">二、时间计算 #{NOW ± N 单位[, 格式]}</div>
        <p class="dp-note">
          以当前时间为基准做加减计算，单位区分大小写：
          <span v-for="(u, idx) in timeUnits" :key="u.unit">
            <code class="dp-code">{{ u.unit }}</code> {{ u.desc }}<span v-if="idx < timeUnits.length - 1">、</span>
          </span>
          。逗号后可指定输出格式（Java <code class="dp-code">DateTimeFormatter</code> 模式），省略时默认
          <code class="dp-code">yyyy-MM-dd HH:mm:ss</code>；也可填 <code class="dp-code">timestamp</code> 输出毫秒时间戳。
        </p>
        <table class="dp-table">
          <thead>
            <tr>
              <th style="width: 320px">示例</th>
              <th>结果</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in timeExamples" :key="item.expr">
              <td><code class="dp-code">{{ item.expr }}</code></td>
              <td>{{ item.desc }}</td>
            </tr>
          </tbody>
        </table>
        <p class="dp-note">注意：单独使用 <code class="dp-code">#{NOW}</code> 不会被替换，需要带偏移量，例如 <code class="dp-code">#{NOW + 0d}</code>。</p>

        <div class="dp-section-title">三、日期关键字 #{关键字[, 格式]}</div>
        <p class="dp-note">同样支持在逗号后追加自定义输出格式。</p>
        <table class="dp-table">
          <thead>
            <tr>
              <th style="width: 320px">表达式</th>
              <th>结果</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in dateKeywords" :key="item.expr">
              <td>
                <code class="dp-code">#&#123;{{ item.expr }}&#125;</code>
                <span class="dp-muted"> / </span>
                <code class="dp-code">#&#123;{{ item.expr }}, yyyyMMdd&#125;</code>
              </td>
              <td>{{ item.desc }}</td>
            </tr>
          </tbody>
        </table>

        <div class="dp-section-title">四、引用上游属性 ${...}</div>
        <table class="dp-table">
          <thead>
            <tr>
              <th style="width: 260px">写法</th>
              <th>说明</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><code class="dp-code">${attr:属性名}</code></td>
              <td>引用上游 FlowFile 中的属性值（推荐写法）</td>
            </tr>
            <tr>
              <td><code class="dp-code">${属性名}</code></td>
              <td>等价写法，部分组件支持</td>
            </tr>
            <tr>
              <td><code class="dp-code">${attr:FLOW_FILE_DATA}</code></td>
              <td>引用当前 FlowFile 的数据内容</td>
            </tr>
          </tbody>
        </table>
        <p class="dp-note">
          可用的属性名称取决于上游组件。可在节点配置弹窗的「调试」页中查看上一次运行的「关键属性」，据此引用对应名称。
        </p>
      </div>
    </b-modal>
  </span>
</template>

<style scoped>
.dynamic-param-help {
  display: inline-flex;
}

.dp-trigger {
  display: inline-flex;
  align-items: center;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--el-text-color-regular, #606266);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}

.dp-trigger:hover {
  color: var(--el-color-primary, #409eff);
}

.dp-body {
  padding-right: 4px;
}

.dp-intro {
  margin-bottom: 12px;
  color: var(--el-text-color-regular, #606266);
  font-size: 13px;
  line-height: 1.7;
}

.dp-section-title {
  margin: 16px 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.dp-note {
  margin: 6px 0;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  line-height: 1.7;
}

.dp-muted {
  color: var(--el-text-color-secondary, #909399);
}

.dp-code {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  background-color: var(--el-fill-color-light, #f5f7fa);
  color: var(--el-color-primary, #409eff);
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.dp-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.dp-table th,
.dp-table td {
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  padding: 6px 10px;
  text-align: left;
  vertical-align: top;
}

.dp-table th {
  background-color: var(--el-fill-color-light, #f5f7fa);
  color: var(--el-text-color-primary, #303133);
  font-weight: 600;
}

.dp-table td {
  color: var(--el-text-color-regular, #606266);
}
</style>
