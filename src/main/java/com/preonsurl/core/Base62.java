package com.preonsurl.core;

import java.util.Arrays;

/**
 * High-performance Base62 encoder and decoder for unsigned 64-bit positive integers.
 * Uses a branchless lookup table for O(1) character decoding.
 */
public final class Base62 {

    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int BASE = ALPHABET.length; // 62

    // Precomputed ASCII lookup table for branchless decoding
    private static final byte[] DECODE_TABLE = new byte[128];

    static {
        Arrays.fill(DECODE_TABLE, (byte) -1);
        for (int i = 0; i < ALPHABET.length; i++) {
            DECODE_TABLE[ALPHABET[i]] = (byte) i;
        }
    }

    private Base62() {
        // Utility class
    }

    /**
     * Encodes a positive 64-bit integer into a Base62 string.
     *
     * @param value the positive integer to encode (must be >= 0)
     * @return Base62 encoded string
     * @throws IllegalArgumentException if value is negative
     */
    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Only non-negative values are supported: " + value);
        }
        if (value == 0) {
            return "0";
        }

        // Long.MAX_VALUE in base 62 has at most 11 characters
        char[] buffer = new char[12];
        int index = buffer.length;

        while (value > 0) {
            int remainder = (int) (value % BASE);
            buffer[--index] = ALPHABET[remainder];
            value /= BASE;
        }

        return new String(buffer, index, buffer.length - index);
    }

    /**
     * Decodes a Base62 string back into a 64-bit positive integer.
     *
     * @param value Base62 string
     * @return decoded positive integer
     * @throws IllegalArgumentException if string is null, empty, or contains invalid characters
     * @throws ArithmeticException      if the decoded value overflows long
     */
    public static long decode(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Base62 string cannot be null or empty");
        }

        long result = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= DECODE_TABLE.length || DECODE_TABLE[c] < 0) {
                throw new IllegalArgumentException("Invalid Base62 character: '" + c + "' at position " + i);
            }
            int digit = DECODE_TABLE[c];
            result = Math.addExact(Math.multiplyExact(result, BASE), digit);
        }
        return result;
    }
}
