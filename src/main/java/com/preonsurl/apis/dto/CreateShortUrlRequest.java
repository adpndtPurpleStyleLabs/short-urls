package com.preonsurl.apis.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateShortUrlRequest {

    @NotBlank(message = "url cannot be empty")
    private String url;

    private String dirType;

    public CreateShortUrlRequest() {
    }

    public CreateShortUrlRequest(String url, String dirType) {
        this.url = url;
        this.dirType = dirType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDirType() {
        return dirType;
    }

    public void setDirType(String dirType) {
        this.dirType = dirType;
    }
}
