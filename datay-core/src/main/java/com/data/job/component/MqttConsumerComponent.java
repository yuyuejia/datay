package com.data.job.component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentRegister;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@ComponentRegister(
    value = "MqttConsumer",
    name = "MQTT消费",
    group = "实时输入",
    desc = "订阅 MQTT 主题消息并转换为 FlowFile 传递到下游。",
    order = 35
)
public class MqttConsumerComponent extends FlowComponent {

    private String brokerUrl = "tcp://localhost:1883";
    private String clientId;
    private String topic;
    private int qos = 1;
    private String username;
    private String password;
    private boolean cleanSession = true;
    private boolean automaticReconnect = true;
    private int keepAliveInterval = 60;
    private int connectionTimeout = 10;
    private int maxInflight = 100;
    private int batchSize = 100;
    private int consumerTimeoutMs = 1000;
    private int maxQueueSize = 10000;
    private String payloadFormat = "AUTO";

    private volatile boolean isRunning = false;
    private volatile Throwable lastConnectionError;
    private BlockingQueue<JSONObject> messageQueue;
    private MqttClient mqttClient;

    public MqttConsumerComponent() {
        setType(ComponentType.SOURCE);
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (getContext() != null && getContext().isDebugMode()) {
            executeDebugMock();
            return;
        }

        if (getParallelism() > 1) {
            throw new IllegalArgumentException(
                "MQTT消费组件不支持并发数大于1：MQTT 没有消费组语义，多线程订阅同一主题会导致消息重复消费"
            );
        }
        if (topic == null || topic.trim().isEmpty()) {
            throw new IllegalArgumentException("MQTT消费组件未配置订阅主题");
        }
        if (brokerUrl == null || brokerUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("MQTT消费组件未配置 Broker 地址");
        }
        String url = brokerUrl.trim();
        if (!url.startsWith("tcp://") && !url.startsWith("ssl://") && !url.startsWith("ws://") && !url.startsWith("wss://")) {
            throw new IllegalArgumentException("MQTT Broker 地址必须以 tcp://、ssl://、ws:// 或 wss:// 开头");
        }

        logInfo("MQTT消费组件开始执行，brokerUrl: " + url + ", topic: " + topic + ", qos: " + qos);
        messageQueue = new LinkedBlockingQueue<>(Math.max(Math.max(maxQueueSize, batchSize), 1));
        isRunning = true;
        lastConnectionError = null;

        try {
            mqttClient = createClient(url);
            connectAndSubscribe();
            consumeMessages();
        } catch (Exception e) {
            logError("MQTT消费组件执行失败: " + e.getMessage());
            throw new RuntimeException("MQTT消费组件执行失败", e);
        } finally {
            closeClient();
        }
    }

    private MqttClient createClient(String url) throws MqttException {
        String resolvedClientId = clientId != null && !clientId.trim().isEmpty()
            ? clientId.trim()
            : "datay-" + (getId() != null && !getId().trim().isEmpty() ? getId() : UUID.randomUUID().toString());
        MqttClient client = new MqttClient(url, resolvedClientId, new MemoryPersistence());
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    logInfo("MQTT连接已恢复，重新订阅主题: " + topic);
                    subscribe(client);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                lastConnectionError = cause;
                logWarn("MQTT连接断开: " + (cause == null ? "unknown" : cause.getMessage()));
            }

            @Override
            public void messageArrived(String arrivedTopic, MqttMessage message) {
                enqueueMessage(arrivedTopic, message);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });
        return client;
    }

    private void connectAndSubscribe() throws MqttException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(cleanSession);
        options.setAutomaticReconnect(automaticReconnect);
        options.setKeepAliveInterval(Math.max(keepAliveInterval, 5));
        options.setConnectionTimeout(Math.max(connectionTimeout, 1));
        options.setMaxInflight(Math.max(maxInflight, 1));
        if (username != null && !username.trim().isEmpty()) {
            options.setUserName(username.trim());
        }
        if (password != null) {
            options.setPassword(password.toCharArray());
        }

        mqttClient.connect(options);
        logInfo("MQTT连接成功，clientId: " + mqttClient.getClientId());
        subscribe(mqttClient);
    }

    private void subscribe(MqttClient client) {
        try {
            client.subscribe(topic.trim(), qos);
            logInfo("MQTT订阅成功，topic: " + topic + ", qos: " + qos);
        } catch (MqttException e) {
            logError("MQTT订阅失败: " + e.getMessage());
            throw new RuntimeException("MQTT订阅失败", e);
        }
    }

    private void enqueueMessage(String arrivedTopic, MqttMessage message) {
        if (!isRunning) {
            return;
        }
        JSONObject record = toRecord(arrivedTopic, message);
        try {
            if (!messageQueue.offer(record, 1, TimeUnit.SECONDS)) {
                logWarn("MQTT消息队列已满，丢弃消息，topic: " + arrivedTopic);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    JSONObject toRecord(String arrivedTopic, MqttMessage message) {
        JSONObject record = new JSONObject();
        record.put("_topic", arrivedTopic);
        record.put("_qos", message.getQos());
        record.put("_retained", message.isRetained());
        record.put("_duplicate", message.isDuplicate());
        record.put("_messageId", message.getId());
        record.put("_timestamp", System.currentTimeMillis());

        String payload = message.getPayload() == null
            ? ""
            : new String(message.getPayload(), StandardCharsets.UTF_8);
        String format = resolvedPayloadFormat();
        if ("TEXT".equals(format)) {
            record.put("_payload", payload);
            return record;
        }
        if ("JSON".equals(format) || looksLikeJson(payload)) {
            try {
                Object parsed = JSON.parse(payload);
                if (parsed instanceof JSONObject) {
                    record.putAll((JSONObject) parsed);
                    return record;
                }
                if (parsed instanceof JSONArray) {
                    JSONArray array = (JSONArray) parsed;
                    if (array.size() == 1 && array.get(0) instanceof JSONObject) {
                        record.putAll((JSONObject) array.get(0));
                    } else {
                        record.put("_payload", array);
                    }
                    return record;
                }
            } catch (Exception e) {
                logDebug("MQTT消息体不是合法 JSON，按文本处理: " + e.getMessage());
            }
        }
        record.put("_payload", payload);
        return record;
    }

    private boolean looksLikeJson(String payload) {
        if (payload == null) {
            return false;
        }
        String trimmed = payload.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private void consumeMessages() {
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            JSONObject first;
            try {
                first = messageQueue.poll(Math.max(consumerTimeoutMs, 100), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (first == null) {
                checkConnection();
                continue;
            }

            List<JSONObject> batch = new ArrayList<>(batchSize);
            batch.add(first);
            messageQueue.drainTo(batch, Math.max(batchSize - 1, 0));
            sendBatch(batch);
        }
    }

    private void checkConnection() {
        if (mqttClient == null || mqttClient.isConnected()) {
            return;
        }
        if (!automaticReconnect) {
            throw new RuntimeException(
                "MQTT连接已断开且未启用自动重连",
                lastConnectionError
            );
        }
        logDebug("MQTT连接已断开，等待自动重连");
    }

    private void sendBatch(List<JSONObject> records) {
        JSONArray jsonArray = new JSONArray();
        jsonArray.addAll(records);

        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(jsonArray);
        flowFile.setAttribute("_mqttTopic", topic);
        flowFile.setAttribute("_recordCount", records.size());
        flowFile.setAttribute("_payloadFormat", resolvedPayloadFormat());
        writeRecords(flowFile);

        logInfo("已成功发送 " + records.size() + " 条MQTT消息到下游组件");
    }

    private void executeDebugMock() {
        JSONObject record = new JSONObject();
        record.put("_topic", topic);
        record.put("_qos", qos);
        record.put("_retained", false);
        record.put("_duplicate", false);
        record.put("_messageId", 0);
        record.put("_timestamp", System.currentTimeMillis());
        record.put("id", 1);
        record.put("name", "debug");
        record.put("event_time", new Timestamp(System.currentTimeMillis()));

        JSONArray jsonArray = new JSONArray();
        jsonArray.add(record);

        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(jsonArray);
        flowFile.setAttribute("_mqttTopic", topic);
        flowFile.setAttribute("_recordCount", 1);
        flowFile.setAttribute("_payloadFormat", resolvedPayloadFormat());
        flowFile.setAttribute("_debugMock", true);
        writeRecords(flowFile);
        logInfo("调试模式：根据组件能力生成模拟 MQTT 消息，topic: " + topic);
    }

    boolean isConnected() {
        MqttClient client = mqttClient;
        return client != null && client.isConnected();
    }

    private synchronized void closeClient() {
        isRunning = false;
        MqttClient client = mqttClient;
        mqttClient = null;
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnectForcibly(1000, 1000);
            }
        } catch (Exception e) {
            logWarn("MQTT断开连接失败: " + e.getMessage());
        }
        try {
            client.close();
            logInfo("MQTT客户端已关闭");
        } catch (Exception e) {
            logWarn("MQTT客户端关闭失败: " + e.getMessage());
        }
    }

    private String resolvedPayloadFormat() {
        if (payloadFormat == null || payloadFormat.trim().isEmpty()) {
            return "AUTO";
        }
        String format = payloadFormat.trim().toUpperCase();
        if ("JSON".equals(format) || "TEXT".equals(format)) {
            return format;
        }
        return "AUTO";
    }

    @Override
    public void stop() {
        isRunning = false;
        super.stop();
        closeClient();
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public void setBrokerUrl(String brokerUrl) {
        if (brokerUrl != null && !brokerUrl.trim().isEmpty()) {
            this.brokerUrl = brokerUrl.trim();
        }
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos >= 0 && qos <= 2 ? qos : 1;
    }

    public void setQos(String qos) {
        if (qos != null && !qos.trim().isEmpty()) {
            try {
                setQos(Integer.parseInt(qos.trim()));
            } catch (NumberFormatException e) {
                this.qos = 1;
            }
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isCleanSession() {
        return cleanSession;
    }

    public void setCleanSession(boolean cleanSession) {
        this.cleanSession = cleanSession;
    }

    public void setCleanSession(String cleanSession) {
        if (cleanSession != null && !cleanSession.trim().isEmpty()) {
            this.cleanSession = Boolean.parseBoolean(cleanSession.trim());
        }
    }

    public boolean isAutomaticReconnect() {
        return automaticReconnect;
    }

    public void setAutomaticReconnect(boolean automaticReconnect) {
        this.automaticReconnect = automaticReconnect;
    }

    public void setAutomaticReconnect(String automaticReconnect) {
        if (automaticReconnect != null && !automaticReconnect.trim().isEmpty()) {
            this.automaticReconnect = Boolean.parseBoolean(automaticReconnect.trim());
        }
    }

    public int getKeepAliveInterval() {
        return keepAliveInterval;
    }

    public void setKeepAliveInterval(int keepAliveInterval) {
        this.keepAliveInterval = keepAliveInterval;
    }

    public void setKeepAliveInterval(String keepAliveInterval) {
        if (keepAliveInterval != null && !keepAliveInterval.trim().isEmpty()) {
            try {
                this.keepAliveInterval = Integer.parseInt(keepAliveInterval.trim());
            } catch (NumberFormatException e) {
                this.keepAliveInterval = 60;
            }
        }
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public void setConnectionTimeout(String connectionTimeout) {
        if (connectionTimeout != null && !connectionTimeout.trim().isEmpty()) {
            try {
                this.connectionTimeout = Integer.parseInt(connectionTimeout.trim());
            } catch (NumberFormatException e) {
                this.connectionTimeout = 10;
            }
        }
    }

    public int getMaxInflight() {
        return maxInflight;
    }

    public void setMaxInflight(int maxInflight) {
        this.maxInflight = maxInflight;
    }

    public void setMaxInflight(String maxInflight) {
        if (maxInflight != null && !maxInflight.trim().isEmpty()) {
            try {
                this.maxInflight = Integer.parseInt(maxInflight.trim());
            } catch (NumberFormatException e) {
                this.maxInflight = 100;
            }
        }
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void setBatchSize(String batchSize) {
        if (batchSize != null && !batchSize.trim().isEmpty()) {
            try {
                this.batchSize = Integer.parseInt(batchSize.trim());
            } catch (NumberFormatException e) {
                this.batchSize = 100;
            }
        }
    }

    public int getConsumerTimeoutMs() {
        return consumerTimeoutMs;
    }

    public void setConsumerTimeoutMs(int consumerTimeoutMs) {
        this.consumerTimeoutMs = consumerTimeoutMs;
    }

    public void setConsumerTimeoutMs(String consumerTimeoutMs) {
        if (consumerTimeoutMs != null && !consumerTimeoutMs.trim().isEmpty()) {
            try {
                this.consumerTimeoutMs = Integer.parseInt(consumerTimeoutMs.trim());
            } catch (NumberFormatException e) {
                this.consumerTimeoutMs = 1000;
            }
        }
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public void setMaxQueueSize(String maxQueueSize) {
        if (maxQueueSize != null && !maxQueueSize.trim().isEmpty()) {
            try {
                this.maxQueueSize = Integer.parseInt(maxQueueSize.trim());
            } catch (NumberFormatException e) {
                this.maxQueueSize = 10000;
            }
        }
    }

    public String getPayloadFormat() {
        return payloadFormat;
    }

    public void setPayloadFormat(String payloadFormat) {
        this.payloadFormat = payloadFormat;
    }
}
