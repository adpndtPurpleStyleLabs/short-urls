package com.preonsurl.core;

public final class Base62 {
    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int BASE = ALPHABET.length;

    private Base62() {}

    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Only positive values are supported");
        }
        if (value == 0) {
            return "0";
        }
        char[] buffer = new char[11];
        int index = buffer.length;
        while (value > 0) {
            long remainder = value % BASE;
            buffer[--index] = ALPHABET[(int) remainder];
            value /= BASE;
        }
        return new String(buffer, index, buffer.length - index);
    }

    public static long decode(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("value cannot be empty");
        }
        long result = 0;
        for (int i = 0; i < value.length(); i++) {
            int digit = digit(value.charAt(i));
            result = Math.addExact(Math.multiplyExact(result, BASE), digit);
        }
        return result;
    }

    private static int digit(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'Z') return c - 'A' + 10;
        if (c >= 'a' && c <= 'z') return c - 'a' + 36;
        throw new IllegalArgumentException("Invalid Base62 character: " + c);
    }
}
