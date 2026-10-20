import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class SlimJar {

    private static final String BOOT_LIB = "BOOT-INF/lib/";

    private static final String[] WEB_DROP_PREFIXES = {
        "bcprov-jdk18on-",
        "hppc-",
        "jackson-datatype-hppc-",
        "aws-java-sdk-s3-",
        "aws-java-sdk-kms-",
        "aws-java-sdk-core-",
        "ion-java-",
        "joda-time-",
        "jmespath-java-",
        "jmatio-",
        "spring-boot-devtools-",
        "spring-boot-docker-compose-",
        "avro-",
        "commons-beanutils-",
        "commons-collections-",
        "commons-logging-",
        "lettuce-core-",
        "spring-boot-loader-tools-",
        "jaxb-runtime-",
        "jaxb-core-",
        "jaxb-api-",
        "jackson-module-jaxb-annotations-",
        "druid-"
    };

    private SlimJar() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalArgumentException("Usage: SlimJar <web|core> <jar> <platform>");
        }
        String mode = args[0];
        Path jar = Paths.get(args[1]);
        String platform = args[2].toLowerCase(Locale.ROOT);
        if (!Files.isRegularFile(jar)) {
            throw new IllegalArgumentException("Jar not found: " + jar);
        }
        if (!Set.of("all", "linux-amd64", "linux-arm64", "macos", "windows").contains(platform)) {
            throw new IllegalArgumentException("Unsupported platform: " + platform);
        }
        boolean stripNatives = !"all".equals(platform);

        Path tmp = jar.resolveSibling(jar.getFileName().toString() + ".slim");
        long before = Files.size(jar);
        try (
            ZipFile in = new ZipFile(jar.toFile());
            ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))
        ) {
            out.setLevel(Deflater.BEST_COMPRESSION);
            Enumeration<? extends ZipEntry> entries = in.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith("/")) {
                    writeStored(out, name, entry.getTime(), new byte[0]);
                    continue;
                }
                if ("web".equals(mode) && isWebDropped(name)) {
                    continue;
                }
                if ("web".equals(mode) && isClasspathIndex(name)) {
                    String filtered = filterIndex(new String(read(in, entry), java.nio.charset.StandardCharsets.UTF_8));
                    writeDeflated(out, name, entry.getTime(), filtered.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    continue;
                }

                boolean nestedJar = "web".equals(mode) && name.startsWith(BOOT_LIB) && name.endsWith(".jar");
                if (nestedJar) {
                    byte[] data = read(in, entry);
                    if (stripNatives && isNativeJar(name)) {
                        data = rewriteNestedJar(data, platform);
                    }
                    writeStored(out, name, entry.getTime(), data);
                } else if ("core".equals(mode) && stripNatives && isNativePath(name) && !keepNativePath(name, platform)) {
                    continue;
                } else {
                    writeDeflated(out, name, entry.getTime(), read(in, entry));
                }
            }
        }
        Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
        long after = Files.size(jar);
        System.out.printf(
            "[slim-jar] %s: %.2f MiB -> %.2f MiB (%.2f MB, %s)%n",
            jar.getFileName(),
            before / 1048576.0,
            after / 1048576.0,
            after / 1000000.0,
            platform
        );
    }

    private static boolean isWebDropped(String name) {
        if (!name.startsWith(BOOT_LIB)) {
            return false;
        }
        String file = name.substring(BOOT_LIB.length());
        for (String prefix : WEB_DROP_PREFIXES) {
            if (file.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isClasspathIndex(String name) {
        return name.equals("BOOT-INF/classpath.idx") || name.equals("BOOT-INF/layers.idx");
    }

    private static String filterIndex(String content) {
        StringBuilder result = new StringBuilder(content.length());
        for (String line : content.split("\n", -1)) {
            if (!isDroppedLibPath(line)) {
                result.append(line).append('\n');
            }
        }
        return result.toString();
    }

    private static boolean isDroppedLibPath(String line) {
        for (String prefix : WEB_DROP_PREFIXES) {
            if (line.contains(BOOT_LIB + prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNativeJar(String name) {
        String file = name.substring(name.lastIndexOf('/') + 1);
        return (
            file.startsWith("duckdb_jdbc-") ||
            file.startsWith("sqlite-jdbc-") ||
            file.startsWith("zstd-jni-") ||
            file.startsWith("snappy-java-") ||
            file.startsWith("lz4-java-")
        );
    }

    private static byte[] rewriteNestedJar(byte[] data, String platform) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(data.length);
        try (
            ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(data));
            ZipOutputStream out = new ZipOutputStream(buffer)
        ) {
            out.setLevel(Deflater.BEST_COMPRESSION);
            Set<String> seen = new HashSet<>();
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith("/") || !seen.add(name)) {
                    continue;
                }
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.startsWith("meta-inf/") && isSignatureOrIndex(lower)) {
                    continue;
                }
                if (isNativePath(name) && !keepNativePath(name, platform)) {
                    continue;
                }
                ZipEntry next = new ZipEntry(name);
                next.setTime(entry.getTime());
                out.putNextEntry(next);
                copy(in, out);
                out.closeEntry();
            }
        }
        return buffer.toByteArray();
    }

    private static boolean isSignatureOrIndex(String lower) {
        return (
            lower.equals("meta-inf/index.list") ||
            lower.endsWith(".sf") ||
            lower.endsWith(".rsa") ||
            lower.endsWith(".dsa") ||
            lower.endsWith(".ec")
        );
    }

    private static boolean isNativePath(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("libduckdb_java.so_")) {
            return true;
        }
        if (
            lower.startsWith("org/sqlite/native/") ||
            lower.startsWith("org/xerial/snappy/native/") ||
            lower.startsWith("net/jpountz/util/")
        ) {
            return true;
        }
        return (
            lower.startsWith("linux/") ||
            lower.startsWith("darwin/") ||
            lower.startsWith("win/") ||
            lower.startsWith("freebsd/") ||
            lower.startsWith("sunos/")
        );
    }

    private static boolean keepNativePath(String name, String platform) {
        String lower = name.toLowerCase(Locale.ROOT);
        boolean mac = platform.equals("macos");
        boolean windows = platform.equals("windows");
        boolean arm64 = platform.equals("linux-arm64");

        if (lower.contains("libduckdb_java.so_")) {
            if (mac) {
                return lower.contains("libduckdb_java.so_osx_universal");
            }
            if (windows) {
                return lower.contains("libduckdb_java.so_windows_amd64");
            }
            return lower.contains(arm64 ? "libduckdb_java.so_linux_arm64" : "libduckdb_java.so_linux_amd64");
        }
        if (lower.startsWith("org/sqlite/native/")) {
            if (mac) {
                return lower.startsWith("org/sqlite/native/mac/");
            }
            if (windows) {
                return lower.startsWith("org/sqlite/native/windows/x86_64/");
            }
            return lower.startsWith(arm64 ? "org/sqlite/native/linux/aarch64/" : "org/sqlite/native/linux/x86_64/");
        }
        if (lower.startsWith("org/xerial/snappy/native/")) {
            if (mac) {
                return lower.startsWith("org/xerial/snappy/native/mac/");
            }
            if (windows) {
                return lower.startsWith("org/xerial/snappy/native/windows/x86_64/");
            }
            return lower.startsWith(
                arm64 ? "org/xerial/snappy/native/linux/aarch64/" : "org/xerial/snappy/native/linux/x86_64/"
            );
        }
        if (lower.startsWith("net/jpountz/util/")) {
            if (mac) {
                return lower.startsWith("net/jpountz/util/darwin/");
            }
            if (windows) {
                return lower.startsWith("net/jpountz/util/win32/amd64/");
            }
            return lower.startsWith(arm64 ? "net/jpountz/util/linux/aarch64/" : "net/jpountz/util/linux/amd64/");
        }
        if (
            lower.startsWith("linux/") ||
            lower.startsWith("darwin/") ||
            lower.startsWith("win/") ||
            lower.startsWith("freebsd/") ||
            lower.startsWith("sunos/")
        ) {
            if (mac) {
                return lower.startsWith("darwin/");
            }
            if (windows) {
                return lower.startsWith("win/amd64/");
            }
            return lower.startsWith(arm64 ? "linux/aarch64/" : "linux/amd64/");
        }
        return true;
    }

    private static byte[] read(ZipFile zip, ZipEntry entry) throws Exception {
        try (InputStream in = zip.getInputStream(entry); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            copy(in, out);
            return out.toByteArray();
        }
    }

    private static void writeStored(ZipOutputStream out, String name, long time, byte[] data) throws Exception {
        CRC32 crc = new CRC32();
        crc.update(data);
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(time);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(data.length);
        entry.setCompressedSize(data.length);
        entry.setCrc(crc.getValue());
        out.putNextEntry(entry);
        out.write(data);
        out.closeEntry();
    }

    private static void writeDeflated(ZipOutputStream out, String name, long time, byte[] data) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(time);
        out.putNextEntry(entry);
        out.write(data);
        out.closeEntry();
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}
