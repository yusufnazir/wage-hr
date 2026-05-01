package com.wagepayroll.api.dto;

import java.util.List;

public record PlatformRoleTemplateCreateRequest(String code, String displayName, List<String> privilegeCodes) {
}

