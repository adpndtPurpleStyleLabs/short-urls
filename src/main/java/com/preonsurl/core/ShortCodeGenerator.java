package com.preonsurl.core;

/**
 * Generates obfuscated Base62 short codes by coordinating the Epoch ID Generator,
 * Feistel Cipher Obfuscator, and Base62 Encoder.
 */
public final class ShortCodeGenerator {

    private final EpochIdGenerator idGenerator;
    private final Feistel62Obfuscator obfuscator;

    /**
     * Constructs a ShortCodeGenerator for a specific worker.
     *
     * @param workerId worker node identifier (0 to 1023)
     * @param secret   cryptographic secret for obfuscation (minimum 16 chars)
     */
    public ShortCodeGenerator(long workerId, String secret) {
        this.idGenerator = new EpochIdGenerator(workerId);
        this.obfuscator = new Feistel62Obfuscator(secret);
    }

    /**
     * Constructs a ShortCodeGenerator with a specific custom epoch.
     *
     * @param workerId    worker node identifier (0 to 1023)
     * @param customEpoch epoch start time in milliseconds
     * @param secret      cryptographic secret for obfuscation
     */
    public ShortCodeGenerator(long workerId, long customEpoch, String secret) {
        this.idGenerator = new EpochIdGenerator(workerId, customEpoch);
        this.obfuscator = new Feistel62Obfuscator(secret);
    }

    /**
     * Generates the next unique, non-sequential Base62 short code.
     *
     * @return unique short code string
     */
    public String nextCode() {
        long id = idGenerator.nextId();
        long obfuscated = obfuscator.encode(id);
        return Base62.encode(obfuscated);
    }

    /**
     * Decodes a short code back to its original Snowflake/Epoch ID.
     *
     * @param code Base62 short code
     * @return original generated ID
     */
    public long decodeCode(String code) {
        long obfuscated = Base62.decode(code);
        return obfuscator.decode(obfuscated);
    }

    public long getWorkerId() {
        return idGenerator.getWorkerId();
    }
}
