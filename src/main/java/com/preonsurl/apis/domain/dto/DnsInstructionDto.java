package com.preonsurl.apis.domain.dto;

public record DnsInstructionDto(
        String provider,
        String type,
        String host,
        String value,
        String ttl,
        String notes
) {}
