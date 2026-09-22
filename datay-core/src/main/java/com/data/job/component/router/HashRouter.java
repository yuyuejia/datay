package com.data.job.component.router;

import com.data.job.ComponentRegister;
import com.data.job.FlowComponent;
import com.data.job.Connection;
import com.data.job.FlowFile;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.Objects;

/**
 * HashRouter组件 - 根据FlowFile中的属性值进行哈希路由
 * 支持通过配置指定用于哈希的属性名和下游队列数量
 */
@ComponentRegister(
    value = "HashRouter",
    name = "哈希路由",
    group = "数据处理",
    desc = "根据 FlowFile 属性值哈希路由到不同下游队列。",
    order = 90
)
public class HashRouter extends FlowComponent {

    // 默认配置参数
    private String hashKey; // 用于哈希的属性名

    public HashRouter() {
        setType(ComponentType.OPERATOR);  // 设置为Operator类型
    }

    @Override
    public void execute(FlowFile flowFile) {
        // 检查是否为结束信号
        if (flowFile.getAttribute("_end") != null) {
            return;
        }
        writeRecords(flowFile);
    }

    /**
     * 根据属性值计算分区索引
     * @param attributeValue 属性值
     * @return 分区索引（0 到 partitionCount-1）
     */
    private int calculatePartitionIndex(Object attributeValue) {
        // 计算哈希值
        int hashCode = Objects.hashCode(attributeValue);
        
        // 确保哈希值为正数
        int positiveHash = hashCode & 0x7FFFFFFF;
        
        // 计算分区索引
        return positiveHash % getOutput().size();
    }

    /**
     * 将FlowFile写入指定分区
     * @param flowFile FlowFile对象
     * @param partitionIndex 分区索引
     */
    private void writeToPartition(FlowFile flowFile, int partitionIndex) {
        List<Connection> outputConnections = getOutput();
        
        // 检查分区索引是否有效
        if (partitionIndex < 0 || partitionIndex >= outputConnections.size()) {
            logError("HashRouter: 分区索引 " + partitionIndex + " 超出范围，有效范围: 0-" + (outputConnections.size() - 1));
            return;
        }

        // 获取目标连接
        Connection targetConnection = outputConnections.get(partitionIndex);
        
        // 获取对应的队列
        BlockingQueue queue = getContext().getConnections().get(this.getId() + "_" + targetConnection.getTargetId());
        if (queue != null) {
            try {
                queue.put(flowFile);
                logDebug("HashRouter: 将FlowFile路由到分区 " + partitionIndex + " (属性: " + hashKey + ")");
            } catch (InterruptedException e) {
                // 恢复中断状态
                Thread.currentThread().interrupt();
                throw new RuntimeException("HashRouter路由被中断", e);
            }
        } else {
            logError("HashRouter: 无法找到目标队列，连接ID: " + this.getId() + "_" + targetConnection.getTargetId());
        }
    }

    /**
     * 重写writeRecords方法，使用哈希路由替代默认的广播方式
     */
    @Override
    public void writeRecords(FlowFile flowFile) {
        // HashRouter使用自己的路由逻辑，不调用父类的writeRecords
        // 获取用于哈希的属性值
        Object attributeValue = flowFile.getAttribute(hashKey);
        if (attributeValue == null) {
            // 如果属性不存在，记录警告并使用默认路由（第一个分区）
            logWarn("HashRouter: 属性 '" + hashKey + "' 不存在，使用默认路由到分区 0");
            writeToPartition(flowFile, 0);
            return;
        }

        // 计算哈希值并确定目标分区
        int partitionIndex = calculatePartitionIndex(attributeValue);
        writeToPartition(flowFile, partitionIndex);
    }

    /**
     * 设置用于哈希的属性名
     * @param hashKey 属性名
     */
    public void setHashKey(String hashKey) {
        this.hashKey = hashKey;
    }
}