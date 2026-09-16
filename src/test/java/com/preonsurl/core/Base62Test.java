package com.preonsurl.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Base62Test {

    @Test
    void encodeZeroReturnsZero() {
        assertEquals("0", Base62.encode(0));
        assertEquals(0, Base62.decode("0"));
    }

    @Test
    void encodeAndDecodeAreReversible() {
        long[] testValues = {
                1L, 61L, 62L, 63L, 1000L, 123456789L,
                10000000000L, 5000000000000L,
                (1L << 61), Long.MAX_VALUE
        };

        for (long val : testValues) {
            String encoded = Base62.encode(val);
            assertNotNull(encoded);
            assertFalse(encoded.isEmpty());
            long decoded = Base62.decode(encoded);
            assertEquals(val, decoded, "Failed roundtrip for value: " + val);
        }
    }

    @Test
    void encodeNegativeThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> Base62.encode(-1));
        assertThrows(IllegalArgumentException.class, () -> Base62.encode(Long.MIN_VALUE));
    }

    @Test
    void decodeInvalidStringThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> Base62.decode(null));
        assertThrows(IllegalArgumentException.class, () -> Base62.decode(""));
        assertThrows(IllegalArgumentException.class, () -> Base62.decode("abc@123"));
        assertThrows(IllegalArgumentException.class, () -> Base62.decode("invalid-char!"));
    }
}
