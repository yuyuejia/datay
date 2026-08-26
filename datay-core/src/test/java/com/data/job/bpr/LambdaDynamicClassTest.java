package com.data.job.bpr;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LambdaDynamicClassTest {

    @Test
    public void testLambdaDynamicClassInLoop() {
        int loopCount = 50;

        List<Runnable> loopLambdas = new ArrayList<>();
        for (int i = 0; i < loopCount; i++) {
            final int counter = i;
            Runnable r = () -> System.out.println("lambda-" + counter);
            loopLambdas.add(r);
        }

        Set<Class<?>> loopClasses = loopLambdas.stream()
                .map(Object::getClass)
                .collect(Collectors.toSet());

        System.out.println("=== 循环中 lambda 生成的动态类 ===");
        System.out.println("Lambda 实例数量: " + loopLambdas.size());
        System.out.println("不同动态类数量: " + loopClasses.size());
        loopClasses.forEach(cls -> System.out.println("  类名: " + cls.getName()));

        assert loopClasses.size() == 1
                : "循环中相同位置的 lambda 应只生成 1 个动态类，实际: " + loopClasses.size();
    }

    @Test
    public void testInfiniteLoopSimulation() {
        int loopCount = 1000;

        List<Runnable> lambdas = new ArrayList<>();
        for (int i = 0; i < loopCount; i++) {
            final int counter = i;
            Runnable r = () -> System.out.println("lambda-" + counter);
            lambdas.add(r);
        }

        Set<Class<?>> classes = lambdas.stream()
                .map(Object::getClass)
                .collect(Collectors.toSet());

        System.out.println("=== 大规模循环 lambda 动态类统计 ===");
        System.out.println("循环次数: " + loopCount);
        System.out.println("Lambda 实例数量: " + lambdas.size());
        System.out.println("不同动态类数量: " + classes.size());
        System.out.println("每个动态类对应实例数: " + (double) lambdas.size() / classes.size());
        classes.forEach(cls -> System.out.println("  类名: " + cls.getName()));
    }

    interface Dto {
        String getCode();
    }

    static class DtoImpl implements Dto {
        private final String code;
        DtoImpl(String code) { this.code = code; }
        public String getCode() { return code; }
    }

    @Test
    public void testLoopLambdaWithCapturedVariable() {
        List<Dto> dtoList = Arrays.asList(
                new DtoImpl("A"), new DtoImpl("B"), new DtoImpl("C"),
                new DtoImpl("A"), new DtoImpl("B"), new DtoImpl("C")
        );
        List<String> keyList = Arrays.asList("A", "B", "C");

        List<Runnable> capturedLambdas = new ArrayList<>();
        for (String filterKey : keyList) {
            Runnable r = () -> {
                long count = dtoList.stream()
                        .filter(dto -> dto.getCode().equals(filterKey))
                        .count();
                System.out.println("filterKey=" + filterKey + ", count=" + count);
            };
            capturedLambdas.add(r);
        }

        Set<Class<?>> lambdaClasses = capturedLambdas.stream()
                .map(Object::getClass)
                .collect(Collectors.toSet());

        System.out.println("=== 循环中捕获变量的 lambda（你提供的模式） ===");
        System.out.println("循环次数: " + keyList.size());
        System.out.println("Lambda 实例数量: " + capturedLambdas.size());
        System.out.println("不同动态类数量: " + lambdaClasses.size());
        lambdaClasses.forEach(cls -> System.out.println("  类名: " + cls.getName()));

    }

    @Test
    public void testLoopAnonymousClassAlsoOneClass() {
        List<Dto> dtoList = Arrays.asList(
                new DtoImpl("A"), new DtoImpl("B"), new DtoImpl("C")
        );
        List<String> keyList = Arrays.asList("A", "B", "C");

        List<Runnable> anonymousInstances = new ArrayList<>();
        for (String filterKey : keyList) {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    long count = dtoList.stream()
                            .filter(dto -> dto.getCode().equals(filterKey))
                            .count();
                    System.out.println("filterKey=" + filterKey + ", count=" + count);
                }
            };
            anonymousInstances.add(r);
        }

        Set<Class<?>> anonClasses = anonymousInstances.stream()
                .map(Object::getClass)
                .collect(Collectors.toSet());

        System.out.println("=== 循环中匿名内部类（实测也只有 1 个类） ===");
        System.out.println("循环次数: " + keyList.size());
        System.out.println("匿名类实例数量: " + anonymousInstances.size());
        System.out.println("不同动态类数量: " + anonClasses.size());
        anonClasses.forEach(cls -> System.out.println("  类名: " + cls.getName()));

        assert anonClasses.size() == 1
                : "匿名内部类在循环中也只生成 1 个类（编译时确定，运行时 new 只是创建实例），实际: " + anonClasses.size();

        System.out.println();
        System.out.println("JVM 原理:");
        System.out.println("  - 匿名内部类在编译期就生成独立的 .class 文件（如 Test$1.class）");
        System.out.println("  - 类名中的 $N 编号由编译器按源码中匿名类出现顺序分配");
        System.out.println("  - 循环中的 new 只是创建实例，不会生成新类");
    }

    @Test
    public void testMultipleLambdaSourcePositions() {
        int lambdaCount = 5;
        List<Runnable> lambdas = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger(0);

        lambdas.add(() -> System.out.println("pos-" + counter.incrementAndGet()));
        lambdas.add(() -> System.out.println("pos-" + counter.incrementAndGet()));
        lambdas.add(() -> System.out.println("pos-" + counter.incrementAndGet()));
        lambdas.add(() -> System.out.println("pos-" + counter.incrementAndGet()));
        lambdas.add(() -> System.out.println("pos-" + counter.incrementAndGet()));

        Set<Class<?>> classes = lambdas.stream()
                .map(Object::getClass)
                .collect(Collectors.toSet());

        System.out.println("=== 不同源码位置的 lambda ===");
        System.out.println("Lambda 数量: " + lambdas.size());
        System.out.println("不同动态类数量: " + classes.size());
        classes.forEach(cls -> System.out.println("  类名: " + cls.getName()));

        assert classes.size() == lambdaCount
                : "每个源码位置的 lambda 各生成 1 个动态类，期望 " + lambdaCount + "，实际: " + classes.size();

        System.out.println();
        System.out.println("关键: 源码中有 5 个不同位置的 lambda → 5 个 CallSite → 5 个动态类");
    }

    interface RecordItem {
        BigDecimal getBigDecimal(String key);
    }

    static class RecordItemImpl implements RecordItem {
        private final Map<String, BigDecimal> data = new HashMap<>();

        RecordItemImpl(int lineNo) {
            data.put("lineNo", BigDecimal.valueOf(lineNo));
        }

        public BigDecimal getBigDecimal(String key) {
            return data.get(key);
        }
    }

    static class ConvertUtils {
        static BigDecimal getBigDecimal(BigDecimal bd) {
            return bd != null ? bd : BigDecimal.ZERO;
        }
    }

    @Test
    public void testSingleStreamLambdaClass() {
        List<RecordItem> items = Arrays.asList(
                new RecordItemImpl(1), new RecordItemImpl(2), new RecordItemImpl(3)
        );

        Set<String> result = items.stream()
                .filter(x -> Objects.nonNull(x.getBigDecimal("lineNo")))
                .map(x -> ConvertUtils.getBigDecimal(x.getBigDecimal("lineNo"))
                        .stripTrailingZeros().toPlainString())
                .collect(Collectors.toSet());

        System.out.println("=== 多层嵌套流 lambda 动态类统计（你提供的代码模式） ===");
        System.out.println("流管道: items.stream().filter(...).map(...).collect(toSet())");
        System.out.println("执行结果: " + result);
        System.out.println();

        Predicate<RecordItem> filterLambda = x -> Objects.nonNull(x.getBigDecimal("lineNo"));
        Function<RecordItem, String> mapLambda = x -> ConvertUtils.getBigDecimal(x.getBigDecimal("lineNo"))
                .stripTrailingZeros().toPlainString();

        List<Object> allLambdas = Arrays.asList(filterLambda, mapLambda);
        Set<Class<?>> lambdaClasses = allLambdas.stream()
                .map(Object::getClass)
                .collect(Collectors.toSet());

        System.out.println("[filter] lambda 类: " + filterLambda.getClass().getName());
        System.out.println("[map]    lambda 类: " + mapLambda.getClass().getName());
        System.out.println();
        System.out.println("用户代码中 lambda 总数: 2 (filter + map)");
        System.out.println("不同动态类总数: " + lambdaClasses.size());

        assert lambdaClasses.size() == 2
                : "filter + map 共 2 个不同位置的 lambda，应生成 2 个动态类，实际: " + lambdaClasses.size();

        System.out.println();
        System.out.println("JVM 原理:");
        System.out.println("  - filter 的 lambda → 源码位置 1 → CallSite 1 → 动态类 1");
        System.out.println("  - map 的 lambda    → 源码位置 2 → CallSite 2 → 动态类 2");
        System.out.println("  - collect(toSet()) 使用 JDK 内部方法引用，不产生用户代码 lambda 类");
        System.out.println("  - 结论: 每层 stream 中间操作 (filter/map 等) 各产生 1 个动态类");
    }

    @Test
    public void testStreamLambdaClassCountByDepth() {
        List<RecordItem> items = Arrays.asList(
                new RecordItemImpl(1), new RecordItemImpl(2), new RecordItemImpl(3)
        );

        // 1 层: 只有 filter
        Predicate<RecordItem> p1 = x -> Objects.nonNull(x.getBigDecimal("lineNo"));
        Set<Class<?>> class1 = Set.of(p1).stream().map(Object::getClass).collect(Collectors.toSet());

        // 2 层: filter + map
        Function<RecordItem, String> f2 = x -> ConvertUtils.getBigDecimal(x.getBigDecimal("lineNo"))
                .stripTrailingZeros().toPlainString();
        Set<Class<?>> class2 = Set.of(p1, f2).stream().map(Object::getClass).collect(Collectors.toSet());

        // 3 层: filter + map + map
        Function<String, Integer> f3 = s -> Integer.parseInt(s);
        Set<Class<?>> class3 = Set.of(p1, f2, f3).stream().map(Object::getClass).collect(Collectors.toSet());

        // 4 层: filter + map + map + filter
        Predicate<Integer> p4 = n -> n > 0;
        Set<Class<?>> class4 = Set.of(p1, f2, f3, p4).stream().map(Object::getClass).collect(Collectors.toSet());

        System.out.println("=== 嵌套深度 vs 动态类数量 ===");
        System.out.println("流管道深度          | lambda 数 | 动态类数");
        System.out.println("--------------------|-----------|----------");
        System.out.printf("%-19s | %-9d | %d%n", "filter", 1, class1.size());
        System.out.printf("%-19s | %-9d | %d%n", "filter + map", 2, class2.size());
        System.out.printf("%-19s | %-9d | %d%n", "filter + map + map", 3, class3.size());
        System.out.printf("%-19s | %-9d | %d%n", "filter + map + map + filter", 4, class4.size());

        assert class1.size() == 1 : "1 层 → 1 个动态类";
        assert class2.size() == 2 : "2 层 → 2 个动态类";
        assert class3.size() == 3 : "3 层 → 3 个动态类";
        assert class4.size() == 4 : "4 层 → 4 个动态类";

        System.out.println();
        System.out.println("结论: 每层 stream 中间操作 (filter/map/flatMap 等) 各产生 1 个动态类。");
        System.out.println("      类数 = 用户代码中 lambda 的数量（按源码位置计算）。");
    }

    @Test
    public void testStreamWithCollectAndMethodRefs() {
        List<RecordItem> items = Arrays.asList(
                new RecordItemImpl(1), new RecordItemImpl(2), new RecordItemImpl(3)
        );

        Set<String> result = items.stream()
                .filter(x -> Objects.nonNull(x.getBigDecimal("lineNo")))
                .map(x -> ConvertUtils.getBigDecimal(x.getBigDecimal("lineNo"))
                        .stripTrailingZeros().toPlainString())
                .collect(Collectors.toSet());

        System.out.println("=== collect(Collectors.toSet()) 内部动态类分析 ===");
        System.out.println("流执行结果: " + result);
        System.out.println();

        // Collectors.toSet() 内部使用的关键方法引用：
        // 1. ConcurrentHashMap::new  → Supplier (实例化 HashMap)
        // 2. ConcurrentHashMap::newKeySet  → Finisher (转换为 Set)
        // 3. HashMap::add → Accumulator (元素收集)

        // 用户代码 lambda: 2 个 (filter + map)
        Predicate<RecordItem> filterLambda = x -> Objects.nonNull(x.getBigDecimal("lineNo"));
        Function<RecordItem, String> mapLambda = x -> ConvertUtils.getBigDecimal(x.getBigDecimal("lineNo"))
                .stripTrailingZeros().toPlainString();

        // Collectors.toSet() 内部方法引用（JDK 代码中定义，不由用户控制）
        java.util.function.Supplier<Set<String>> supplier = HashSet::new;
        java.util.function.BiConsumer<Set<String>, String> accumulator = Set::add;

        List<Object> userLambdas = Arrays.asList(filterLambda, mapLambda);
        List<Object> allLambdas = Arrays.asList(filterLambda, mapLambda, supplier, accumulator);

        Set<Class<?>> userClasses = userLambdas.stream()
                .map(Object::getClass).collect(Collectors.toSet());
        Set<Class<?>> allClasses = allLambdas.stream()
                .map(Object::getClass).collect(Collectors.toSet());

        System.out.println("用户代码 lambda 数量: " + userLambdas.size());
        System.out.println("用户代码动态类数量: " + userClasses.size());
        userClasses.forEach(cls -> System.out.println("  [用户] " + cls.getName()));

        System.out.println();
        System.out.println("包含 JDK 内部方法引用:");
        System.out.println("  总计 lambda/方法引用数量: " + allLambdas.size());
        System.out.println("  总计动态类数量: " + allClasses.size());
        allClasses.forEach(cls -> System.out.println("  [全部] " + cls.getName()));

        System.out.println();
        System.out.println("说明: Collectors.toSet() 在 JDK 内部实现中使用了方法引用");
        System.out.println("      (ConcurrentHashMap::new, HashMap::add 等)");
        System.out.println("      这些方法引用同样通过 invokedynamic 生成动态类");
        System.out.println("      但它们属于 JDK 代码的 CallSite，不是用户代码产生的");
    }

    @Test
    public void testNestedStreamInLoopClassCount() {
        // 场景: 循环中执行嵌套 stream（用户提供的代码模式）
        int loopCount = 10;
        List<RecordItem> items = Arrays.asList(
                new RecordItemImpl(1), new RecordItemImpl(2), new RecordItemImpl(3)
        );
        List<String> keyList = Arrays.asList("A", "B", "C");

        List<Object> allCreatedLambdas = new ArrayList<>();

        for (int loopIdx = 0; loopIdx < loopCount; loopIdx++) {
            String dummy = keyList.get(loopIdx % keyList.size());

            Predicate<RecordItem> filterLambda = x -> Objects.nonNull(x.getBigDecimal("lineNo"));
            Function<RecordItem, String> mapLambda = x -> ConvertUtils.getBigDecimal(x.getBigDecimal("lineNo"))
                    .stripTrailingZeros().toPlainString();

            allCreatedLambdas.add(filterLambda);
            allCreatedLambdas.add(mapLambda);

            // 同时执行实际的 stream
            Set<String> result = items.stream()
                    .filter(x -> Objects.nonNull(x.getBigDecimal("lineNo")))
                    .map(x -> ConvertUtils.getBigDecimal(x.getBigDecimal("lineNo"))
                            .stripTrailingZeros().toPlainString())
                    .collect(Collectors.toSet());
        }

        Set<Class<?>> classes = allCreatedLambdas.stream()
                .map(Object::getClass).collect(Collectors.toSet());

        System.out.println("=== 循环中嵌套 stream 的动态类统计 ===");
        System.out.println("循环次数: " + loopCount);
        System.out.println("每次循环创建的 lambda 数: 2 (filter + map)");
        System.out.println("Lambda 实例总数: " + allCreatedLambdas.size());
        System.out.println("不同动态类总数: " + classes.size());
        classes.forEach(cls -> System.out.println("  类名: " + cls.getName()));

        System.out.println();
        System.out.println("按源码位置分类:");
        Set<Class<?>> filterClasses = allCreatedLambdas.stream()
                .filter(l -> l.getClass().getSimpleName().contains("Lambda"))
                .map(Object::getClass).collect(Collectors.toSet());
        System.out.println("  filter 位置产生的类数: " +
                allCreatedLambdas.stream()
                        .map(Object::getClass).collect(Collectors.toSet()).size());

        System.out.println();
        System.out.println("核心结论: 循环 10 次，每次 2 个 lambda → 共 20 个实例，但只有 2 个动态类");
        System.out.println("          原因: filter 和 map 各有 1 个 CallSite，循环只是重复创建实例");
    }

}