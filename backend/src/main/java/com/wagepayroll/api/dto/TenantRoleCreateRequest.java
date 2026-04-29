package com.wagepayroll.api.dto;

import java.util.List;

public record TenantRoleCreateRequest(String name, List<String> privilegeCodes) {
}

