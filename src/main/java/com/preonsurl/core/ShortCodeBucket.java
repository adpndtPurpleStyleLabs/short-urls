package com.preonsurl.core;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;

final class ShortCodeBucket implements AutoCloseable {
    private final ArrayBlockingQueue<String> queue;
    private final ShortCodeGenerator generator;
    private final Runnable onCodeGenerated;
    private final CountDownLatch initiallyFull = new CountDownLatch(1);
    private final int workerId;
    private final Thread workerThread;
    private volatile boolean running = true;

    ShortCodeBucket(int workerId, int capacity, String secret, Runnable onCodeGenerated) {
        this.workerId = workerId;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.generator = new ShortCodeGenerator(workerId, secret);
        this.onCodeGenerated = onCodeGenerated;
        this.workerThread = new Thread(this::runWorker, "url-code-worker-" + workerId);
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
                    System.err.println("Worker " + workerId + " unexpected error: " + t.getMessage());
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

    boolean isWorkerAlive() {
        return workerThread.isAlive();
    }

    @Override
    public void close() {
        running = false;
        workerThread.interrupt();
    }
}

