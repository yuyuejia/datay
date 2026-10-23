import { computed, defineComponent, inject, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import AlertCenterService from './alert-center.service';
import { useDateFormat } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';
import {
  ALERT_CHANNEL_TYPE_OPTIONS,
  ALERT_EVENT_TYPE_OPTIONS,
  ALERT_SEVERITY_OPTIONS,
  ALERT_STATUS_OPTIONS,
  parseChannelConfig,
  parseStringArray,
  type IAlertChannel,
  type IAlertDelivery,
  type IAlertEvent,
  type IAlertRecipientOptions,
  type IAlertRule,
  type IAlertSummary,
} from '@/shared/model/alert.model';

interface RuleForm {
  id?: string;
  name: string;
  code: string;
  eventTypes: string[];
  severity: string;
  jobType: string;
  silenceMinutes: number;
  recipients: string[];
  channelIds: string[];
  enabled: boolean;
}

interface ChannelForm {
  id?: string;
  name: string;
  code: string;
  type: string;
  enabled: boolean;
  to: string;
  cc: string;
  subjectPrefix: string;
  url: string;
  secret: string;
  headersText: string;
}

const defaultRuleForm = (): RuleForm => ({
  name: '',
  code: '',
  eventTypes: ['FAILED', 'TIMEOUT'],
  severity: 'P1',
  jobType: '',
  silenceMinutes: 30,
  recipients: ['role:ROLE_TENANT_ADMIN'],
  channelIds: [],
  enabled: true,
});

const defaultChannelForm = (): ChannelForm => ({
  name: '',
  code: '',
  type: 'EMAIL',
  enabled: true,
  to: '',
  cc: '',
  subjectPrefix: '[DataY告警]',
  url: '',
  secret: '',
  headersText: '',
});

export default defineComponent({
  name: 'AlertCenter',
  setup() {
    const dateFormat = useDateFormat();
    const service = inject('alertCenterService', () => new AlertCenterService(), true);
    const alertService = inject('alertService', () => useAlertService(), true);

    const showRequestError = (err: any) => {
      if (err?.response) {
        alertService.showHttpError(err.response);
      } else {
        ElMessage.error('请求失败，请检查网络或服务状态');
      }
    };

    const activeTab = ref('events');
    const loading = ref(false);
    const summary = ref<IAlertSummary>({});

    const events = ref<IAlertEvent[]>([]);
    const eventPage = ref(1);
    const eventSize = ref(20);
    const eventTotal = ref(0);
    const eventStatus = ref('');
    const eventSeverity = ref('');
    const eventSearch = ref('');

    const rules = ref<IAlertRule[]>([]);
    const channels = ref<IAlertChannel[]>([]);

    const selectedEvent = ref<IAlertEvent | null>(null);
    const eventDrawer = ref(false);
    const deliveries = ref<IAlertDelivery[]>([]);
    const testingChannelId = ref('');

    const ruleDialog = ref(false);
    const ruleForm = ref<RuleForm>(defaultRuleForm());
    const recipientDialog = ref(false);
    const recipientDraft = ref<string[]>([]);
    const recipientSearch = ref('');
    const recipientOptionsLoading = ref(false);
    const recipientOptionsLoaded = ref(false);
    const recipientOptions = ref<IAlertRecipientOptions>({ roles: [], users: [] });
    const channelDialog = ref(false);
    const channelForm = ref<ChannelForm>(defaultChannelForm());

    const channelName = (id?: string): string => {
      if (!id) {
        return '-';
      }
      const found = channels.value.find(item => item.id === id);
      return found?.name ?? id;
    };

    const channelOptions = computed(() =>
      channels.value.map(item => ({ label: `${item.name}（${channelTypeLabel(item.type)}）`, value: item.id })),
    );

    const loadSummary = async () => {
      try {
        summary.value = await service.getSummary();
      } catch (err) {
        showRequestError(err);
      }
    };

    const loadEvents = async () => {
      loading.value = true;
      try {
        const res = await service.retrieveEvents({
          page: eventPage.value - 1,
          size: eventSize.value,
          sort: ['lastSeen,desc'],
          status: eventStatus.value,
          severity: eventSeverity.value,
          search: eventSearch.value,
        });
        eventTotal.value = Number(res.headers['x-total-count'] ?? 0);
        events.value = res.data ?? [];
      } catch (err) {
        showRequestError(err);
      } finally {
        loading.value = false;
      }
    };

    const loadRules = async () => {
      try {
        rules.value = await service.retrieveRules();
      } catch (err) {
        showRequestError(err);
      }
    };

    const loadChannels = async () => {
      try {
        channels.value = await service.retrieveChannels();
      } catch (err) {
        showRequestError(err);
      }
    };

    const refreshAll = async () => {
      await Promise.all([loadSummary(), loadEvents(), loadRules(), loadChannels()]);
    };

    const onSearch = () => {
      eventPage.value = 1;
      loadEvents();
    };

    const resetFilters = () => {
      eventStatus.value = '';
      eventSeverity.value = '';
      eventSearch.value = '';
      eventPage.value = 1;
      loadEvents();
    };

    const openEvent = async (row: IAlertEvent) => {
      selectedEvent.value = row;
      eventDrawer.value = true;
      deliveries.value = [];
      if (row.id) {
        try {
          deliveries.value = await service.getDeliveries(row.id);
        } catch (err) {
          showRequestError(err);
        }
      }
    };

    const refreshSelectedEvent = async (id: string) => {
      if (selectedEvent.value?.id !== id) {
        return;
      }
      try {
        selectedEvent.value = await service.getEvent(id);
        deliveries.value = await service.getDeliveries(id);
      } catch {
        // 详情刷新失败不打断列表操作
      }
    };

    const acknowledgeEvent = async (row: IAlertEvent) => {
      if (!row.id) {
        return;
      }
      try {
        await service.acknowledgeEvent(row.id);
        ElMessage.success('已认领该告警');
        await Promise.all([loadEvents(), loadSummary(), refreshSelectedEvent(row.id)]);
      } catch (err) {
        showRequestError(err);
      }
    };

    const resolveEvent = async (row: IAlertEvent) => {
      if (!row.id) {
        return;
      }
      try {
        await ElMessageBox.confirm('确认将该告警标记为已恢复？', '告警闭环', { type: 'warning' });
      } catch {
        return;
      }
      try {
        await service.resolveEvent(row.id);
        ElMessage.success('告警已恢复');
        await Promise.all([loadEvents(), loadSummary(), refreshSelectedEvent(row.id)]);
      } catch (err) {
        showRequestError(err);
      }
    };

    const normalizeRecipient = (expression: string): string => {
      const value = expression.trim();
      if (!value) {
        return '';
      }
      return value.includes(':') ? value : `user:${value}`;
    };

    const normalizeRecipients = (values: string[]): string[] => Array.from(new Set(values.map(normalizeRecipient).filter(Boolean)));

    const recipientLabel = (expression: string): string => {
      if (expression.startsWith('role:')) {
        return expression.substring('role:'.length) || expression;
      }
      if (expression.startsWith('user:')) {
        return expression.substring('user:'.length) || expression;
      }
      return expression;
    };

    const recipientTagType = (expression: string): 'primary' | 'info' => (expression.startsWith('role:') ? 'info' : 'primary');

    const recipientKeyword = computed(() => recipientSearch.value.trim().toLowerCase());

    const filteredRecipientRoles = computed(() => {
      const keyword = recipientKeyword.value;
      if (!keyword) {
        return recipientOptions.value.roles;
      }
      return recipientOptions.value.roles.filter(role => role.name.toLowerCase().includes(keyword));
    });

    const filteredRecipientUsers = computed(() => {
      const keyword = recipientKeyword.value;
      if (!keyword) {
        return recipientOptions.value.users;
      }
      return recipientOptions.value.users.filter(user => user.login.toLowerCase().includes(keyword));
    });

    const knownRecipientExpressions = computed(() => {
      const known = new Set<string>();
      recipientOptions.value.roles.forEach(role => known.add(`role:${role.name}`));
      recipientOptions.value.users.forEach(user => known.add(`user:${user.login}`));
      return known;
    });

    const missingRecipientExpressions = computed(() => recipientDraft.value.filter(item => !knownRecipientExpressions.value.has(item)));

    const loadRecipientOptions = async () => {
      if (recipientOptionsLoaded.value) {
        return;
      }
      recipientOptionsLoading.value = true;
      try {
        const options = await service.retrieveRecipientOptions();
        recipientOptions.value = {
          roles: options?.roles ?? [],
          users: options?.users ?? [],
        };
        recipientOptionsLoaded.value = true;
      } catch (err) {
        showRequestError(err);
      } finally {
        recipientOptionsLoading.value = false;
      }
    };

    const openRecipientDialog = async () => {
      recipientDraft.value = normalizeRecipients(ruleForm.value.recipients);
      recipientSearch.value = '';
      recipientDialog.value = true;
      await loadRecipientOptions();
    };

    const confirmRecipients = () => {
      ruleForm.value.recipients = normalizeRecipients(recipientDraft.value);
      recipientDialog.value = false;
    };

    const removeRecipient = (expression: string) => {
      ruleForm.value.recipients = ruleForm.value.recipients.filter(item => item !== expression);
    };

    const removeDraftRecipient = (expression: string) => {
      recipientDraft.value = recipientDraft.value.filter(item => item !== expression);
    };

    const openCreateRule = () => {
      ruleForm.value = defaultRuleForm();
      ruleDialog.value = true;
    };

    const openEditRule = (rule: IAlertRule) => {
      const recipients = normalizeRecipients(parseStringArray(rule.recipients));
      ruleForm.value = {
        id: rule.id,
        name: rule.name ?? '',
        code: rule.code ?? '',
        eventTypes: (rule.eventType ?? 'FAILED,TIMEOUT').split(',').filter(Boolean),
        severity: rule.severity ?? 'P1',
        jobType: rule.jobType ?? '',
        silenceMinutes: rule.silenceMinutes ?? 30,
        recipients: recipients.length ? recipients : ['role:ROLE_TENANT_ADMIN'],
        channelIds: parseStringArray(rule.channelIds),
        enabled: rule.enabled !== false,
      };
      ruleDialog.value = true;
    };

    const saveRule = async () => {
      if (!ruleForm.value.name.trim()) {
        ElMessage.warning('请填写规则名称');
        return;
      }
      if (ruleForm.value.eventTypes.length === 0) {
        ElMessage.warning('请至少选择一种告警事件');
        return;
      }
      const recipients = normalizeRecipients(ruleForm.value.recipients);
      if (recipients.length === 0) {
        recipients.push('role:ROLE_TENANT_ADMIN');
      }
      const payload: IAlertRule = {
        id: ruleForm.value.id,
        name: ruleForm.value.name.trim(),
        code: ruleForm.value.code.trim() || undefined,
        sourceType: 'JOB',
        eventType: ruleForm.value.eventTypes.join(','),
        severity: ruleForm.value.severity,
        jobType: ruleForm.value.jobType.trim() || null,
        silenceMinutes: Number(ruleForm.value.silenceMinutes) || 0,
        recipients: JSON.stringify(recipients),
        channelIds: JSON.stringify(ruleForm.value.channelIds),
        enabled: ruleForm.value.enabled,
      };
      try {
        if (payload.id) {
          await service.updateRule(payload);
          ElMessage.success('规则已更新');
        } else {
          await service.createRule(payload);
          ElMessage.success('规则已创建');
        }
        ruleDialog.value = false;
        await Promise.all([loadRules(), loadSummary()]);
      } catch (err) {
        showRequestError(err);
      }
    };

    const toggleRule = async (rule: IAlertRule) => {
      try {
        await service.updateRule({ ...rule, enabled: rule.enabled });
        ElMessage.success(rule.enabled ? '规则已启用' : '规则已停用');
      } catch (err) {
        showRequestError(err);
        await loadRules();
      }
    };

    const deleteRule = async (rule: IAlertRule) => {
      if (!rule.id) {
        return;
      }
      try {
        await ElMessageBox.confirm(`确认删除规则「${rule.name}」？`, '删除确认', { type: 'warning' });
      } catch {
        return;
      }
      try {
        await service.deleteRule(rule.id);
        ElMessage.success('规则已删除');
        await loadRules();
      } catch (err) {
        showRequestError(err);
      }
    };

    const openCreateChannel = () => {
      channelForm.value = defaultChannelForm();
      channelDialog.value = true;
    };

    const openEditChannel = (channel: IAlertChannel) => {
      const config = parseChannelConfig(channel.config);
      channelForm.value = {
        id: channel.id,
        name: channel.name ?? '',
        code: channel.code ?? '',
        type: channel.type ?? 'EMAIL',
        enabled: channel.enabled !== false,
        to: String(config.to ?? ''),
        cc: String(config.cc ?? ''),
        subjectPrefix: String(config.subjectPrefix ?? '[DataY告警]'),
        url: String(config.url ?? ''),
        secret: String(config.secret ?? ''),
        headersText: config.headers ? JSON.stringify(config.headers, null, 2) : '',
      };
      channelDialog.value = true;
    };

    const saveChannel = async () => {
      if (!channelForm.value.name.trim()) {
        ElMessage.warning('请填写渠道名称');
        return;
      }
      let config: Record<string, unknown>;
      if (channelForm.value.type === 'EMAIL') {
        if (!channelForm.value.to.trim()) {
          ElMessage.warning('请填写收件人');
          return;
        }
        config = {
          to: channelForm.value.to.trim(),
          cc: channelForm.value.cc.trim(),
          subjectPrefix: channelForm.value.subjectPrefix.trim() || '[DataY告警]',
        };
      } else {
        if (!channelForm.value.url.trim()) {
          ElMessage.warning('请填写 Webhook 地址');
          return;
        }
        if (!/^https?:\/\//i.test(channelForm.value.url.trim())) {
          ElMessage.warning('Webhook 地址必须以 http:// 或 https:// 开头');
          return;
        }
        let headers: Record<string, string> = {};
        if (channelForm.value.headersText.trim()) {
          try {
            const parsed = JSON.parse(channelForm.value.headersText);
            if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
              ElMessage.warning('自定义请求头必须是 JSON 对象');
              return;
            }
            headers = parsed;
          } catch {
            ElMessage.warning('自定义请求头不是合法 JSON');
            return;
          }
        }
        config = { url: channelForm.value.url.trim(), secret: channelForm.value.secret.trim(), headers };
      }
      const payload: IAlertChannel = {
        id: channelForm.value.id,
        name: channelForm.value.name.trim(),
        code: channelForm.value.code.trim() || undefined,
        type: channelForm.value.type,
        config: JSON.stringify(config),
        enabled: channelForm.value.enabled,
      };
      try {
        if (payload.id) {
          await service.updateChannel(payload);
          ElMessage.success('渠道已更新');
        } else {
          await service.createChannel(payload);
          ElMessage.success('渠道已创建');
        }
        channelDialog.value = false;
        await Promise.all([loadChannels(), loadRules()]);
      } catch (err) {
        showRequestError(err);
      }
    };

    const toggleChannel = async (channel: IAlertChannel) => {
      try {
        await service.updateChannel({ ...channel, enabled: channel.enabled });
        ElMessage.success(channel.enabled ? '渠道已启用' : '渠道已停用');
      } catch (err) {
        showRequestError(err);
        await loadChannels();
      }
    };

    const testChannel = async (channel: IAlertChannel) => {
      if (!channel.id) {
        return;
      }
      testingChannelId.value = channel.id;
      try {
        await service.testChannel(channel.id);
        ElMessage.success('测试消息已发送，请检查目标邮箱或 Webhook');
      } catch (err) {
        showRequestError(err);
      } finally {
        testingChannelId.value = '';
      }
    };

    const deleteChannel = async (channel: IAlertChannel) => {
      if (!channel.id) {
        return;
      }
      try {
        await ElMessageBox.confirm(`确认删除渠道「${channel.name}」？已绑定该渠道的规则将不再外部投递。`, '删除确认', { type: 'warning' });
      } catch {
        return;
      }
      try {
        await service.deleteChannel(channel.id);
        ElMessage.success('渠道已删除');
        await Promise.all([loadChannels(), loadRules()]);
      } catch (err) {
        showRequestError(err);
      }
    };

    const statusLabel = (status?: string): string => {
      return { FIRING: '待处理', ACKNOWLEDGED: '已认领', RESOLVED: '已恢复' }[status ?? ''] ?? status ?? '-';
    };

    const statusTagType = (status?: string): string => {
      return { FIRING: 'danger', ACKNOWLEDGED: 'warning', RESOLVED: 'success' }[status ?? ''] ?? 'info';
    };

    const severityTagType = (severity?: string): string => {
      return { P0: 'danger', P1: 'warning', P2: 'info' }[severity ?? ''] ?? 'info';
    };

    const eventTypeLabel = (eventType?: string): string => {
      return { FAILED: '任务失败', TIMEOUT: '任务超时' }[eventType ?? ''] ?? eventType ?? '-';
    };

    const channelTypeLabel = (type?: string): string => {
      return ALERT_CHANNEL_TYPE_OPTIONS.find(item => item.value === type)?.label ?? type ?? '-';
    };

    const deliveryStatusLabel = (status?: string): string => {
      return { SUCCESS: '成功', FAILED: '失败', PENDING: '发送中' }[status ?? ''] ?? status ?? '-';
    };

    const channelTarget = (channel: IAlertChannel): string => {
      const config = parseChannelConfig(channel.config);
      if (channel.type === 'EMAIL') {
        return String(config.to ?? '-');
      }
      if (channel.type === 'WEBHOOK') {
        return String(config.url ?? '-');
      }
      return '-';
    };

    const ruleRecipients = (rule: IAlertRule): string =>
      parseStringArray(rule.recipients).map(normalizeRecipient).filter(Boolean).map(recipientLabel).join('、') || '-';

    const ruleChannels = (rule: IAlertRule): string => {
      const ids = parseStringArray(rule.channelIds);
      return ids.length === 0 ? '仅站内信' : ids.map(channelName).join(', ');
    };

    const onEventPageChange = (page: number) => {
      eventPage.value = page;
      loadEvents();
    };

    onMounted(refreshAll);

    return {
      dateFormat,
      activeTab,
      loading,
      summary,
      events,
      eventPage,
      eventSize,
      eventTotal,
      eventStatus,
      eventSeverity,
      eventSearch,
      rules,
      channels,
      channelOptions,
      selectedEvent,
      eventDrawer,
      deliveries,
      testingChannelId,
      ruleDialog,
      ruleForm,
      recipientDialog,
      recipientDraft,
      recipientSearch,
      recipientOptionsLoading,
      recipientOptions,
      filteredRecipientRoles,
      filteredRecipientUsers,
      missingRecipientExpressions,
      recipientLabel,
      recipientTagType,
      openRecipientDialog,
      confirmRecipients,
      removeRecipient,
      removeDraftRecipient,
      channelDialog,
      channelForm,
      alertEventTypeOptions: ALERT_EVENT_TYPE_OPTIONS,
      alertSeverityOptions: ALERT_SEVERITY_OPTIONS,
      alertStatusOptions: ALERT_STATUS_OPTIONS,
      alertChannelTypeOptions: ALERT_CHANNEL_TYPE_OPTIONS,
      refreshAll,
      loadEvents,
      loadSummary,
      onSearch,
      resetFilters,
      openEvent,
      acknowledgeEvent,
      resolveEvent,
      openCreateRule,
      openEditRule,
      saveRule,
      toggleRule,
      deleteRule,
      openCreateChannel,
      openEditChannel,
      saveChannel,
      toggleChannel,
      testChannel,
      deleteChannel,
      statusLabel,
      statusTagType,
      severityTagType,
      eventTypeLabel,
      channelTypeLabel,
      deliveryStatusLabel,
      channelTarget,
      ruleRecipients,
      ruleChannels,
      onEventPageChange,
    };
  },
});
