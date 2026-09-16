package com.preonsurl.core;

import java.util.concurrent.locks.LockSupport;

/**
 * Distributed 64-bit unique ID generator inspired by Twitter Snowflake.
 * <p>
 * Bit allocation (62 bits total, fits inside positive signed 64-bit long):
 * - 41 bits: Milliseconds since custom epoch (approx 69 years)
 * - 10 bits: Worker ID (up to 1,024 worker threads/instances)
 * - 11 bits: Sequence number (up to 2,048 IDs per millisecond per worker)
 */
public final class EpochIdGenerator {

    public static final int WORKER_BITS = 10;
    public static final int SEQUENCE_BITS = 11;
    public static final long MAX_WORKER_ID = (1L << WORKER_BITS) - 1;       // 1023
    public static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;       // 2047
    public static final long MAX_TIMESTAMP = (1L << 41) - 1;

    private static final int TIMESTAMP_SHIFT = WORKER_BITS + SEQUENCE_BITS;  // 21
    private static final int WORKER_SHIFT = SEQUENCE_BITS;                   // 11

    /** Default custom epoch: 2024-01-01 00:00:00 UTC (1704067200000L) */
    public static final long DEFAULT_CUSTOM_EPOCH = 1704067200000L;

    private final long workerId;
    private final long customEpoch;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    /**
     * Creates an EpochIdGenerator with the default custom epoch.
     *
     * @param workerId worker identifier (0 to 1023)
     */
    public EpochIdGenerator(long workerId) {
        this(workerId, DEFAULT_CUSTOM_EPOCH);
    }

    /**
     * Creates an EpochIdGenerator with a specific custom epoch.
     *
     * @param workerId    worker identifier (0 to 1023)
     * @param customEpoch epoch timestamp in milliseconds
     */
    public EpochIdGenerator(long workerId, long customEpoch) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    String.format("workerId must be between 0 and %d, got: %d", MAX_WORKER_ID, workerId));
        }
        this.workerId = workerId;
        this.customEpoch = customEpoch;
    }

    /**
     * Generates the next monotonically increasing unique 62-bit ID.
     *
     * @return unique 62-bit positive integer
     */
    public synchronized long nextId() {
        long timestamp = currentTimestamp();

        // Handle clock backwards anomaly
        if (timestamp < lastTimestamp) {
            long diff = lastTimestamp - timestamp;
            if (diff <= 50) {
                // Short clock jitter: spin/wait until system clock catches up
                timestamp = waitForNextMillis(lastTimestamp);
            } else {
                throw new IllegalStateException(String.format(
                        "System clock moved backwards by %dms. Refusing to generate ID for %dms",
                        diff, diff));
            }
        }

        // Same millisecond: increment sequence
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // Sequence exhausted within the current millisecond; wait for next ms
                timestamp = waitForNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return (timestamp << TIMESTAMP_SHIFT)
                | (workerId << WORKER_SHIFT)
                | sequence;
    }

    private long currentTimestamp() {
        long timestamp = System.currentTimeMillis() - customEpoch;
        if (timestamp < 0) {
            throw new IllegalStateException(
                    "System clock is earlier than the configured custom epoch (" + customEpoch + ")");
        }
        if (timestamp > MAX_TIMESTAMP) {
            throw new IllegalStateException("Timestamp exceeded 41-bit capacity. Generation epoch expired.");
        }
        return timestamp;
    }

    private long waitForNextMillis(long previousTimestamp) {
        long timestamp;
        do {
            LockSupport.parkNanos(100_000); // 100 microseconds backoff
            timestamp = currentTimestamp();
        } while (timestamp <= previousTimestamp);
        return timestamp;
    }

    public long getWorkerId() {
        return workerId;
    }

    public long getCustomEpoch() {
        return customEpoch;
    }
}
