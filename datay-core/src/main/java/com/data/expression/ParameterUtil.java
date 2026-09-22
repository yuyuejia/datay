package com.data.expression;

import com.data.expression.gen.ParameterLexer;
import com.data.expression.gen.ParameterParser;
import com.data.job.FlowFile;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParameterUtil {

    private static final Random random = new Random();
    
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
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        Map<String, Object> attributes = flowFile.getAttributeMap();
        List<ParameterInfo> parameters = parseParameters(text);
        // 获取并打印解析结果
        for (ParameterInfo param : parameters) {
            if(param.getType() == ParameterType.DOLLAR_CURLY_ATTR||param.getType() == ParameterType.DOLLAR_CURLY) {
                Object value = null;
                if(variables != null && variables.containsKey(param.getName())) {
                    value = variables.get(param.getName());
                } else if(attributes != null && attributes.containsKey(param.getName())) {
                    value = attributes.get(param.getName());
                }else if(param.getName().equals("FLOW_FILE_DATA")) {
                    value = flowFile.getData();
                }
                if(value != null) {
                    text = text.replace(param.getFullMatch(), String.valueOf(value));
                }
            }else if(param.getType() == ParameterType.HASH_CURLY) {
                String replacement = getParameterValue(param.getName(), param.getType());
                text = text.replace(param.getFullMatch(), replacement);
            }
        }
        return text;
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