package com.wagepayroll.api.dto;

import java.util.List;

public record TenantRolePatchRequest(String name, List<String> privilegeCodes) {
}

