package com.preonsurl.apis.publiclink.dto;

public record AccessData(
        String pin,
        String password,
        String mode
) {}