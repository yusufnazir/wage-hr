package com.wagepayroll.api.dto;

import java.util.UUID;

public record PlatformWageComponentTemplatePutLedgerRequest(UUID debitPlatformLedgerTemplateId,
		UUID creditPlatformLedgerTemplateId) {
}
