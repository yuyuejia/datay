package com.data.datafusion.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * 生成 RFC 9562 标准的 UUID v7（按时间有序）。
 *
 * <p>v7 的 128 位布局为：48 位毫秒时间戳 + 4 位版本号(7) + 12 位 rand_a + 2 位变体(10) + 62 位 rand_b。
 * 时间戳位于最高位，因此生成的 UUID 字符串天然按生成时间递增，适合作为数据库主键以减少索引分裂，
 * 并保证跨系统迁移时的唯一性。</p>
 *
 * <p>本实现将 12 位 rand_a 作为同一毫秒内的单调计数器（RFC 9562 的计数器单调性方法），
 * 因此同一毫秒内生成的 UUID 也严格递增；当同一毫秒内计数超过 4096 个或系统时钟回拨时，
 * 内部时间戳会前进以保证单调，不会倒退。rand_b 使用 {@link SecureRandom} 生成以保证唯一性。</p>
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 时间戳位数（毫秒）。 */
    private static final long TIMESTAMP_MASK = 0xFFFFFFFFFFFFL;

    /** rand_a 位数，作为同毫秒内的单调计数器。 */
    private static final long RAND_A_MASK = 0xFFFL;

    /** rand_b 位数。 */
    private static final long RAND_B_MASK = 0x3FFFFFFFFFFFFFFFL;

    /** 版本号 7，位于 msb 的第 12~15 位。 */
    private static final long VERSION_BITS = 0x7000L;

    /** 变体 10，位于 lsb 的第 62~63 位。 */
    private static final long VARIANT_BITS = 0x8000000000000000L;

    /** 上次使用的时间戳，用于保证单调与时钟回拨处理。 */
    private static long lastTimestamp = -1L;

    /** 同一毫秒内的 rand_a 计数器。 */
    private static long counter = 0L;

    private UuidV7() {}

    /**
     * 生成一个有序的 UUID v7 字符串（标准带连字符格式，36 个字符）。
     *
     * @return uuid v7 字符串
     */
    public static synchronized String generate() {
        long timestamp = System.currentTimeMillis();

        if (timestamp > lastTimestamp) {
            // 进入新的一毫秒：rand_a 使用随机起点，之后在本毫秒内递增。
            lastTimestamp = timestamp;
            counter = RANDOM.nextInt((int) RAND_A_MASK + 1);
        } else {
            // 同一毫秒或时钟回拨：保持单调，不倒退时间戳。
            timestamp = lastTimestamp;
            counter = (counter + 1) & RAND_A_MASK;
            if (counter == 0) {
                // 本毫秒 12 位计数器已用尽，时间戳前进 1ms 以避免覆盖。
                lastTimestamp = lastTimestamp + 1;
                timestamp = lastTimestamp;
                counter = RANDOM.nextInt((int) RAND_A_MASK + 1);
            }
        }

        long randB = RANDOM.nextLong() & RAND_B_MASK;

        long msb = ((timestamp & TIMESTAMP_MASK) << 16) | VERSION_BITS | (counter & RAND_A_MASK);
        long lsb = VARIANT_BITS | randB;
        return new UUID(msb, lsb).toString();
    }
}
