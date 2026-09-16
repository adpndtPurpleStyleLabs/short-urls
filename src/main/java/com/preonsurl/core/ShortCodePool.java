package com.preonsurl.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-throughput multi-worker short code buffer pool.
 * <p>
 * Maintains background worker buckets that continually replenish pre-generated short codes
 * in memory. Code requests are fulfilled in sub-microsecond time via spin-wait and lock-free
 * bucket polling, completely decoupling ID generation from request latency.
 */
public final class ShortCodePool implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ShortCodePool.class);

    private static final int SPIN_WAIT_LIMIT = 250;
    private static final int MAX_WORKER_COUNT = 1024;

    private final List<ShortCodeBucket> buckets;
    private final Semaphore availableCodes = new Semaphore(0);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean started = new AtomicBoolean(false);

    /**
     * Initializes the short code pool with specified worker count and capacity.
     *
     * @param workerCount    number of worker threads (1 to 1024)
     * @param bucketCapacity buffer queue capacity per worker
     * @param secret         cryptographic secret for obfuscation (min 16 chars)
     */
    public ShortCodePool(int workerCount, int bucketCapacity, String secret) {
        if (workerCount <= 0 || workerCount > MAX_WORKER_COUNT) {
            throw new IllegalArgumentException(
                    String.format("workerCount must be between 1 and %d, got: %d", MAX_WORKER_COUNT, workerCount));
        }
        if (bucketCapacity <= 0) {
            throw new IllegalArgumentException("bucketCapacity must be positive, got: " + bucketCapacity);
        }

        this.buckets = new ArrayList<>(workerCount);
        for (int i = 0; i < workerCount; i++) {
            buckets.add(new ShortCodeBucket(i, bucketCapacity, secret, availableCodes::release));
        }
    }

    /**
     * Starts all background worker threads and waits until all buckets are initially filled.
     *
     * @throws InterruptedException if thread is interrupted while waiting
     */
    public void start() throws InterruptedException {
        if (started.compareAndSet(false, true)) {
            log.info("Starting ShortCodePool with {} worker buckets...", buckets.size());
            for (ShortCodeBucket bucket : buckets) {
                bucket.start();
            }
            for (ShortCodeBucket bucket : buckets) {
                bucket.awaitInitiallyFull();
            }
            log.info("ShortCodePool is ready. Initial available codes: {}", totalAvailableCodes());
        }
    }

    /**
     * Retrieves the next available short code, blocking indefinitely if buffer is empty.
     *
     * @return unique Base62 short code
     * @throws InterruptedException if interrupted while waiting
     */
    public String nextCode() throws InterruptedException {
        try {
            return nextCode(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Unexpected timeout waiting for short code", e);
        }
    }

    /**
     * Retrieves the next available short code within the specified timeout.
     *
     * @param timeout duration to wait
     * @param unit    time unit
     * @return unique Base62 short code
     * @throws InterruptedException if thread is interrupted
     * @throws TimeoutException     if code is not available within the timeout
     */
    public String nextCode(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        checkRunning();

        long deadlineNanos = (timeout == Long.MAX_VALUE) ? Long.MAX_VALUE : (System.nanoTime() + unit.toNanos(timeout));

        // Fast-path: optimistic spin-wait to acquire permit with sub-microsecond latency
        if (!acquirePermit(deadlineNanos)) {
            throw new TimeoutException("Timed out waiting for available short code permit");
        }

        boolean taken = false;
        try {
            while (running.get()) {
                int start = ThreadLocalRandom.current().nextInt(buckets.size());
                for (int i = 0; i < buckets.size(); i++) {
                    int index = (start + i) % buckets.size();
                    String code = buckets.get(index).tryTake();
                    if (code != null) {
                        taken = true;
                        return code;
                    }
                }

                if (deadlineNanos != Long.MAX_VALUE && System.nanoTime() >= deadlineNanos) {
                    throw new TimeoutException("Timed out taking short code from worker queues");
                }
                Thread.onSpinWait();
            }
            throw new IllegalStateException("ShortCodePool is closed");
        } finally {
            if (!taken) {
                availableCodes.release(); // Restore permit if we failed to obtain a code
            }
        }
    }

    private boolean acquirePermit(long deadlineNanos) throws InterruptedException {
        if (availableCodes.tryAcquire()) {
            return true;
        }

        // Spin-wait optimization
        for (int spin = 0; spin < SPIN_WAIT_LIMIT; spin++) {
            Thread.onSpinWait();
            if (availableCodes.tryAcquire()) {
                return true;
            }
        }

        Thread.yield();
        if (availableCodes.tryAcquire()) {
            return true;
        }

        // Fall back to blocking semaphore acquire
        if (deadlineNanos == Long.MAX_VALUE) {
            availableCodes.acquire();
            return true;
        } else {
            long remainingNanos = deadlineNanos - System.nanoTime();
            return remainingNanos > 0 && availableCodes.tryAcquire(remainingNanos, TimeUnit.NANOSECONDS);
        }
    }

    private void checkRunning() {
        if (!running.get()) {
            throw new IllegalStateException("ShortCodePool is closed");
        }
    }

    /**
     * Returns total pre-buffered codes ready in memory across all buckets.
     */
    public int totalAvailableCodes() {
        int total = 0;
        for (ShortCodeBucket bucket : buckets) {
            total += bucket.size();
        }
        return total;
    }

    public int workerCount() {
        return buckets.size();
    }

    public boolean isAllWorkersAlive() {
        for (ShortCodeBucket bucket : buckets) {
            if (!bucket.isWorkerAlive()) {
                return false;
            }
        }
        return true;
    }

    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void close() {
        if (running.compareAndSet(true, false)) {
            log.info("Shutting down ShortCodePool and stopping worker threads...");
            for (ShortCodeBucket bucket : buckets) {
                bucket.close();
            }
        }
    }
}
