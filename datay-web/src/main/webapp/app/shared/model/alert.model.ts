export type AlertSeverity = 'P0' | 'P1' | 'P2';

export interface IAlertEvent {
  id?: string;
  ruleId?: string;
  sourceType?: string;
  eventType?: string;
  severity?: AlertSeverity | string;
  title?: string;
  content?: string;
  jobCode?: string;
  jobName?: string;
  jobInstanceId?: string;
  instanceCode?: string;
  project?: string;
  status?: string;
  eventCount?: number;
  firstSeen?: Date | string;
  lastSeen?: Date | string;
  ackBy?: string;
  ackTime?: Date | string;
  resolveBy?: string;
  resolveTime?: Date | string;
  createTime?: Date | string;
  updateTime?: Date | string;
}

export interface IAlertDelivery {
  id?: string;
  eventId?: string;
  channelId?: string;
  channelType?: string;
  target?: string;
  status?: string;
  attempt?: number;
  errorMessage?: string;
  sentTime?: Date | string;
  createTime?: Date | string;
}

export interface IAlertRule {
  id?: string;
  name?: string;
  code?: string;
  sourceType?: string;
  eventType?: string;
  severity?: AlertSeverity | string;
  project?: string | null;
  jobType?: string | null;
  silenceMinutes?: number;
  recipients?: string;
  channelIds?: string;
  defaultRule?: boolean;
  enabled?: boolean;
  createTime?: Date | string;
  updateTime?: Date | string;
}

export interface IAlertChannel {
  id?: string;
  name?: string;
  code?: string;
  type?: string;
  config?: string;
  enabled?: boolean;
  createTime?: Date | string;
  updateTime?: Date | string;
}

export interface INotification {
  id?: string;
  eventId?: string;
  title?: string;
  content?: string;
  readFlag?: boolean;
  createTime?: Date | string;
  readTime?: Date | string;
}

export interface IAlertSummary {
  firing?: number;
  acknowledged?: number;
  resolved?: number;
}

export interface IAlertChannelConfig {
  to?: string;
  cc?: string;
  subjectPrefix?: string;
  url?: string;
  secret?: string;
  headers?: Record<string, string>;
  [key: string]: unknown;
}

export const ALERT_EVENT_TYPE_OPTIONS = [
  { label: '任务失败', value: 'FAILED' },
  { label: '任务超时', value: 'TIMEOUT' },
];

export const ALERT_SEVERITY_OPTIONS = [
  { label: 'P0 紧急', value: 'P0' },
  { label: 'P1 重要', value: 'P1' },
  { label: 'P2 一般', value: 'P2' },
];

export const ALERT_STATUS_OPTIONS = [
  { label: '待处理', value: 'FIRING' },
  { label: '已认领', value: 'ACKNOWLEDGED' },
  { label: '已恢复', value: 'RESOLVED' },
];

export const ALERT_CHANNEL_TYPE_OPTIONS = [
  { label: '邮件', value: 'EMAIL' },
  { label: 'Webhook', value: 'WEBHOOK' },
];

export const parseStringArray = (value?: string | null): string[] => {
  if (!value) {
    return [];
  }
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.map(item => String(item)) : [];
  } catch {
    return [];
  }
};

export const parseChannelConfig = (value?: string | null): IAlertChannelConfig => {
  if (!value) {
    return {};
  }
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
};
