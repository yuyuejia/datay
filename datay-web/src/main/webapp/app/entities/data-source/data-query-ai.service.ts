import axios from "axios";

const baseApiUrl = "api/ai/sql";

/** 生成场景模式：query 只读查询，task SQL 任务（允许 DDL/DML）。 */
export type AiSqlMode = "query" | "task";

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

export interface AiSqlGenerateResult {
  available: boolean;
  sql?: string;
  explanation?: string;
  rounds?: number;
  converged?: boolean;
  toolCalls?: AiToolTrace[];
  promptTokens?: number;
  completionTokens?: number;
}

export interface AiSqlStatus {
  available: boolean;
  model?: string;
  maxToolRounds?: number;
  toolCount?: number;
  message?: string;
}

export interface AiToolInfo {
  name: string;
  description: string;
  mutating: boolean;
  parameters?: Record<string, any>;
}

/**
 * AI SQL 助手服务。
 *
 * 后端会自行完成多轮 tool-calling 循环，前端只负责传递需求与历史对话，
 * 并展示最终 SQL 及工具调用轨迹。
 */
export default class DataQueryAiService {
  getStatus(): Promise<AiSqlStatus> {
    return axios.get(`${baseApiUrl}/status`).then((res) => res.data);
  }

  listTools(mode: AiSqlMode = "query"): Promise<AiToolInfo[]> {
    return axios
      .get(`${baseApiUrl}/tools`, { params: { mode } })
      .then((res) => res.data);
  }

  generate(
    message: string,
    dataSourceId?: number,
    history: AiHistoryMessage[] = [],
    mode: AiSqlMode = "query",
  ): Promise<AiSqlGenerateResult> {
    return axios
      .post(`${baseApiUrl}/generate`, { message, dataSourceId, history, mode })
      .then((res) => res.data);
  }
}
