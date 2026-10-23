import axios from 'axios';

import buildPaginationQueryOpts from '@/shared/sort/sorts';
import type {
  IAlertChannel,
  IAlertDelivery,
  IAlertEvent,
  IAlertRecipientOptions,
  IAlertRule,
  IAlertSummary,
  INotification,
} from '@/shared/model/alert.model';

const baseApiUrl = 'api';

const buildQuery = (params: Record<string, any>): string => {
  const filtered = Object.fromEntries(Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''));
  return buildPaginationQueryOpts(filtered);
};

export default class AlertCenterService {
  retrieveEvents(params: Record<string, any>): Promise<any> {
    return axios.get(`${baseApiUrl}/alert-events?${buildQuery(params)}`);
  }

  getEvent(id: string): Promise<IAlertEvent> {
    return axios.get(`${baseApiUrl}/alert-events/${id}`).then(res => res.data);
  }

  getSummary(): Promise<IAlertSummary> {
    return axios.get(`${baseApiUrl}/alert-events/summary`).then(res => res.data);
  }

  getDeliveries(id: string): Promise<IAlertDelivery[]> {
    return axios.get(`${baseApiUrl}/alert-events/${id}/deliveries`).then(res => res.data);
  }

  acknowledgeEvent(id: string): Promise<IAlertEvent> {
    return axios.post(`${baseApiUrl}/alert-events/${id}/ack`).then(res => res.data);
  }

  resolveEvent(id: string): Promise<IAlertEvent> {
    return axios.post(`${baseApiUrl}/alert-events/${id}/resolve`).then(res => res.data);
  }

  retrieveRules(): Promise<IAlertRule[]> {
    return axios.get(`${baseApiUrl}/alert-rules`).then(res => res.data);
  }

  retrieveRecipientOptions(): Promise<IAlertRecipientOptions> {
    return axios.get(`${baseApiUrl}/alert-rules/recipient-options`).then(res => res.data);
  }

  createRule(rule: IAlertRule): Promise<IAlertRule> {
    return axios.post(`${baseApiUrl}/alert-rules`, rule).then(res => res.data);
  }

  updateRule(rule: IAlertRule): Promise<IAlertRule> {
    return axios.put(`${baseApiUrl}/alert-rules/${rule.id}`, rule).then(res => res.data);
  }

  deleteRule(id: string): Promise<any> {
    return axios.delete(`${baseApiUrl}/alert-rules/${id}`);
  }

  retrieveChannels(): Promise<IAlertChannel[]> {
    return axios.get(`${baseApiUrl}/alert-channels`).then(res => res.data);
  }

  createChannel(channel: IAlertChannel): Promise<IAlertChannel> {
    return axios.post(`${baseApiUrl}/alert-channels`, channel).then(res => res.data);
  }

  updateChannel(channel: IAlertChannel): Promise<IAlertChannel> {
    return axios.put(`${baseApiUrl}/alert-channels/${channel.id}`, channel).then(res => res.data);
  }

  deleteChannel(id: string): Promise<any> {
    return axios.delete(`${baseApiUrl}/alert-channels/${id}`);
  }

  testChannel(id: string): Promise<any> {
    return axios.post(`${baseApiUrl}/alert-channels/${id}/test`);
  }

  retrieveNotifications(params: Record<string, any> = {}): Promise<any> {
    return axios.get(`${baseApiUrl}/notifications?${buildQuery(params)}`);
  }

  unreadCount(): Promise<number> {
    return axios.get(`${baseApiUrl}/notifications/unread-count`).then(res => res.data?.count ?? 0);
  }

  markNotificationRead(id: string): Promise<any> {
    return axios.post(`${baseApiUrl}/notifications/${id}/read`);
  }

  markAllNotificationsRead(): Promise<any> {
    return axios.post(`${baseApiUrl}/notifications/read-all`);
  }
}
