package com.preonsurl.core;

import java.util.concurrent.locks.LockSupport;

public final class EpochIdGenerator {
    private static final int WORKER_BITS = 10;
    private static final int SEQUENCE_BITS = 11;
    private static final long MAX_WORKER_ID = (1L << WORKER_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final int TIMESTAMP_SHIFT = WORKER_BITS + SEQUENCE_BITS;
    private static final int WORKER_SHIFT = SEQUENCE_BITS;
    private static final long MAX_TIMESTAMP = (1L << 41) - 1;
    private static final long CUSTOM_EPOCH = 1767225600000L; // 2026-01-01 UTC

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public EpochIdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId must be between 0 and " + MAX_WORKER_ID);
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long timestamp = currentTimestamp();

        if (timestamp < lastTimestamp) {
            long diff = lastTimestamp - timestamp;
            if (diff <= 50) {
                timestamp = waitForNextMillis(lastTimestamp);
            } else {
                throw new IllegalStateException(
                        "System clock moved backwards by " + diff + "ms. last=" + lastTimestamp + ", current=" + timestamp);
            }
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
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
        long timestamp = System.currentTimeMillis() - CUSTOM_EPOCH;
        if (timestamp < 0) {
            throw new IllegalStateException("System clock is before custom epoch");
        }
        if (timestamp > MAX_TIMESTAMP) {
            throw new IllegalStateException("Timestamp exceeded 41-bit limit");
        }
        return timestamp;
    }

    private long waitForNextMillis(long previousTimestamp) {
        long timestamp;
        do {
            LockSupport.parkNanos(100_000);
            timestamp = currentTimestamp();
        } while (timestamp <= previousTimestamp);
        return timestamp;
    }

    public long getWorkerId() {
        return workerId;
    }
}
