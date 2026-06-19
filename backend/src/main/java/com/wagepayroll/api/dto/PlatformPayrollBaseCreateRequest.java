package com.wagepayroll.api.dto;

public record PlatformPayrollBaseCreateRequest(String code, String name, String category, Boolean active) {
}
