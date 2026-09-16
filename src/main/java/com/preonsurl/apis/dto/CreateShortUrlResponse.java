package com.preonsurl.apis.dto;

public class CreateShortUrlResponse {

    private String shortUrl;
    private String shortCode;
    private String originalUrl;
    private String dirType;
    private boolean existing;

    public CreateShortUrlResponse() {
    }

    public CreateShortUrlResponse(String shortUrl, String shortCode, String originalUrl, String dirType, boolean existing) {
        this.shortUrl = shortUrl;
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.dirType = dirType;
        this.existing = existing;
    }

    public String getShortUrl() {
        return shortUrl;
    }

    public void setShortUrl(String shortUrl) {
        this.shortUrl = shortUrl;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getDirType() {
        return dirType;
    }

    public void setDirType(String dirType) {
        this.dirType = dirType;
    }

    public boolean isExisting() {
        return existing;
    }

    public void setExisting(boolean existing) {
        this.existing = existing;
    }
}
