package com.preonsurl.apis.config;

import com.preonsurl.core.ShortCodePool;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ShortenerBeanConfigTest {

    @Test
    void shortCodePool_runsAndBuffersCodes_whenAppProfileIsActive() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("app");

        ShortenerBeanConfig config = new ShortenerBeanConfig(env);
        ReflectionTestUtils.setField(config, "workerCount", 2);
        ReflectionTestUtils.setField(config, "bucketCapacity", 10);
        ReflectionTestUtils.setField(config, "secret", "TEST-SECRET-1234567890");
        ReflectionTestUtils.setField(config, "domain", "http://localhost:8081");

        try (ShortCodePool pool = config.shortCodePool()) {
            assertNotNull(pool);
            assertEquals(2, pool.workerCount());
            assertTrue(pool.isAllWorkersAlive());
            assertTrue(pool.totalAvailableCodes() > 0);
            assertNotNull(pool.nextCode(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void shortCodePool_runsAndBuffersCodes_whenApp1ProfileIsActive() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("app-1");

        ShortenerBeanConfig config = new ShortenerBeanConfig(env);
        ReflectionTestUtils.setField(config, "workerCount", 2);
        ReflectionTestUtils.setField(config, "bucketCapacity", 10);
        ReflectionTestUtils.setField(config, "secret", "TEST-SECRET-1234567890");
        ReflectionTestUtils.setField(config, "domain", "http://localhost:8081");

        try (ShortCodePool pool = config.shortCodePool()) {
            assertNotNull(pool);
            assertEquals(2, pool.workerCount());
            assertTrue(pool.isAllWorkersAlive());
            assertTrue(pool.totalAvailableCodes() > 0);
        }
    }

    @Test
    void shortCodePool_isEmpty_whenServeProfileIsActive() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("serve");

        ShortenerBeanConfig config = new ShortenerBeanConfig(env);
        ReflectionTestUtils.setField(config, "workerCount", 2);
        ReflectionTestUtils.setField(config, "bucketCapacity", 10);
        ReflectionTestUtils.setField(config, "secret", "TEST-SECRET-1234567890");
        ReflectionTestUtils.setField(config, "domain", "http://localhost:8081");

        try (ShortCodePool pool = config.shortCodePool()) {
            assertNotNull(pool);
            assertEquals(0, pool.workerCount());
            assertEquals(0, pool.totalAvailableCodes());
            assertFalse(pool.isAllWorkersAlive());
            assertThrows(IllegalStateException.class, () -> pool.nextCode(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void shortCodePool_isEmpty_whenPsecureServeProfileIsActive() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("psecureServe");

        ShortenerBeanConfig config = new ShortenerBeanConfig(env);
        ReflectionTestUtils.setField(config, "workerCount", 2);
        ReflectionTestUtils.setField(config, "bucketCapacity", 10);
        ReflectionTestUtils.setField(config, "secret", "TEST-SECRET-1234567890");
        ReflectionTestUtils.setField(config, "domain", "http://localhost:8081");

        try (ShortCodePool pool = config.shortCodePool()) {
            assertNotNull(pool);
            assertEquals(0, pool.workerCount());
            assertEquals(0, pool.totalAvailableCodes());
            assertFalse(pool.isAllWorkersAlive());
        }
    }
}
