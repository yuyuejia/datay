const JOB_TYPE_LABELS: Record<string, string> = {
  ETL: 'ETL 任务',
  DATA_COMPARE: '数据比对',
  SQL: 'SQL 任务',
  DAG: '任务编排',
  SHELL: 'Shell 任务',
  SPARK: 'Spark 任务',
  FLINK: 'Flink 任务',
  DEMO: '示例任务',
};

export const jobTypeLabel = (type?: string | null): string => {
  if (!type) return '-';
  return JOB_TYPE_LABELS[type.toUpperCase()] ?? type;
};
