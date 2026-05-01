package com.wagepayroll.mail;

/** Ephemeral result of rendering a row from {@code mail_template_locale} with placeholders applied. */
public record RenderedCatalogEmail(String subject, String htmlBody, String textBody) {
}
