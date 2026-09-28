package com.preonsurl.coreconfig.exception;

public class CoreConfigNotFoundException extends RuntimeException {
    public CoreConfigNotFoundException(String configKey) {
        super("Core configuration not found: " + configKey);
    }

    public CoreConfigNotFoundException(String configKey, Throwable cause) {
        super("Core configuration not found: " + configKey, cause);
    }
}
