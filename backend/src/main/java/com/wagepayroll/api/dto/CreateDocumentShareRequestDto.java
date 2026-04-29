package com.wagepayroll.api.dto;

import java.util.UUID;

/**
 * Exactly one of {@code granteeUserId} or {@code granteeRoleId} must be set (enforced in service).
 */
public record CreateDocumentShareRequestDto(UUID granteeUserId, UUID granteeRoleId) {
}
