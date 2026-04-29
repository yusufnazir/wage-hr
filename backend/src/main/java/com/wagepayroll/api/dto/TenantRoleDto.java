package com.wagepayroll.api.dto;

import java.util.List;
import java.util.UUID;

public record TenantRoleDto(UUID id, String name, List<String> privilegeCodes) {
}

