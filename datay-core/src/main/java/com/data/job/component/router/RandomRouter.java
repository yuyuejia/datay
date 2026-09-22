package com.data.job.component.router;

import com.data.job.ComponentRegister;
import com.data.job.FlowComponent;
import com.data.job.Connection;
import com.data.job.FlowFile;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.Random;

/**
 * RandomRouter组件 - 将FlowFile随机路由到下游队列
 * 上游输入的FlowFile会随机发送到下游队列中
 */
@ComponentRegister(
    value = "RandomRouter",
    name = "随机路由",
    group = "数据处理",
    desc = "将 FlowFile 随机路由到下游队列。",
    order = 100
)
public class RandomRouter extends FlowComponent {

    private Random random;

    public RandomRouter() {
        this.random = new Random();
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
     * 随机计算分区索引
     * @return 分区索引（0 到 partitionCount-1）
     */
    private int calculateRandomPartitionIndex() {
        List<Connection> outputConnections = getOutput();
        if (outputConnections.isEmpty()) {
            return 0;
        }
        return random.nextInt(outputConnections.size());
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
            logError("RandomRouter: 分区索引 " + partitionIndex + " 超出范围，有效范围: 0-" + (outputConnections.size() - 1));
            return;
        }

        // 获取目标连接
        Connection targetConnection = outputConnections.get(partitionIndex);
        
        // 获取对应的队列
        BlockingQueue queue = getContext().getConnections().get(this.getId() + "_" + targetConnection.getTargetId());
        if (queue != null) {
            try {
                queue.put(flowFile);
                logDebug("RandomRouter: 将FlowFile随机路由到分区 " + partitionIndex);
            } catch (InterruptedException e) {
                // 恢复中断状态
                Thread.currentThread().interrupt();
                throw new RuntimeException("RandomRouter路由被中断", e);
            }
        } else {
            logError("RandomRouter: 无法找到目标队列，连接ID: " + this.getId() + "_" + targetConnection.getTargetId());
        }
    }

    /**
     * 重写writeRecords方法，使用随机路由替代默认的广播方式
     */
    @Override
    public void writeRecords(FlowFile flowFile) {
        // RandomRouter使用自己的路由逻辑，不调用父类的writeRecords
        // 随机计算分区索引
        int partitionIndex = calculateRandomPartitionIndex();
        writeToPartition(flowFile, partitionIndex);
    }

    /**
     * 设置随机种子，用于测试和调试
     * @param seed 随机种子
     */
    public void setRandomSeed(long seed) {
        this.random = new Random(seed);
    }
}