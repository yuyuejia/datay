package com.data.status;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地文件状态保存策略（单机部署默认使用）。
 *
 * <p>每个任务编码对应一个 {@code status_<jobCode>.json} 文件。
 */
public class LocalFileStatusStorageStrategy implements StatusStorageStrategy {

    private static final String STATUS_FILE_PREFIX = "status_";
    private static final String STATUS_FILE_SUFFIX = ".json";
    private static final String LEGACY_STATUS_FILE = "etl_status.json";
    private static final Pattern STATUS_FILE_PATTERN = Pattern.compile("^status_(.+)\\.json$");

    private final String basePath;

    public LocalFileStatusStorageStrategy() {
        this(defaultBasePath());
    }

    public LocalFileStatusStorageStrategy(String basePath) {
        if (basePath == null || basePath.trim().isEmpty()) {
            this.basePath = defaultBasePath();
        } else {
            this.basePath = new File(basePath.trim()).getAbsolutePath();
        }
    }

    private static String defaultBasePath() {
        return new File(System.getProperty("user.dir"), "log").getAbsolutePath();
    }

    @Override
    public boolean saveStatus(Map<String, Object> status, String jobCode) {
        Path statusPath = Path.of(getStatusFileName(jobCode));
        Path parent = statusPath.getParent();
        Path tempFile = null;

        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }

            // 先在内存中完成序列化，再写临时文件，避免序列化异常破坏已有状态文件
            String jsonString = JSON.toJSONString(
                status,
                JSONWriter.Feature.PrettyFormat // 美化格式
            );

            // 临时文件必须与目标文件在同一目录，保证后续 move 是同一文件系统内的原子替换
            tempFile = Files.createTempFile(parent, statusPath.getFileName().toString() + ".", ".tmp");
            try (FileChannel channel = FileChannel.open(tempFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(jsonString.getBytes(StandardCharsets.UTF_8));
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                // 确保数据真正落盘后再替换正式文件
                channel.force(true);
            }

            try {
                Files.move(tempFile, statusPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException e) {
                // 极少数文件系统不支持原子 move，或 ATOMIC_MOVE 下未覆盖已有文件，退化为同目录替换
                System.err.println("当前文件系统不支持原子移动，回退为普通替换: " + statusPath);
                Files.move(tempFile, statusPath, StandardCopyOption.REPLACE_EXISTING);
            }
            forceDirectory(parent);
            return true;
        } catch (IOException e) {
            System.err.println("状态保存到本地文件失败: " + e.getMessage());
            return false;
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // 临时文件清理失败不影响已提交的状态
                }
            }
        }
    }

    /**
     * 尽力对目录执行 fsync，保证 rename 后的目录项也已落盘。
     * 部分平台/文件系统不支持对目录 fsync，此时忽略即可。
     */
    private void forceDirectory(Path directory) {
        if (directory == null) {
            return;
        }
        try (FileChannel directoryChannel = FileChannel.open(directory, StandardOpenOption.READ)) {
            directoryChannel.force(true);
        } catch (IOException ignored) {
            // 目录 fsync 不是所有平台都支持，rename 本身已保证文件内容的原子可见性
        }
    }

    @Override
    public Map<String, Object> loadStatus(String jobCode) {
        Path statusPath = Path.of(getStatusFileName(jobCode));

        try {
            String jsonContent = Files.readString(statusPath, StandardCharsets.UTF_8);
            if (jsonContent.isBlank()) {
                throw new StatusStorageException(
                    StatusStorageException.Reason.EMPTY_CONTENT,
                    "本地状态文件为空，可能已损坏: " + statusPath
                );
            }

            Map<String, Object> status;
            try {
                status = JSON.parseObject(jsonContent, Map.class);
            } catch (Exception e) {
                throw new StatusStorageException(
                    StatusStorageException.Reason.INVALID_CONTENT,
                    "本地状态文件内容不是合法 JSON: " + statusPath,
                    e
                );
            }
            if (status == null) {
                throw new StatusStorageException(
                    StatusStorageException.Reason.INVALID_CONTENT,
                    "本地状态文件内容为空，可能已损坏: " + statusPath
                );
            }
            return status;
        } catch (NoSuchFileException e) {
            // 只有文件不存在才表示首次运行
            return null;
        } catch (IOException e) {
            // 文件存在但读取失败时不能返回 null，避免被误判为“没有历史状态”
            throw new StatusStorageException(
                StatusStorageException.Reason.READ_FAILED,
                "从本地文件读取状态失败: " + statusPath,
                e
            );
        }
    }

    @Override
    public boolean deleteStatus(String jobCode) {
        File statusFile = new File(getStatusFileName(jobCode));
        if (!statusFile.exists()) {
            return true;
        }
        boolean deleted = statusFile.delete();
        if (!deleted) {
            System.err.println("删除本地状态文件失败: " + statusFile.getAbsolutePath());
        }
        return deleted;
    }

    @Override
    public List<String> listJobCodes() {
        List<String> jobCodes = new ArrayList<>();
        File dir = new File(basePath);
        if (!dir.isDirectory()) {
            return jobCodes;
        }
        File[] files = dir.listFiles((d, name) -> STATUS_FILE_PATTERN.matcher(name).matches());
        if (files == null) {
            return jobCodes;
        }
        for (File file : files) {
            Matcher matcher = STATUS_FILE_PATTERN.matcher(file.getName());
            if (matcher.matches()) {
                jobCodes.add(matcher.group(1));
            }
        }
        return jobCodes;
    }

    @Override
    public String getStrategyName() {
        return "LocalFile";
    }

    private String getStatusFileName(String jobCode) {
        if (jobCode == null || jobCode.trim().isEmpty()) {
            return new File(basePath, LEGACY_STATUS_FILE).getAbsolutePath(); // 兼容旧版本
        }
        return new File(basePath, STATUS_FILE_PREFIX + jobCode + STATUS_FILE_SUFFIX).getAbsolutePath();
    }
}
