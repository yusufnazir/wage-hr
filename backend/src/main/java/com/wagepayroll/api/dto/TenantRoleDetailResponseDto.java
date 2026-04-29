package com.wagepayroll.api.dto;

import java.util.List;

public record TenantRoleDetailResponseDto(TenantRoleDto role, List<String> assignablePrivilegeCodes) {
}

