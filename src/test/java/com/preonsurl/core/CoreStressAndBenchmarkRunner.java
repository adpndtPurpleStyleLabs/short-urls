package com.preonsurl.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * High-performance stress testing and benchmark suite for PreonsURL Core.
 * <p>
 * Evaluates:
 * 1. Throughput (Codes Generated per Second) across different worker counts (1, 2, 4, 8, 16).
 * 2. Strict Uniqueness and Collisions across up to 100 Million generated codes.
 * 3. Memory efficiency using primitive long arrays to prevent OOM.
 */
public class CoreStressAndBenchmarkRunner {

    private static final String DEFAULT_SECRET = "BENCHMARK-SECRET-KEY-PREONS-2026-X99";
    private static final NumberFormat NUM_FMT = NumberFormat.getNumberInstance(Locale.US);

    public static void main(String[] args) throws Exception {
        int totalCodes = (args.length > 0) ? Integer.parseInt(args[0]) : 100_000_000;
        int[] workerConfigs = (args.length > 1)
                ? Arrays.stream(args[1].split(",")).mapToInt(Integer::parseInt).toArray()
                : new int[]{1, 2, 4, 8, 16};
        String secret = (args.length > 2) ? args[2] : DEFAULT_SECRET;

        runBenchmarkSuite(totalCodes, workerConfigs, secret);
    }

    @Test
    @Tag("stress")
    void runFastBenchmarkInTestSuite() throws Exception {
        // Run a fast 1,000,000 verification in regular test suite (or 100M if -Dbenchmark.count=100000000 is specified)
        String countProp = System.getProperty("benchmark.count");
        int count = (countProp != null) ? Integer.parseInt(countProp) : 1_000_000;
        int[] workers = new int[]{1, 2, 4};

        BenchmarkResult result = benchmarkWorkers(count, 4, DEFAULT_SECRET);
        assertEquals(0, result.collisions, "Zero collisions expected across generated codes");
    }

    public static void runBenchmarkSuite(int totalCodes, int[] workerConfigs, String secret) throws Exception {
        System.out.println("==========================================================================================================");
        System.out.println("                              PREONSURL CORE " + NUM_FMT.format(totalCodes) + " CODE BENCHMARK");
        System.out.println("==========================================================================================================");
        System.out.println(String.format(" %-9s | %-15s | %-12s | %-18s | %-12s | %-12s",
                "Workers", "Total Codes", "Time Taken", "Codes / Sec", "Collisions", "Max Memory"));
        System.out.println("-----------+-----------------+--------------+--------------------+--------------+-------------");

        for (int workers : workerConfigs) {
            System.gc();
            Thread.sleep(200); // Allow GC to settle
            BenchmarkResult res = benchmarkWorkers(totalCodes, workers, secret);
            System.out.println(String.format(" %-9d | %-15s | %-12s | %-18s | %-12d | %-12s",
                    res.workers,
                    NUM_FMT.format(res.totalCodes),
                    String.format("%.2f s", res.timeTakenSeconds),
                    NUM_FMT.format((long) res.codesPerSecond) + " /s",
                    res.collisions,
                    String.format("%d MB", res.usedMemoryMb)));
        }

        System.out.println("==========================================================================================================");
    }

    public static BenchmarkResult benchmarkWorkers(int totalCodes, int workerCount, String secret) throws Exception {
        int bucketCapacity = Math.min(10_000, Math.max(1_000, totalCodes / (workerCount * 10)));
        long[] samples = new long[totalCodes];

        long memBefore = getUsedMemoryMb();
        long startTime = System.nanoTime();

        try (ShortCodePool pool = new ShortCodePool(workerCount, bucketCapacity, secret)) {
            pool.start();

            int threads = Math.max(workerCount, Runtime.getRuntime().availableProcessors());
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            int batchSize = 10_000;
            int totalBatches = totalCodes / batchSize;
            int remainder = totalCodes % batchSize;

            AtomicInteger indexCounter = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(totalBatches + (remainder > 0 ? 1 : 0));

            for (int b = 0; b < totalBatches; b++) {
                executor.submit(() -> {
                    try {
                        int startIdx = indexCounter.getAndAdd(batchSize);
                        for (int i = 0; i < batchSize; i++) {
                            String code = pool.nextCode(30, TimeUnit.SECONDS);
                            // Decode to 62-bit long to store in 8-byte primitive for collision detection
                            samples[startIdx + i] = Base62.decode(code);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            if (remainder > 0) {
                executor.submit(() -> {
                    try {
                        int startIdx = indexCounter.getAndAdd(remainder);
                        for (int i = 0; i < remainder; i++) {
                            String code = pool.nextCode(30, TimeUnit.SECONDS);
                            samples[startIdx + i] = Base62.decode(code);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();
        }

        long durationNanos = System.nanoTime() - startTime;
        double durationSeconds = durationNanos / 1_000_000_000.0;
        double codesPerSecond = totalCodes / durationSeconds;
        long memAfter = getUsedMemoryMb();

        // Exact collision detection: parallelSort O(N log N) then linear scan
        Arrays.parallelSort(samples);
        long collisions = 0;
        for (int i = 0; i < samples.length - 1; i++) {
            if (samples[i] == samples[i + 1]) {
                collisions++;
            }
        }

        return new BenchmarkResult(
                workerCount,
                totalCodes,
                durationSeconds,
                codesPerSecond,
                collisions,
                Math.max(0, memAfter - memBefore)
        );
    }

    private static long getUsedMemoryMb() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    public static class BenchmarkResult {
        public final int workers;
        public final int totalCodes;
        public final double timeTakenSeconds;
        public final double codesPerSecond;
        public final long collisions;
        public final long usedMemoryMb;

        public BenchmarkResult(int workers, int totalCodes, double timeTakenSeconds,
                               double codesPerSecond, long collisions, long usedMemoryMb) {
            this.workers = workers;
            this.totalCodes = totalCodes;
            this.timeTakenSeconds = timeTakenSeconds;
            this.codesPerSecond = codesPerSecond;
            this.collisions = collisions;
            this.usedMemoryMb = usedMemoryMb;
        }
    }
}
