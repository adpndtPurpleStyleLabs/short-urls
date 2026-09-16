package com.preonsurl.core;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Dependency-free 62-bit cryptographic permutation backed by an 8-round balanced Feistel cipher with HMAC-SHA256.
 * <p>
 * Guarantees a strict bijection (one-to-one mapping):
 * - Every 62-bit input yields a unique 62-bit output.
 * - decode(encode(x)) == x for all x in [0, 2^62 - 1].
 * - Completely hides sequential numbering and prevents URL enumeration attacks.
 */
public final class Feistel62Obfuscator {

    public static final int BITS = 62;
    private static final int HALF_BITS = 31;
    private static final long HALF_MASK = (1L << HALF_BITS) - 1; // 0x7FFFFFFF
    private static final int ROUNDS = 8;
    private static final long MAX_VALUE = (1L << BITS) - 1;

    private static final class ThreadLocalState {
        final Mac mac;
        final byte[] input = new byte[Long.BYTES + Integer.BYTES]; // 8 + 4 = 12 bytes
        final byte[] output = new byte[32]; // SHA-256 output buffer

        ThreadLocalState(byte[] secret) {
            try {
                this.mac = Mac.getInstance("HmacSHA256");
                this.mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize HMAC-SHA256 engine", e);
            }
        }
    }

    private final byte[] secret;
    private final ThreadLocal<ThreadLocalState> state;

    /**
     * Initializes the obfuscator with a secret key.
     *
     * @param secret secret key (minimum 16 characters required)
     */
    public Feistel62Obfuscator(String secret) {
        if (secret == null || secret.trim().length() < 16) {
            throw new IllegalArgumentException("Secret key must contain at least 16 characters for cryptographic safety");
        }
        this.secret = secret.trim().getBytes(StandardCharsets.UTF_8);
        this.state = ThreadLocal.withInitial(() -> new ThreadLocalState(this.secret));
    }

    /**
     * Encodes (permutates) a 62-bit integer into an obfuscated 62-bit integer.
     *
     * @param value positive 62-bit integer
     * @return obfuscated 62-bit integer
     */
    public long encode(long value) {
        validate(value);
        long left = (value >>> HALF_BITS) & HALF_MASK;
        long right = value & HALF_MASK;
        ThreadLocalState s = state.get();

        for (int round = 0; round < ROUNDS; round++) {
            long newLeft = right;
            long newRight = (left ^ roundFunction(s, right, round)) & HALF_MASK;
            left = newLeft;
            right = newRight;
        }

        return (left << HALF_BITS) | right;
    }

    /**
     * Decodes (reverses permutation) an obfuscated 62-bit integer back to its original value.
     *
     * @param value obfuscated 62-bit integer
     * @return original 62-bit integer
     */
    public long decode(long value) {
        validate(value);
        long left = (value >>> HALF_BITS) & HALF_MASK;
        long right = value & HALF_MASK;
        ThreadLocalState s = state.get();

        for (int round = ROUNDS - 1; round >= 0; round--) {
            long previousRight = left;
            long previousLeft = (right ^ roundFunction(s, left, round)) & HALF_MASK;
            left = previousLeft;
            right = previousRight;
        }

        return (left << HALF_BITS) | right;
    }

    private long roundFunction(ThreadLocalState s, long value, int round) {
        byte[] in = s.input;
        in[0] = (byte) (value >>> 56);
        in[1] = (byte) (value >>> 48);
        in[2] = (byte) (value >>> 40);
        in[3] = (byte) (value >>> 32);
        in[4] = (byte) (value >>> 24);
        in[5] = (byte) (value >>> 16);
        in[6] = (byte) (value >>> 8);
        in[7] = (byte) value;
        in[8] = (byte) (round >>> 24);
        in[9] = (byte) (round >>> 16);
        in[10] = (byte) (round >>> 8);
        in[11] = (byte) round;

        s.mac.update(in, 0, 12);
        try {
            s.mac.doFinal(s.output, 0);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC round computation error", e);
        }

        byte[] hash = s.output;
        long result = ((hash[0] & 0xFFL) << 24)
                | ((hash[1] & 0xFFL) << 16)
                | ((hash[2] & 0xFFL) << 8)
                | (hash[3] & 0xFFL);

        return result & HALF_MASK;
    }

    private static void validate(long value) {
        if (value < 0 || value > MAX_VALUE) {
            throw new IllegalArgumentException(
                    String.format("Value must be a positive 62-bit integer (0 <= value <= %d), got: %d", MAX_VALUE, value));
        }
    }

    /**
     * Clears the ThreadLocal state for the calling thread.
     */
    public void removeThreadLocal() {
        state.remove();
    }
}
