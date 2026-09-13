package com.data.datafusion;

/**
 * Worker 独立部署入口。
 *
 * <p>DataY Web 复用同一套代码支持调度 master 与执行 worker 的独立/合并部署，
 * 角色由配置项 {@code development.mode} 决定。本类作为 worker 独立部署时的可读入口，
 * 便于部署脚本与容器镜像显式指定主类；行为与 {@link DatafusionApp} 一致，
 * 仅在未显式指定部署模式时默认使用 {@code worker} 角色。</p>
 *
 * <pre>
 * # worker 独立部署（仅执行，可水平扩展）
 * java -cp datay-web.jar com.data.datafusion.WorkerApp --spring.profiles.active=prod
 * </pre>
 */
public final class WorkerApp {

    private static final String MODE_ARG_PREFIX = "--development.mode=";

    private WorkerApp() {}

    public static void main(String[] args) {
        DatafusionApp.main(withDefaultWorkerMode(args));
    }

    /**
     * 未显式指定 {@code development.mode} 时追加 {@code --development.mode=worker}，
     * 保证 WorkerApp 默认以 worker 角色启动。
     */
    private static String[] withDefaultWorkerMode(String[] args) {
        if (args == null) {
            return new String[] { MODE_ARG_PREFIX + "worker" };
        }
        for (String arg : args) {
            if (arg != null && arg.startsWith(MODE_ARG_PREFIX)) {
                return args;
            }
        }
        String[] merged = new String[args.length + 1];
        System.arraycopy(args, 0, merged, 0, args.length);
        merged[args.length] = MODE_ARG_PREFIX + "worker";
        return merged;
    }
}
