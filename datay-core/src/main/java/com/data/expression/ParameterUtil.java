package com.data.expression;

import com.data.expression.gen.ParameterLexer;
import com.data.expression.gen.ParameterParser;
import com.data.job.FlowFile;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParameterUtil {

    private static final Random random = new Random();
    /** 区分“字段不存在”和“字段值为 null”。 */
    private static final Object MISSING_VALUE = new Object();
    
    public static List<ParameterInfo> parseParameters(String expression) {
        // 创建ANTLR输入流
        CharStream input = CharStreams.fromString(expression);

        // 创建词法分析器
        ParameterLexer lexer = new ParameterLexer(input);

        // 创建令牌流
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        // 创建语法分析器
        ParameterParser parser = new ParameterParser(tokens);

        // 解析输入，获取语法树
        ParseTree tree = parser.parameters();

        // 创建并应用监听器
        ParameterListener listener = new ParameterListener();
        ParseTreeWalker.DEFAULT.walk(listener, tree);

        // 获取并打印解析结果
        return listener.getParameters();
    }
    
    /**
     * 替换文本中的参数，返回替换后的内容
     * @param text 包含参数的文本
     * @return 替换参数后的文本
     */
    public static String replaceParameters(String text) {
        return replaceParameters(text, (Map<String, Object>) null);
    }

    public static String replaceParameters(String text, Map<String, Object> attributes) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        List<ParameterInfo> parameters = parseParameters(text);
        // 获取并打印解析结果
        for (ParameterInfo param : parameters) {
            if(param.getType() == ParameterType.DOLLAR_CURLY_ATTR) {
                if(attributes != null && attributes.containsKey(param.getName())) {
                    String replacement = attributes.get(param.getName()).toString();
                    text = text.replace(param.getFullMatch(), replacement);
                }
            }else if(param.getType() == ParameterType.HASH_CURLY) {
                String replacement = getParameterValue(param.getName(), param.getType());
                text = text.replace(param.getFullMatch(), replacement);
            }
        }
        return text;
    }

    public static String replaceParameters(String text, FlowFile flowFile) {
        return replaceParameters(text, flowFile, null);
    }

    /**
     * 替换文本中的参数，优先使用 variables（例如当前行数据字段），其次回退到 FlowFile 属性。
     *
     * @param text      包含参数的文本
     * @param flowFile  当前 FlowFile，提供属性与内置参数上下文
     * @param variables 优先匹配的变量集合，可为 null
     * @return 替换参数后的文本
     */
    public static String replaceParameters(String text, FlowFile flowFile, Map<String, Object> variables) {
        return replaceParametersStrict(text, flowFile, variables).getText();
    }

    /**
     * 替换文本中的动态参数，并返回未解析的参数。
     *
     * <p>与 {@link #replaceParameters(String, FlowFile, Map)} 的替换规则一致，但不会忽略缺失的
     * 参数：调用方可以根据 {@link ReplacementResult#getUnresolvedParameters()} 决定报错或降级处理。
     * {@code ${字段名}} 支持 {@code __before.amount} 这样的嵌套路径。</p>
     *
     * @param text      原始文本
     * @param flowFile  当前 FlowFile，可为 null
     * @param variables 优先匹配的变量集合，可为 null
     * @return 替换结果与未解析参数
     */
    public static ReplacementResult replaceParametersStrict(
        String text,
        FlowFile flowFile,
        Map<String, Object> variables
    ) {
        if (text == null || text.trim().isEmpty()) {
            return new ReplacementResult(text, Collections.emptyList());
        }

        Map<String, Object> attributes = flowFile == null ? null : flowFile.getAttributeMap();
        List<ParameterInfo> parameters = parseParameters(text);
        Set<String> unresolved = new LinkedHashSet<>();

        for (ParameterInfo param : parameters) {
            Object value = resolveParameterValue(param, flowFile, variables, attributes);
            if (value == MISSING_VALUE) {
                unresolved.add(param.getFullMatch());
                continue;
            }
            text = text.replace(param.getFullMatch(), String.valueOf(value));
        }
        return new ReplacementResult(text, new ArrayList<>(unresolved));
    }

    /**
     * 将 SQL 中的动态参数转换为 JDBC {@code ?} 占位符。
     *
     * <p>返回的 SQL 可以直接交给 {@link java.sql.PreparedStatement}，参数顺序与
     * {@link #resolveParameterValues(List, FlowFile, Map)} 返回的值顺序一致。</p>
     *
     * @param text 包含动态参数的 SQL 模板
     * @return 参数化 SQL 与参数定义
     */
    public static ParameterizedSql prepareParameterizedSql(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ParameterizedSql(text, Collections.emptyList());
        }

        List<ParameterInfo> parameters = parseParameters(text);
        if (parameters.isEmpty()) {
            return new ParameterizedSql(text, parameters);
        }

        StringBuilder sql = new StringBuilder(text.length());
        int cursor = 0;
        for (ParameterInfo param : parameters) {
            String fullMatch = param.getFullMatch();
            int index = text.indexOf(fullMatch, cursor);
            if (index < 0) {
                throw new IllegalArgumentException("无法定位 SQL 动态参数：" + fullMatch);
            }
            sql.append(text, cursor, index).append('?');
            cursor = index + fullMatch.length();
        }
        sql.append(text, cursor, text.length());
        return new ParameterizedSql(sql.toString(), parameters);
    }

    /**
     * 按参数定义解析当前行/FlowFile 的绑定值。
     *
     * @param parameters 参数定义，来自 {@link #prepareParameterizedSql(String)}
     * @param flowFile   当前 FlowFile
     * @param variables  当前行字段
     * @return 绑定值及未解析参数
     */
    public static ParameterValues resolveParameterValues(
        List<ParameterInfo> parameters,
        FlowFile flowFile,
        Map<String, Object> variables
    ) {
        if (parameters == null || parameters.isEmpty()) {
            return new ParameterValues(Collections.emptyList(), Collections.emptyList());
        }

        Map<String, Object> attributes = flowFile == null ? null : flowFile.getAttributeMap();
        List<Object> values = new ArrayList<>(parameters.size());
        Set<String> unresolved = new LinkedHashSet<>();
        for (ParameterInfo param : parameters) {
            Object value = resolveParameterValue(param, flowFile, variables, attributes);
            if (value == MISSING_VALUE) {
                unresolved.add(param.getFullMatch());
                values.add(null);
            } else {
                values.add(value);
            }
        }
        return new ParameterValues(values, new ArrayList<>(unresolved));
    }

    private static Object resolveParameterValue(
        ParameterInfo param,
        FlowFile flowFile,
        Map<String, Object> variables,
        Map<String, Object> attributes
    ) {
        if (param.getType() == ParameterType.HASH_CURLY) {
            String replacement = getParameterValue(param.getName(), param.getType());
            if (("#{" + param.getName() + "}").equals(replacement)) {
                return MISSING_VALUE;
            }
            return replacement;
        }
        Object value = resolveDollarParameter(param, flowFile, variables, attributes);
        return value == null ? MISSING_VALUE : value;
    }

    private static Object resolveDollarParameter(
        ParameterInfo param,
        FlowFile flowFile,
        Map<String, Object> variables,
        Map<String, Object> attributes
    ) {
        String name = param.getName();
        if (param.getType() == ParameterType.DOLLAR_CURLY) {
            if (variables != null) {
                if (variables.containsKey(name)) {
                    return variables.get(name);
                }
                Object nestedValue = resolveNestedValue(variables, name);
                if (nestedValue != MISSING_VALUE) {
                    return nestedValue;
                }
            }
            if (attributes != null && attributes.containsKey(name)) {
                return attributes.get(name);
            }
            if ("FLOW_FILE_DATA".equals(name) && flowFile != null) {
                return flowFile.getData();
            }
            return null;
        }

        if (attributes != null && attributes.containsKey(name)) {
            return attributes.get(name);
        }
        if ("FLOW_FILE_DATA".equals(name) && flowFile != null) {
            return flowFile.getData();
        }
        return null;
    }

    /**
     * 按点号路径读取嵌套变量，例如 {@code __before.amount}。
     */
    private static Object resolveNestedValue(Map<String, Object> variables, String name) {
        if (variables == null || name == null || !name.contains(".")) {
            return MISSING_VALUE;
        }
        Object current = variables;
        for (String part : name.split("\\.")) {
            if (!(current instanceof Map)) {
                return MISSING_VALUE;
            }
            Map<?, ?> map = (Map<?, ?>) current;
            if (!map.containsKey(part)) {
                return MISSING_VALUE;
            }
            current = map.get(part);
        }
        return current;
    }

    /**
     * 动态参数替换结果。
     */
    public static final class ReplacementResult {

        private final String text;
        private final List<String> unresolvedParameters;

        private ReplacementResult(String text, List<String> unresolvedParameters) {
            this.text = text;
            this.unresolvedParameters = unresolvedParameters;
        }

        public String getText() {
            return text;
        }

        public List<String> getUnresolvedParameters() {
            return unresolvedParameters;
        }

        public boolean hasUnresolved() {
            return !unresolvedParameters.isEmpty();
        }
    }

    /**
     * 参数化后的 SQL 与参数定义。
     */
    public static final class ParameterizedSql {

        private final String sql;
        private final List<ParameterInfo> parameters;

        private ParameterizedSql(String sql, List<ParameterInfo> parameters) {
            this.sql = sql;
            this.parameters = parameters;
        }

        public String getSql() {
            return sql;
        }

        public List<ParameterInfo> getParameters() {
            return parameters;
        }

        public boolean hasParameters() {
            return !parameters.isEmpty();
        }
    }

    /**
     * 当前行的 JDBC 绑定值。
     */
    public static final class ParameterValues {

        private final List<Object> values;
        private final List<String> unresolvedParameters;

        private ParameterValues(List<Object> values, List<String> unresolvedParameters) {
            this.values = values;
            this.unresolvedParameters = unresolvedParameters;
        }

        public List<Object> getValues() {
            return values;
        }

        public List<String> getUnresolvedParameters() {
            return unresolvedParameters;
        }

        public boolean hasUnresolved() {
            return !unresolvedParameters.isEmpty();
        }
    }
    
    /**
     * 根据参数名称和类型获取对应的值
     * @param paramName 参数名称
     * @param paramType 参数类型
     * @return 参数值
     */
    private static String getParameterValue(String paramName, ParameterType paramType) {
        if (paramName == null) {
            return "";
        }
        
        // 转换为大写以便统一处理
        String upperParamName = paramName.toUpperCase();

        // 检查是否是时间计算表达式
        String timeExpressionResult = parseTimeExpression(paramName);
        if (!timeExpressionResult.toUpperCase().equals(upperParamName)) {
            return timeExpressionResult;
        }
        
        // 内置参数处理
        return switch (upperParamName) {
            case "RANDOM" -> String.valueOf(random.nextInt(1000));
            case "RANDOM_LONG" -> String.valueOf(random.nextLong());
            case "RANDOM_DOUBLE" -> String.valueOf(random.nextDouble());
            case "TIMESTAMP" -> String.valueOf(System.currentTimeMillis());
            case "UUID" -> UUID.randomUUID().toString();
            default ->
                // 对于未知参数，返回参数名称本身（可以根据需要修改为返回空字符串或其他默认值）
                    "#{" + paramName + "}";
        };
    }

    /**
     * 解析时间计算表达式，如 NOW - 1D, yyyy-MM-dd, NOW - 6M, HH:mm:ss
     * @param expression 时间表达式
     * @return 计算后的时间字符串
     */
    private static String parseTimeExpression(String expression) {
        // 检查是否包含逗号分隔的格式部分
        String[] parts = expression.split(",\\s*", 2);
        String mainExpression = parts[0].trim();
        String format = parts.length > 1 ? parts[1].trim() : null;

        // 匹配 NOW +/- 数字 + 单位 的模式（不包含格式）
        Pattern pattern = Pattern.compile(
                "^NOW\\s*([+-])\\s*(\\d+)\\s*([dMyHms])$"
        );

        Matcher matcher = pattern.matcher(mainExpression);
        if (matcher.matches()) {
            String operator = matcher.group(1);  // + or -
            int amount = Integer.parseInt(matcher.group(2));  // 数量
            String unit = matcher.group(3);  // 单位

            if ("-".equals(operator)) {
                amount = -amount;  // 如果是减号，则取负数
            }

            java.time.LocalDateTime now = java.time.LocalDateTime.now();

            // 根据单位调整时间
            java.time.LocalDateTime result = switch (unit) {
                case "d" -> now.plusDays(amount);      // 天
                case "M" -> now.plusMonths(amount);    // 月
                case "y" -> now.plusYears(amount);     // 年
                case "H" -> now.plusHours(amount);     // 小时
                case "m" -> now.plusMinutes(amount);   // 分钟
                case "s" -> now.plusSeconds(amount);   // 秒
                default -> now;
            };

            // 支持时间计算结果转换为时间戳（毫秒）
            if ("timestamp".equalsIgnoreCase(format)) {
                return String.valueOf(result.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
            }

            // 如果指定了格式，则使用指定格式；否则使用默认格式
            if (format != null && !format.isEmpty()) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
                    return result.format(formatter);
                } catch (Exception e) {
                    // 如果格式无效，使用默认格式
                    return result.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                }
            } else {
                // 默认格式
                return result.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
        }

        // 检查是否是其他预定义参数带格式的情况
        String[] specificParts = expression.split(",\\s*", 2);
        String baseParam = specificParts[0].trim();
        String specificFormat = specificParts.length > 1 ? specificParts[1].trim() : null;

        // 处理基础参数
        String baseResult = switch (baseParam) {
            case "YYYY-MM-DD" -> java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            case "YESTERDAY" -> java.time.LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            case "TOMORROW" -> java.time.LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            case "LAST_MONTH" -> java.time.LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
            case "NEXT_MONTH" -> java.time.LocalDate.now().plusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
            case "LAST_YEAR" -> java.time.LocalDate.now().minusYears(1).format(DateTimeFormatter.ofPattern("yyyy"));
            case "NEXT_YEAR" -> java.time.LocalDate.now().plusYears(1).format(DateTimeFormatter.ofPattern("yyyy"));
            case "THIS_MONTH_FIRST_DAY" -> java.time.LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            case "THIS_MONTH_LAST_DAY" -> java.time.LocalDate.now().withDayOfMonth(java.time.LocalDate.now().lengthOfMonth()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            case "WEEK_AGO" -> java.time.LocalDate.now().minusWeeks(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            case "NEXT_WEEK" -> java.time.LocalDate.now().plusWeeks(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            default -> expression; // 不匹配的返回原值
        };

        // 如果基础参数返回的是有效日期且有格式要求，则重新格式化
        if (!baseResult.equals(expression) && specificFormat != null && !specificFormat.isEmpty()) {
            try {
                if (baseParam.equals("YYYY-MM-DD") || baseParam.equals("YESTERDAY") ||
                        baseParam.equals("TOMORROW") || baseParam.equals("WEEK_AGO") ||
                        baseParam.equals("NEXT_WEEK") || baseParam.contains("DAY")) {
                    java.time.LocalDate date = java.time.LocalDate.parse(baseResult.split("\\s+")[0]);
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(specificFormat);
                    return date.format(formatter);
                } else if (baseParam.equals("LAST_MONTH") || baseParam.equals("NEXT_MONTH")) {
                    java.time.LocalDate date = java.time.LocalDate.parse(baseResult + "-01");
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(specificFormat);
                    return date.format(formatter);
                } else if (baseParam.equals("LAST_YEAR") || baseParam.equals("NEXT_YEAR")) {
                    java.time.LocalDate date = java.time.LocalDate.parse(baseResult + "-01-01");
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(specificFormat);
                    return date.format(formatter);
                }
            } catch (Exception e) {
                // 如果格式转换失败，返回原结果
                return baseResult;
            }
        }

        return baseResult;
    }

}