package com.wagepayroll.api.dto;

public record MailTemplateLocalePutDto(String locale, String subject, String bodyHtml) {
}
