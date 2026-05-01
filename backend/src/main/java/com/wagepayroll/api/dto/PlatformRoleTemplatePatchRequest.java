package com.wagepayroll.api.dto;

import java.util.List;

public record PlatformRoleTemplatePatchRequest(String displayName, List<String> privilegeCodes) {
}

