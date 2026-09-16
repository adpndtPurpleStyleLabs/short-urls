package com.preonsurl.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class ShortCodePool implements AutoCloseable {
    private final List<ShortCodeBucket> buckets;
    private final Semaphore availableCodes = new Semaphore(0);
    private final AtomicInteger roundRobin = new AtomicInteger();
    private volatile boolean running = true;

    public ShortCodePool(int workerCount, int bucketCapacity, String secret) {
        if (workerCount <= 0) throw new IllegalArgumentException("workerCount must be > 0");
        if (workerCount > 1024) throw new IllegalArgumentException("Maximum workerCount is 1024");
        if (bucketCapacity <= 0) throw new IllegalArgumentException("bucketCapacity must be > 0");

        this.buckets = new ArrayList<>(workerCount);
        for (int i = 0; i < workerCount; i++) {
            buckets.add(new ShortCodeBucket(i, bucketCapacity, secret, availableCodes::release));
        }
    }

    public void start() throws InterruptedException {
        for (ShortCodeBucket bucket : buckets) bucket.start();
        for (ShortCodeBucket bucket : buckets) bucket.awaitInitiallyFull();
    }

    public String nextCode() throws InterruptedException {
        if (!availableCodes.tryAcquire()) {
            boolean acquired = false;
            for (int spin = 0; spin < 250; spin++) {
                Thread.onSpinWait();
                if (availableCodes.tryAcquire()) {
                    acquired = true;
                    break;
                }
            }
            if (!acquired) {
                Thread.yield();
                if (availableCodes.tryAcquire()) {
                    acquired = true;
                } else {
                    for (int spin = 0; spin < 250; spin++) {
                        Thread.onSpinWait();
                        if (availableCodes.tryAcquire()) {
                            acquired = true;
                            break;
                        }
                    }
                    if (!acquired) {
                        availableCodes.acquire();
                    }
                }
            }
        }
        boolean success = false;
        try {
            while (running) {
                int start = ThreadLocalRandom.current().nextInt(buckets.size());
                for (int i = 0; i < buckets.size(); i++) {
                    int index = (start + i) % buckets.size();
                    String code = buckets.get(index).tryTake();
                    if (code != null) {
                        success = true;
                        return code;
                    }
                }
                Thread.onSpinWait();
            }
            throw new IllegalStateException("Pool is closed");
        } finally {
            if (!success) {
                availableCodes.release();
            }
        }
    }

    public String nextCode(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        if (!availableCodes.tryAcquire()) {
            boolean acquired = false;
            for (int spin = 0; spin < 250; spin++) {
                Thread.onSpinWait();
                if (availableCodes.tryAcquire()) {
                    acquired = true;
                    break;
                }
            }
            if (!acquired) {
                Thread.yield();
                if (availableCodes.tryAcquire()) {
                    acquired = true;
                } else {
                    for (int spin = 0; spin < 250; spin++) {
                        Thread.onSpinWait();
                        if (availableCodes.tryAcquire()) {
                            acquired = true;
                            break;
                        }
                    }
                    if (!acquired) {
                        long remainingNanos = deadlineNanos - System.nanoTime();
                        if (remainingNanos <= 0 || !availableCodes.tryAcquire(remainingNanos, TimeUnit.NANOSECONDS)) {
                            throw new TimeoutException("Timed out waiting for available short code permit");
                        }
                    }
                }
            }
        }
        boolean success = false;
        try {
            while (running) {
                int start = ThreadLocalRandom.current().nextInt(buckets.size());
                for (int i = 0; i < buckets.size(); i++) {
                    int index = (start + i) % buckets.size();
                    String code = buckets.get(index).tryTake();
                    if (code != null) {
                        success = true;
                        return code;
                    }
                }
                if (System.nanoTime() >= deadlineNanos) {
                    throw new TimeoutException("Timed out taking short code from buckets");
                }
                Thread.onSpinWait();
            }
            throw new IllegalStateException("Pool is closed");
        } finally {
            if (!success) {
                availableCodes.release();
            }
        }
    }

    public String nextCodeRandom() throws InterruptedException {
        return nextCode();
    }

    public int totalAvailableCodes() {
        int total = 0;
        for (ShortCodeBucket bucket : buckets) total += bucket.size();
        return total;
    }

    public int workerCount() {
        return buckets.size();
    }

    public boolean isAllWorkersAlive() {
        for (ShortCodeBucket bucket : buckets) {
            if (!bucket.isWorkerAlive()) return false;
        }
        return true;
    }

    @Override
    public void close() {
        running = false;
        for (ShortCodeBucket bucket : buckets) bucket.close();
    }
}

