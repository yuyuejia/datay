package com.data.job.bpr;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MultiLambdaClassInLoopTest {

    interface Item {
        BigDecimal getValue();
    }

    static class ItemImpl implements Item {
        private final BigDecimal value;
        ItemImpl(int v) { this.value = BigDecimal.valueOf(v); }
        public BigDecimal getValue() { return value; }
    }

    @Test
    public void testSamePositionSameClass() {
        int loopCount = 10;
        List<Item> items = Arrays.asList(new ItemImpl(1), new ItemImpl(2));

        List<Object> lambdas = new ArrayList<>();
        for (int i = 0; i < loopCount; i++) {
            Predicate<Item> p = x -> x.getValue() != null;
            lambdas.add(p);
        }

        Set<Class<?>> classes = lambdas.stream()
                .map(Object::getClass).collect(Collectors.toSet());

        System.out.println("=== 场景1: 循环中同一位置 lambda ===");
        System.out.println("循环次数: " + loopCount);
        System.out.println("Lambda 实例数: " + lambdas.size());
        System.out.println("动态类数: " + classes.size());
        classes.forEach(cls -> System.out.println("  " + cls.getName()));
        System.out.println("结论: 同一源码位置 → 同一 CallSite → 只有 1 个类\n");

        assert classes.size() == 1 : "同一位置应只有 1 个类";
    }

    @Test
    public void testIfBranchCreatesMultipleClasses() {
        int loopCount = 10;
        List<Item> items = Arrays.asList(new ItemImpl(1), new ItemImpl(2));
        List<Object> lambdas = new ArrayList<>();

        for (int i = 0; i < loopCount; i++) {
            if (i % 2 == 0) {
                Predicate<Item> p = x -> x.getValue().intValue() > 0;
                lambdas.add(p);
            } else {
                Predicate<Item> p = x -> x.getValue().intValue() < 100;
                lambdas.add(p);
            }
        }

        Set<Class<?>> classes = lambdas.stream()
                .map(Object::getClass).collect(Collectors.toSet());

        System.out.println("=== 场景2: 循环中 if/else 分支 lambda（不同位置） ===");
        System.out.println("循环次数: " + loopCount);
        System.out.println("Lambda 实例数: " + lambdas.size());
        System.out.println("动态类数: " + classes.size());
        classes.forEach(cls -> System.out.println("  " + cls.getName()));
        System.out.println("结论: if 分支 lambda 位置 A + else 分支 lambda 位置 B → 2 个 CallSite → 2 个类\n");

        assert classes.size() == 2 : "if/else 两个分支应生成 2 个类";
    }

    @Test
    public void testSwitchBranchCreatesMultipleClasses() {
        int loopCount = 10;
        List<Object> lambdas = new ArrayList<>();

        for (int i = 0; i < loopCount; i++) {
            int type = i % 3;
            Predicate<Item> p = switch (type) {
                case 0 -> (x -> x.getValue().compareTo(BigDecimal.ZERO) > 0);
                case 1 -> (x -> x.getValue().compareTo(BigDecimal.TEN) < 0);
                default -> (x -> x.getValue() != null);
            };
            lambdas.add(p);
        }

        Set<Class<?>> classes = lambdas.stream()
                .map(Object::getClass).collect(Collectors.toSet());

        System.out.println("=== 场景3: 循环中 switch 表达式 lambda（不同 case 位置） ===");
        System.out.println("循环次数: " + loopCount);
        System.out.println("Lambda 实例数: " + lambdas.size());
        System.out.println("动态类数: " + classes.size());
        classes.forEach(cls -> System.out.println("  " + cls.getName()));
        System.out.println("结论: switch 中 3 个 case 各有独立 lambda 位置 → 3 个 CallSite → 3 个类\n");

        assert classes.size() == 3 : "switch 三个 case 应生成 3 个类";
    }

    @Test
    public void testNestedIfInLoopCreatesManyClasses() {
        int loopCount = 10;
        List<Object> lambdas = new ArrayList<>();

        for (int i = 0; i < loopCount; i++) {
            Predicate<Item> p;
            if (i < 3) {
                p = x -> x.getValue().intValue() == 1;
            } else if (i < 6) {
                p = x -> x.getValue().intValue() == 2;
            } else if (i < 8) {
                p = x -> x.getValue().intValue() == 3;
            } else {
                p = x -> x.getValue().intValue() == 4;
            }
            lambdas.add(p);
        }

        Set<Class<?>> classes = lambdas.stream()
                .map(Object::getClass).collect(Collectors.toSet());

        System.out.println("=== 场景4: 循环中嵌套 if-else 链（4 个分支） ===");
        System.out.println("循环次数: " + loopCount);
        System.out.println("Lambda 实例数: " + lambdas.size());
        System.out.println("动态类数: " + classes.size());
        classes.forEach(cls -> System.out.println("  " + cls.getName()));
        System.out.println("结论: 4 个 if-else 分支 = 4 个 lambda 源码位置 = 4 个动态类\n");

        assert classes.size() == 4 : "4 个分支应生成 4 个类";
    }

    @Test
    public void testPolymorphicLambdaFactory() {
        int loopCount = 10;
        List<Object> lambdas = new ArrayList<>();

        for (int i = 0; i < loopCount; i++) {
            final int idx = i;
            Runnable r = createLambda(idx);
            lambdas.add(r);
        }

        Set<Class<?>> classes = lambdas.stream()
                .map(Object::getClass).collect(Collectors.toSet());

        System.out.println("=== 场景5: 循环中通过工厂方法创建 lambda ===");
        System.out.println("循环次数: " + loopCount);
        System.out.println("Lambda 实例数: " + lambdas.size());
        System.out.println("动态类数: " + classes.size());
        classes.forEach(cls -> System.out.println("  " + cls.getName()));
        System.out.println("结论: 每次调用 createLambda()，内部的 lambda 处于同一位置 → 只有 1 个类\n");

        assert classes.size() == 1 : "工厂方法内部同一位置，应只有 1 个类";
    }

    private Runnable createLambda(int idx) {
        return () -> System.out.println("lambda-" + idx);
    }

    @Test
    public void testMultipleLambdaClassesInLoopWithDifferentTargets() {
        int loopCount = 5;
        List<Object> lambdas = new ArrayList<>();
        List<String> classNames = new ArrayList<>();

        for (int i = 0; i < loopCount; i++) {
            final int idx = i;
            switch (idx % 3) {
                case 0:
                    Runnable r0 = () -> System.out.println("case0-" + idx);
                    lambdas.add(r0);
                    classNames.add(r0.getClass().getName());
                    break;
                case 1:
                    Runnable r1 = () -> System.out.println("case1-" + idx);
                    lambdas.add(r1);
                    classNames.add(r1.getClass().getName());
                    break;
                default:
                    Runnable r2 = () -> System.out.println("case2-" + idx);
                    lambdas.add(r2);
                    classNames.add(r2.getClass().getName());
                    break;
            }
        }

        Set<Class<?>> classes = lambdas.stream()
                .map(Object::getClass).collect(Collectors.toSet());

        System.out.println("=== 场景6: switch 中 Runnable lambda（捕获不同值）===");
        System.out.println("循环次数: " + loopCount);
        System.out.println("Lambda 实例数: " + lambdas.size());
        System.out.println("动态类数: " + classes.size());
        classes.forEach(cls -> System.out.println("  " + cls.getName()));
        System.out.println("每次调用打印的类名: " + classNames);
        System.out.println("结论: switch 每个分支 = 独立源码位置 = 独立 CallSite = 独立动态类\n");
    }

    @Test
    public void testActualMultiClassInLoopSummary() {
        System.out.println("========================================");
        System.out.println("循环中生成多个 lambda 动态类的模式总结");
        System.out.println("========================================");
        System.out.println();
        System.out.println("模式一: if/else 分支（每个分支 = 一个源码位置）");
        System.out.println("  for (...) {");
        System.out.println("    if (cond) { list.stream().filter(x -> ...) }   // 位置 A → 类 A");
        System.out.println("    else     { list.stream().filter(x -> ...) }   // 位置 B → 类 B");
        System.out.println("  }");
        System.out.println("  类数 = if/else 分支数量");
        System.out.println();
        System.out.println("模式二: switch 表达式（每个 case = 一个源码位置）");
        System.out.println("  for (...) {");
        System.out.println("    Predicate<T> p = switch(x) {");
        System.out.println("      case 0 -> (t -> ...)   // 位置 A → 类 A");
        System.out.println("      case 1 -> (t -> ...)   // 位置 B → 类 B");
        System.out.println("      default -> (t -> ...)  // 位置 C → 类 C");
        System.out.println("    };");
        System.out.println("  }");
        System.out.println("  类数 = switch case 数量");
        System.out.println();
        System.out.println("模式三: 嵌套 if-else 链");
        System.out.println("  类数 = 分支数量（每个分支的 lambda 是独立源码位置）");
        System.out.println();
        System.out.println("为什么会这样?");
        System.out.println("  JVM 的 invokedynamic 指令通过 CallSite 缓存 lambda 类。");
        System.out.println("  【每个源码位置】对应【一个 CallSite】。");
        System.out.println("  同一位置循环 N 次 → 同一 CallSite → 1 个类，N 个实例。");
        System.out.println("  不同位置 (if/switch 分支) → 不同 CallSite → N 个类。");
    }
}