package com.preonsurl.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;

/**
 * Dedicated background worker bucket that pre-generates and buffers short codes in memory.
 * Runs on a dedicated daemon thread to refill its internal queue proactively.
 */
final class ShortCodeBucket implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ShortCodeBucket.class);

    private final ArrayBlockingQueue<String> queue;
    private final ShortCodeGenerator generator;
    private final Runnable onCodeGenerated;
    private final CountDownLatch initiallyFull = new CountDownLatch(1);
    private final int workerId;
    private final Thread workerThread;
    private volatile boolean running = true;

    ShortCodeBucket(int workerId, int capacity, String secret, Runnable onCodeGenerated) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Bucket capacity must be positive: " + capacity);
        }
        this.workerId = workerId;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.generator = new ShortCodeGenerator(workerId, secret);
        this.onCodeGenerated = onCodeGenerated;
        this.workerThread = new Thread(this::runWorker, "short-code-worker-" + workerId);
        this.workerThread.setDaemon(true);
    }

    void start() {
        workerThread.start();
    }

    void awaitInitiallyFull() throws InterruptedException {
        initiallyFull.await();
    }

    private void runWorker() {
        try {
            while (running) {
                try {
                    String code = generator.nextCode();
                    queue.put(code);
                    onCodeGenerated.run();

                    if (initiallyFull.getCount() > 0 && queue.remainingCapacity() == 0) {
                        initiallyFull.countDown();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Throwable t) {
                    if (!running) break;
                    log.error("Worker {} encountered unexpected error generating code: {}", workerId, t.getMessage(), t);
                    LockSupport.parkNanos(10_000_000); // 10ms backoff before retry
                }
            }
        } finally {
            initiallyFull.countDown();
        }
    }

    String tryTake() {
        return queue.poll();
    }

    int size() {
        return queue.size();
    }

    int remainingCapacity() {
        return queue.remainingCapacity();
    }

    boolean isWorkerAlive() {
        return workerThread.isAlive();
    }

    int getWorkerId() {
        return workerId;
    }

    @Override
    public void close() {
        running = false;
        workerThread.interrupt();
    }
}
