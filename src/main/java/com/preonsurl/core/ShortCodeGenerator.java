package com.preonsurl.core;

public final class ShortCodeGenerator {
    private final EpochIdGenerator idGenerator;
    private final Feistel62Obfuscator obfuscator;

    public ShortCodeGenerator(long workerId, String secret) {
        this.idGenerator = new EpochIdGenerator(workerId);
        this.obfuscator = new Feistel62Obfuscator(secret);
    }

    public String nextCode() {
        long id = idGenerator.nextId();
        long obfuscated = obfuscator.encode(id);
        return Base62.encode(obfuscated);
    }

    public long decodeCode(String code) {
        return obfuscator.decode(Base62.decode(code));
    }
}
