package com.wagepayroll.api.dto;

import java.util.List;
import java.util.UUID;

public record TenantMaterializePayrollInputsRequest(UUID companyId, List<UUID> employeeIds) {
}
