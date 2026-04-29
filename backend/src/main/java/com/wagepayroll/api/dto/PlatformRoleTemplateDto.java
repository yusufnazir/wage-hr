package com.wagepayroll.api.dto;

import java.util.List;
import java.util.UUID;

public record PlatformRoleTemplateDto(UUID id, String code, String displayName, List<String> privilegeCodes) {
}

