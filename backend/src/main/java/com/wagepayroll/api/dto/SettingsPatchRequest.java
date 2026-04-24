package com.wagepayroll.api.dto;

import java.util.List;

public record SettingsPatchRequest(List<SettingEntryDto> entries) {
}
