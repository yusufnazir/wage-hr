package com.wagepayroll.api.dto;

import java.util.UUID;

public record MailTemplateListItemDto(UUID id, String code, String contentVersion, boolean active, String updatedAt) {
}
