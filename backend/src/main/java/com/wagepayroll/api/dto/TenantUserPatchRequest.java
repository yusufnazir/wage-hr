package com.wagepayroll.api.dto;

import java.util.List;
import java.util.UUID;

public record TenantUserPatchRequest(String email, List<UUID> roleIds) {
}
