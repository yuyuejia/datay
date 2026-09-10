package com.data.job;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.*;

// DAG解析器
public class DAGParser {

    private final Map<String, Component> components = new HashMap<>();

    private final Map<String, List<String>> edges = new HashMap<>();

    public void parse(String json) throws IllegalArgumentException {

        JSONObject jsonObj = JSONObject.parse(json);
        if (jsonObj == null) {
            throw new IllegalArgumentException("jobContext 不是有效的 JSON");
        }
        JSONArray units = jsonObj.getJSONArray("units");
        if (units == null || units.isEmpty()) {
            throw new IllegalArgumentException("jobContext 缺少 units 字段或 units 为空，无法解析 DAG。请确认 ETL 任务已正确保存。");
        }
        JSONArray connections = jsonObj.getJSONArray("connections");
        if (connections == null) {
            connections = new JSONArray();
        }

        for (JSONObject unit : units.toList(JSONObject.class)) {
            String id = unit.getString(".id");
            unit.put("id", id);
            String className = unit.getString(".name");
            unit.put("name", className);
            JSONObject datasource = unit.getJSONObject("datasource");
            if(datasource == null) {
                JSONObject sourceId  = unit.getJSONObject("sourceId");
                if(sourceId != null) {
                    unit.put("datasource", sourceId);
                }
            }
            Component component = ComponentFactory.create(className, unit);
            
            // 设置并发数
            if (unit.containsKey("parallelism")) {
                component.setParallelism(unit.getString("parallelism"));
            }
            
            components.put(id, component);
        }

        for (JSONObject connection : connections.toList(JSONObject.class)) {
            String sourceId = connection.getString("sourceId");
            String targetId = connection.getString("targetId");
            int sourcePort = connection.getIntValue("sourcePort");
            Component source = components.get(sourceId);
            Component target = components.get(targetId);
            if (source == null || target == null) {
                continue;
            }
            source.getOutput().add(new Connection(sourceId, targetId, sourcePort));
            target.getInput().add(new Connection(sourceId, targetId, sourcePort));
            List<String> targets = edges.getOrDefault(sourceId, new ArrayList<>());
            targets.add(targetId);
            edges.put(sourceId, targets);
        }
    }

    public List<Component> topologicalSort() {
        // 初始化入度表和邻接表
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjList = new HashMap<>(edges);
        List<Component> executionOrder = new ArrayList<>();

        // 构建入度表
        components.keySet().forEach(id -> inDegree.put(id, 0));
        adjList.values().forEach(targets -> targets.forEach(target -> inDegree.put(target, inDegree.getOrDefault(target, 0) + 1)));

        Queue<String> queue = new LinkedList<>();

        // 初始化第一层可并行节点
        inDegree.forEach((id, degree) -> {
            if (degree == 0) queue.add(id);
        });

        // 层级式拓扑排序
        while (!queue.isEmpty()) {
            int levelSize = queue.size();

            // 并行执行当前层级所有组件
            for (int i = 0; i < levelSize; i++) {
                String nodeId = queue.poll();
                executionOrder.add(components.get(nodeId));

                // 更新后继节点入度
                for (String neighbor : adjList.getOrDefault(nodeId, new ArrayList<>())) {
                    int newDegree = inDegree.get(neighbor) - 1;
                    inDegree.put(neighbor, newDegree);
                    if (newDegree == 0) {
                        queue.add(neighbor);
                    }
                }
            }

            // 添加层级分隔符
            if (!queue.isEmpty()) {
                executionOrder.add(null);
            }
        }

        // 移除最后的null分隔符
        if (!executionOrder.isEmpty() && executionOrder.get(executionOrder.size() - 1) == null) {
            executionOrder.remove(executionOrder.size() - 1);
        }

        return executionOrder;
    }

    public Map<String, List<String>> getEdges() {
        return edges;
    }
}