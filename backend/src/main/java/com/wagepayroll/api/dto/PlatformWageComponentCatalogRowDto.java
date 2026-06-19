package com.wagepayroll.api.dto;

import java.util.UUID;

public record PlatformWageComponentCatalogRowDto(UUID id, String countryCode, String code, String name) {
}
