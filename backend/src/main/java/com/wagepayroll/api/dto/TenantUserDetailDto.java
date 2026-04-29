package com.wagepayroll.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TenantUserDetailDto(UUID userId, String email, String status, Instant lastActiveAt, List<String> roleNames,
		List<TenantUserRoleAssignmentDto> roleAssignments, List<TenantRoleOptionDto> assignableRoles) {
}
