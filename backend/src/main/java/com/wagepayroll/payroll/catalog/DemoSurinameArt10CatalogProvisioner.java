package com.wagepayroll.payroll.catalog;

import java.util.Set;
import java.util.UUID;

import com.wagepayroll.domain.wagecomponent.PlatformWageComponentTemplateRepository;
import com.wagepayroll.wagecomponent.TenantWageComponentService;
import com.wagepayroll.wagecomponent.WageComponentProcessingOrderService;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisions Art. 10 P2/P4 platform wage component templates (1049–1057, 1058–1065) on the demo company.
 * These templates are outside the SR default component group; seeders need tenant rows before standing input.
 */
@Component
public class DemoSurinameArt10CatalogProvisioner {

	static final Set<String> ART10_TEMPLATE_CODES = Set.of("1049", "1050", "1051", "1052", "1053", "1054", "1057",
			"1058", "1059", "1060", "1061", "1062", "1063", "1064", "1065");

	private final PlatformWageComponentTemplateRepository templateRepository;
	private final TenantWageComponentService tenantWageComponentService;
	private final WageComponentProcessingOrderService processingOrderService;

	public DemoSurinameArt10CatalogProvisioner(PlatformWageComponentTemplateRepository templateRepository,
			TenantWageComponentService tenantWageComponentService,
			WageComponentProcessingOrderService processingOrderService) {
		this.templateRepository = templateRepository;
		this.tenantWageComponentService = tenantWageComponentService;
		this.processingOrderService = processingOrderService;
	}

	@Transactional
	public int provisionForCompany(UUID tenantId, UUID companyId, String payrollCountry) {
		if (tenantId == null || companyId == null || payrollCountry == null || payrollCountry.isBlank()) {
			return 0;
		}
		String country = payrollCountry.trim().toUpperCase();
		int provisioned = 0;
		for (var template : templateRepository.findByCountryCodeAndActiveIsTrueOrderByTemplateCodeAsc(country)) {
			if (!ART10_TEMPLATE_CODES.contains(template.getTemplateCode())) {
				continue;
			}
			tenantWageComponentService.provisionFromPlatformTemplateIfAbsent(tenantId, companyId, template.getId());
			provisioned++;
		}
		if (provisioned > 0) {
			processingOrderService.realignTenantCompany(tenantId, companyId);
		}
		return provisioned;
	}
}
