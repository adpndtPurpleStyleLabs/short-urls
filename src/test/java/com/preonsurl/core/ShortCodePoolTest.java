package com.preonsurl.core;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ShortCodePoolTest {

    private static final String SECRET = "TEST-SHORT-CODE-POOL-SECRET-2026";

    @Test
    void multiThreadedCodeGenerationProducesZeroDuplicates() throws Exception {
        int workerCount = 4;
        int bucketCapacity = 50;

        try (ShortCodePool pool = new ShortCodePool(workerCount, bucketCapacity, SECRET)) {
            pool.start();

            assertTrue(pool.isAllWorkersAlive());
            assertEquals(workerCount, pool.workerCount());
            assertTrue(pool.totalAvailableCodes() > 0);

            int totalRequests = 2000;
            int threadCount = 8;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            Set<String> generatedCodes = Collections.synchronizedSet(new HashSet<>(totalRequests));
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(totalRequests);

            for (int i = 0; i < totalRequests; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        String code = pool.nextCode(5, TimeUnit.SECONDS);
                        assertNotNull(code);
                        assertFalse(code.isEmpty());
                        generatedCodes.add(code);
                    } catch (Exception e) {
                        fail("Exception during code retrieval: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // Release all threads simultaneously
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "All tasks should complete within timeout");
            executor.shutdown();

            assertEquals(totalRequests, generatedCodes.size(), "All generated codes must be strictly unique");
        }
    }

    @Test
    void poolValidation() {
        assertThrows(IllegalArgumentException.class, () -> new ShortCodePool(0, 10, SECRET));
        assertThrows(IllegalArgumentException.class, () -> new ShortCodePool(1025, 10, SECRET));
        assertThrows(IllegalArgumentException.class, () -> new ShortCodePool(2, 0, SECRET));
    }
}
