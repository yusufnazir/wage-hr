package com.wagepayroll.api.dto;

import java.util.List;

public record TenantCurrenciesReplaceRequest(List<String> codes) {
}
