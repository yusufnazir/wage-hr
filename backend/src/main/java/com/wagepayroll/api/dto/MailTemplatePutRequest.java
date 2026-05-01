package com.wagepayroll.api.dto;

import java.util.List;

public record MailTemplatePutRequest(String ifUpdatedAt, boolean active, List<MailTemplateLocalePutDto> locales) {
}
