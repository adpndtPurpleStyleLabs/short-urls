package com.preonsurl.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class Feistel62ObfuscatorTest {

    private static final String SECRET = "TESTING-FEISTEL-SECRET-KEY-2026";

    @Test
    void encodeAndDecodeAreBijective() {
        Feistel62Obfuscator obfuscator = new Feistel62Obfuscator(SECRET);

        long[] testValues = {
                0L, 1L, 2L, 100L, 99999L, 1234567890L,
                (1L << 31) - 1, (1L << 31),
                (1L << 60), (1L << 62) - 1
        };

        for (long val : testValues) {
            long encoded = obfuscator.encode(val);
            long decoded = obfuscator.decode(encoded);

            assertEquals(val, decoded, "Decode should reverse encode for value: " + val);
        }
    }

    @Test
    void sequentialValuesProduceNonSequentialOutputs() {
        Feistel62Obfuscator obfuscator = new Feistel62Obfuscator(SECRET);
        int count = 1000;
        Set<Long> outputs = new HashSet<>(count);

        for (long i = 1; i <= count; i++) {
            long encoded = obfuscator.encode(i);
            assertNotEquals(i, encoded, "Obfuscated output should not equal input");
            assertTrue(outputs.add(encoded), "Collisions must not occur in Feistel permutation");
        }

        assertEquals(count, outputs.size());
    }

    @Test
    void rejectsShortSecret() {
        assertThrows(IllegalArgumentException.class, () -> new Feistel62Obfuscator(null));
        assertThrows(IllegalArgumentException.class, () -> new Feistel62Obfuscator("short"));
        assertThrows(IllegalArgumentException.class, () -> new Feistel62Obfuscator("less-than-16"));
    }

    @Test
    void rejectsOutOfBoundValues() {
        Feistel62Obfuscator obfuscator = new Feistel62Obfuscator(SECRET);
        assertThrows(IllegalArgumentException.class, () -> obfuscator.encode(-1));
        assertThrows(IllegalArgumentException.class, () -> obfuscator.encode(1L << 62));
    }
}
