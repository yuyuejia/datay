package com.data.job;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * VOC（用户之声）流水线演示：造一批电商用户反馈文本，走一遍
 * 「DuckDB 输入 → 大模型组件（LlmComponent）→ DuckDB 写入」的数据管道。
 *
 * <p>仅用于生成公众号文章的演示数据与真实运行日志，不参与日常回归。
 */
public class VocLlmDemoTest {

    /** 源库（业务库）：电商用户反馈原文，放在临时目录，不污染仓库。 */
    private static final String ODS_DB =
        System.getProperty("java.io.tmpdir") + "/datay_voc_demo/voc_demo_ods.duckdb";
    /** 数仓库文件名：DuckDBWrite 的 dbFile 相对运行目录下的 log/ 目录。 */
    private static final String DW_DB = "voc_demo_dw.duckdb";

    private static final Object[][] SAMPLES = {
        { "P1001", "无线蓝牙耳机", "评价", "拆开快递盒，包装已经瘪了一块，里面的耳机盒有压痕，耳机本身没坏但看着不舒服，希望加强包装。" },
        { "P1001", "无线蓝牙耳机", "客服工单", "用户来电说左耳没有声音，重启、重置都试过，还是没用，已引导申请售后换新。" },
        { "P1002", "空气炸锅", "评价", "收到货插上电完全没反应，指示灯也不亮，怀疑是坏的，等了两周真的很失望。" },
        { "P1002", "空气炸锅", "评价", "用了三天，炸篮涂层开始一块块掉，洗的时候掉下来的黑色碎屑粘在食物上，太吓人了。" },
        { "P1003", "儿童学习桌", "售后备注", "客户反馈桌面升降杆卡死，怎么摇都不动，孩子开学急着用，要求尽快上门处理。" },
        { "P1004", "女士羽绒服", "评价", "尺码严重偏小，平时穿 M 码，这件 M 码拉链根本拉不上，只能退了再买大一码。" },
        { "P1004", "女士羽绒服", "评价", "跟图片完全不是一个颜色，图上是奶白色，实物发黄，感觉像放了两年的库存。" },
        { "P1005", "扫地机器人", "客服会话", "客户：你们这个机器人扫到地毯上就停住了，一直提示请清理滚刷。客服：建议取下滚刷检查。客户：检查过了没有异物，还是提示。" },
        { "P1005", "扫地机器人", "直播弹幕", "刚下单的扫拖一体机，问下边刷能单独买吗，另外建图能不能一张图存两层楼？" },
        { "P1006", "保温杯", "评价", "杯子挺好，就是物流太慢了，下单一周才到，客服还一直让我等。" },
        { "P1007", "儿童安全座椅", "投诉", "安装说明书太简单，卡扣方向看不懂，客服电话打了三次都没人接，最后还是自己研究了两小时。" },
        { "P1008", "智能手表", "客服会话", "客户：手表连不上手机 App，蓝牙搜不到。客服：请重启手表。客户：试过了。客服：请卸载重装 App。客户：也试过了，还是不行。" }
    };

    private static final String[] NOISE = {
        "还行吧，暂时没发现什么问题。",
        "习惯好评，先给五星，后续有问题再追评。",
        "东西收到了，包装完好，物流很快。",
        "第一次在这家买，看着不错，用一段时间再说。",
        "客服态度很好，问的问题都耐心回答了。",
        "活动价买的，很划算，比线下便宜不少。"
    };

    @Test
    @Timeout(900)
    public void buildVocPipeline() throws Exception {
        createSourceData();
        String job = "{\n"
            + "  \"units\": [\n"
            + "    {\n"
            + "      \".id\": \"voc_input\",\n"
            + "      \".name\": \"StreamJdbcInput\",\n"
            + "      \"datasource\": { \"url\": \"jdbc:duckdb:" + ODS_DB + "\", \"driver\": \"org.duckdb.DuckDBDriver\", \"dbschema\": \"main\" },\n"
            + "      \"schema\": \"main\",\n"
            + "      \"table\": \"ods_voc_feedback\"\n"
            + "    },\n"
            + "    {\n"
            + "      \".id\": \"voc_llm\",\n"
            + "      \".name\": \"LlmComponent\",\n"
            + "      \"baseUrl\": \"https://api.deepseek.com/v1\",\n"
            + "      \"apiKey\": \"${VOC_LLM_API_KEY}\",\n"
            + "      \"model\": \"deepseek-chat\",\n"
            + "      \"temperature\": 0.1,\n"
            + "      \"timeoutSeconds\": 120,\n"
            + "      \"retryCount\": 2,\n"
            + "      \"systemPrompt\": \"你是电商平台的用户之声（VOC）分析助手。请阅读一条用户反馈文本，输出一个 JSON 对象，不要输出任何解释。字段定义：category（问题大类，取值：包装物流/商品质量/功能故障/描述不符/客服服务/其他）、sub_tag（细分标签，如 包装破损、涂层脱落、无法开机、尺码偏小、响应不及时 等，6 字以内）、sentiment（情感：负面/中性/正面）、negative_score（负面程度 0-100 的整数，0 表示无负面）、summary（不超过 25 字的问题摘要）。\",\n"
            + "      \"userPrompt\": \"渠道：${channel}；商品：${product_name}；用户反馈原文：${feedback_text}\"\n"
            + "    },\n"
            + "    {\n"
            + "      \".id\": \"voc_output\",\n"
            + "      \".name\": \"DuckDBWrite\",\n"
            + "      \"table\": \"dwd_voc_tagged\",\n"
            + "      \"model\": \"overwrite\",\n"
            + "      \"dbFile\": \"" + DW_DB + "\"\n"
            + "    }\n"
            + "  ],\n"
            + "  \"connections\": [\n"
            + "    { \"sourceId\": \"voc_input\", \"targetId\": \"voc_llm\", \"sourcePort\": 0 },\n"
            + "    { \"sourceId\": \"voc_llm\", \"targetId\": \"voc_output\", \"sourcePort\": 0 }\n"
            + "  ],\n"
            + "  \"version\": \"1.0.0\"\n"
            + "}";
        // 演示运行时把 ${VOC_LLM_API_KEY} 换成真实 Key 即可（这里从环境变量读取，避免把密钥写进代码）
        String apiKey = System.getenv("VOC_LLM_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getProperty("voc.llm.apiKey", "");
        }
        // 未提供 Key 时跳过（真实调用会产生 token 费用，不适合放进日常回归）
        Assumptions.assumeTrue(!apiKey.isBlank(), "未提供 VOC_LLM_API_KEY，跳过大模型调用演示");
        job = job.replace("${VOC_LLM_API_KEY}", apiKey);

        new ETLFlowTask().runJob(job);
    }

    /** 造源数据：24 条真实语义反馈 + 若干无信息噪声，模拟线上评价/工单/弹幕混杂的真实分布。 */
    private void createSourceData() throws Exception {
        File dir = new File(ODS_DB).getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        Class.forName("org.duckdb.DuckDBDriver");
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + ODS_DB);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS ods_voc_feedback");
            stmt.execute("CREATE TABLE ods_voc_feedback ("
                + "feedback_id VARCHAR, product_id VARCHAR, product_name VARCHAR, channel VARCHAR,"
                + "feedback_text VARCHAR, create_time VARCHAR)");

            List<String[]> rows = new ArrayList<>();
            Random random = new Random(20241008L);
            for (int i = 0; i < SAMPLES.length; i++) {
                Object[] s = SAMPLES[i];
                for (int repeat = 0; repeat < 2; repeat++) {
                    rows.add(new String[] {
                        "F" + (10000 + i * 2 + repeat), (String) s[0], (String) s[1], (String) s[2], (String) s[3],
                        "2024-10-0" + (1 + i % 8) + " " + String.format("%02d:%02d:00", 9 + i % 10, random.nextInt(60))
                    });
                }
            }
            for (int i = 0; i < NOISE.length; i++) {
                rows.add(new String[] {
                    "N" + (20000 + i), "P10" + (10 + i), "通用商品", i % 2 == 0 ? "评价" : "直播弹幕", NOISE[i],
                    "2024-10-0" + (1 + i % 8) + " 20:0" + i + ":00"
                });
            }

            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO ods_voc_feedback VALUES (?,?,?,?,?,?)")) {
                for (String[] row : rows) {
                    for (int c = 0; c < row.length; c++) {
                        ps.setString(c + 1, row[c]);
                    }
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            try (Statement check = conn.createStatement();
                 ResultSet rs = check.executeQuery("SELECT count(*) FROM ods_voc_feedback")) {
                rs.next();
                System.out.println("源表 ods_voc_feedback 生成完成，共 " + rs.getInt(1) + " 条");
            }
        }
    }
}
