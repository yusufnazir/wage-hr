package com.wagepayroll.api.dto;

import java.util.UUID;

public record TenantUserRoleAssignmentDto(UUID roleId, String roleName) {
}
