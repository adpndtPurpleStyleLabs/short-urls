package com.preonsurl.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EpochIdGeneratorTest {

    @Test
    void generatesMonotonicallyIncreasingUniqueIds() {
        EpochIdGenerator generator = new EpochIdGenerator(1);
        int count = 10_000;
        Set<Long> generated = new HashSet<>(count);
        long lastId = -1;

        for (int i = 0; i < count; i++) {
            long id = generator.nextId();
            assertTrue(id > lastId, "ID must be monotonically increasing");
            assertTrue(generated.add(id), "ID must be unique");
            lastId = id;
        }

        assertEquals(count, generated.size());
    }

    @Test
    void workerIdValidation() {
        assertThrows(IllegalArgumentException.class, () -> new EpochIdGenerator(-1));
        assertThrows(IllegalArgumentException.class, () -> new EpochIdGenerator(1024));
        assertDoesNotThrow(() -> new EpochIdGenerator(0));
        assertDoesNotThrow(() -> new EpochIdGenerator(1023));
    }

    @Test
    void differentWorkersProduceDistinctIds() {
        EpochIdGenerator worker1 = new EpochIdGenerator(1);
        EpochIdGenerator worker2 = new EpochIdGenerator(2);

        long id1 = worker1.nextId();
        long id2 = worker2.nextId();

        assertNotEquals(id1, id2);
    }
}
