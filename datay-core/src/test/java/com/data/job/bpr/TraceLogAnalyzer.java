package com.data.job.bpr;

import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 分析DuckDB中trace_log表的IUAP调用链路
 */
public class TraceLogAnalyzer {
    // 匹配iuap的正则（不区分大小写）
    private static final Pattern IUAP_PATTERN = Pattern.compile("iuap", Pattern.CASE_INSENSITIVE);
    private static final String DUCKDB_URL = "jdbc:duckdb:v5_0115.duckdb";

    // 存储调用链路节点
    static class TraceNode {
        String traceId;
        String spanId;
        String parentId;
        String service;
        String remoteCallMethod;
        String beginTs;
        String action;
        int level;

        public TraceNode(String traceId, String spanId, String parentId, String service, String remoteCallMethod,
                         String beginTs, String action, int level) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.parentId = parentId;
            this.service = service;
            this.remoteCallMethod = remoteCallMethod;
            this.beginTs = beginTs;
            this.action = action;
            this.level = level;
        }

        // 检查当前节点service是否包含iuap
        public boolean containsIuap() {
            return IUAP_PATTERN.matcher(service).find();
        }

        @Override
        public String toString() {
            return String.format("层级%2d | sid: %-10s | pId: %-10s | %s | %s | %s",
                    level, 
                    spanId.length() > 10 ? spanId.substring(0, 10) : spanId,
                    parentId.length() > 10 ? parentId.substring(0, 10) : parentId,
                    service,remoteCallMethod, action);
        }
    }

    // 构建调用树：key=traceId, value=Map(spanId -> TraceNode)
    private static Map<String, Map<String, TraceNode>> buildTraceTree(Connection conn) throws SQLException {
        Map<String, Map<String, TraceNode>> traceTree = new HashMap<>();
        
        // 读取trace_log核心数据
        String sql = "SELECT traceid, spanid, parentId, service,remoteCallMethod, beginTs, action, leval FROM main.trace_log";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                String traceId = rs.getString("traceid");
                TraceNode node = new TraceNode(
                        traceId,
                        rs.getString("spanid"),
                        rs.getString("parentId"),
                        rs.getString("service"),
                        rs.getString("remoteCallMethod"),
                        rs.getString("beginTs"),
                        rs.getString("action"),
                        rs.getInt("leval")
                );
                
                // 初始化traceId对应的span映射
                traceTree.computeIfAbsent(traceId, k -> new HashMap<>())
                        .put(node.spanId, node);
            }
        }
        return traceTree;
    }

    // 构建子节点映射：key=traceId, value=Map(parentSpanId -> List<childSpanId>)
    private static Map<String, Map<String, List<String>>> buildChildMap(Map<String, Map<String, TraceNode>> traceTree) {
        Map<String, Map<String, List<String>>> childMap = new HashMap<>();
        
        for (Map.Entry<String, Map<String, TraceNode>> traceEntry : traceTree.entrySet()) {
            String traceId = traceEntry.getKey();
            Map<String, TraceNode> spanMap = traceEntry.getValue();
            
            // 初始化当前traceId的子节点映射
            childMap.computeIfAbsent(traceId, k -> new HashMap<>());
            
            for (TraceNode node : spanMap.values()) {
                String parentId = node.parentId;
                // 为父节点添加子节点spanId
                childMap.get(traceId)
                        .computeIfAbsent(parentId, k -> new ArrayList<>())
                        .add(node.spanId);
            }
        }
        return childMap;
    }

    // 递归检查节点及其所有子节点是否都包含iuap
    private static boolean checkAllChildrenIuap(String traceId, String spanId,
                                               Map<String, Map<String, TraceNode>> traceTree,
                                               Map<String, Map<String, List<String>>> childMap) {
        // 获取当前节点
        TraceNode currentNode = traceTree.get(traceId).get(spanId);
        if (currentNode == null || !currentNode.containsIuap()) {
            return false;
        }
        
        // 获取当前节点的所有子节点
        List<String> childSpanIds = childMap.get(traceId).getOrDefault(spanId, Collections.emptyList());
        for (String childSpanId : childSpanIds) {
            // 递归检查子节点
            if (!checkAllChildrenIuap(traceId, childSpanId, traceTree, childMap)) {
                return false;
            }
        }
        return true;
    }

    // 收集符合条件的完整链路
    private static List<List<TraceNode>> collectValidChains(Map<String, Map<String, TraceNode>> traceTree,
                                                           Map<String, Map<String, List<String>>> childMap) {
        List<List<TraceNode>> validChains = new ArrayList<>();
        
        for (Map.Entry<String, Map<String, TraceNode>> traceEntry : traceTree.entrySet()) {
            String traceId = traceEntry.getKey();
            Map<String, TraceNode> spanMap = traceEntry.getValue();
            
            for (TraceNode node : spanMap.values()) {
                // 找到包含iuap且下游全包含iuap的节点
                if (node.containsIuap() && checkAllChildrenIuap(traceId, node.spanId, traceTree, childMap)) {
                    // 收集该节点的完整链路（当前节点+所有子节点）
                    List<TraceNode> chain = new ArrayList<>();
                    collectChainNodes(traceId, node.spanId, traceTree, childMap, chain);
                    // 按层级排序
                    chain.sort(Comparator.comparingInt(n -> n.level));
                    validChains.add(chain);
                }
            }
        }
        return validChains;
    }

    // 递归收集链路节点
    private static void collectChainNodes(String traceId, String spanId,
                                         Map<String, Map<String, TraceNode>> traceTree,
                                         Map<String, Map<String, List<String>>> childMap,
                                         List<TraceNode> chain) {
        TraceNode node = traceTree.get(traceId).get(spanId);
        if (node != null) {
            chain.add(node);
            // 递归收集子节点
            List<String> childSpanIds = childMap.get(traceId).getOrDefault(spanId, Collections.emptyList());
            for (String childSpanId : childSpanIds) {
                collectChainNodes(traceId, childSpanId, traceTree, childMap, chain);
            }
        }
    }

    // 输出链路结果
    private static void printValidChains(List<List<TraceNode>> validChains) {
        System.out.println("===== 符合条件的IUAP调用链路 =====");
        System.out.printf("共找到 %d 条符合条件的链路%n%n", validChains.size());
        
        for (int i = 0; i < validChains.size(); i++) {
            List<TraceNode> chain = validChains.get(i);
            String traceId = chain.get(0).traceId;
            if(chain.size() ==1){
                continue;
            }
            System.out.printf("链路 %d (traceid: %s):%n", i + 1, traceId);
            System.out.println("--------------------------------------------------------------------------------");
            for (TraceNode node : chain) {
                System.out.println(node);
            }
            System.out.println();
        }
    }

    public static void main(String[] args) {
        // DuckDB连接配置（文件路径或内存数据库）
        
        try (Connection conn = DriverManager.getConnection(DUCKDB_URL)) {
            // 1. 构建调用树
            Map<String, Map<String, TraceNode>> traceTree = buildTraceTree(conn);
            // 2. 构建子节点映射
            Map<String, Map<String, List<String>>> childMap = buildChildMap(traceTree);
            // 3. 收集符合条件的链路
            List<List<TraceNode>> validChains = collectValidChains(traceTree, childMap);
            // 4. 输出结果
            printValidChains(validChains);
            
        } catch (SQLException e) {
            System.err.println("处理DuckDB数据时出错：" + e.getMessage());
            e.printStackTrace();
        }
    }
}