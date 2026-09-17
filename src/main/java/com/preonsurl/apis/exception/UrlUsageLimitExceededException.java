package com.preonsurl.apis.exception;

public class UrlUsageLimitExceededException extends RuntimeException {
    public UrlUsageLimitExceededException(String message) {
        super(message);
    }
}
