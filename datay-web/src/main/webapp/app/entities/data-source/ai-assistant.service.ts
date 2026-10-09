import axios from "axios";

const baseApiUrl = "api/ai";

export interface AiHistoryMessage {
  role: "user" | "assistant";
  content: string;
}

export interface AiToolTrace {
  name: string;
  arguments?: Record<string, any>;
  success: boolean;
  elapsedMs: number;
}

export interface AiMetricQueryData {
  columns?: string[];
  rows?: Record<string, any>[];
  sql?: string;
  totalRows?: number;
  truncated?: boolean;
  parsed?: Record<string, any>;
}

export interface AiGenerateResult {
  available: boolean;
  /** 模型抽取出的最终 SQL（SQL 类助手）。 */
  sql?: string;
  /** 模型抽取出的最终产物，脚本类助手用它承载生成的 Java 代码。 */
  code?: string;
  explanation?: string;
  rounds?: number;
  converged?: boolean;
  toolCalls?: AiToolTrace[];
  /** 指标问数产物：一次指标查询的列与数据行，供前端直接渲染数据表。 */
  data?: AiMetricQueryData;
  promptTokens?: number;
  completionTokens?: number;
}

export interface AiStatus {
  available: boolean;
  model?: string;
  maxToolRounds?: number;
  toolCount?: number;
  assistantCount?: number;
  message?: string;
}

export interface AiToolInfo {
  name: string;
  description: string;
  mutating: boolean;
  parameters?: Record<string, any>;
}

/** 后端注册的 AI 助手描述，前端据此动态渲染标题、示例与能力清单。 */
export interface AiAssistantInfo {
  id: string;
  name: string;
  description: string;
  allowWrites: boolean;
  samplePrompts: string[];
  tools: AiToolInfo[];
}

/**
 * AI 助手服务。
 *
 * 助手由后端注册与描述，前端只负责选定 assistantId，并传递需求与历史对话；
 * 后端会自行完成多轮 tool-calling 循环，前端展示最终 SQL 及工具调用轨迹。
 */
export default class AiAssistantService {
  getStatus(): Promise<AiStatus> {
    return axios.get(`${baseApiUrl}/status`).then((res) => res.data);
  }

  listAssistants(): Promise<AiAssistantInfo[]> {
    return axios.get(`${baseApiUrl}/assistants`).then((res) => res.data);
  }

  generate(
    message: string,
    dataSourceId: string | undefined,
    history: AiHistoryMessage[] = [],
    assistantId = "query",
    contextData?: Record<string, any>,
  ): Promise<AiGenerateResult> {
    return axios
      .post(`${baseApiUrl}/generate`, {
        message,
        dataSourceId,
        history,
        assistantId,
        contextData,
      })
      .then((res) => res.data);
  }
}
