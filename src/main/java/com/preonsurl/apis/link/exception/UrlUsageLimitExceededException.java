package com.preonsurl.apis.link.exception;

public class UrlUsageLimitExceededException extends RuntimeException {
    public UrlUsageLimitExceededException(String message) {
        super(message);
    }
}
