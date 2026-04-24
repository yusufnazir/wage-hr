package com.wagepayroll.api.dto;

import java.util.List;
import java.util.UUID;

public record NavigationItemDto(UUID id, String path, String labelKey, int sortOrder, List<NavigationItemDto> children) {
}
