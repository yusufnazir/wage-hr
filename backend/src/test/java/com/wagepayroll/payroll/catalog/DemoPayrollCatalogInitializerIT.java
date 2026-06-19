package com.wagepayroll.payroll.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.wagepayroll.domain.banktemplate.TenantBankTemplateRepository;
import com.wagepayroll.domain.componentgroup.TenantComponentGroupRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DemoPayrollCatalogInitializerIT {

	@Autowired
	private DemoPayrollCatalogInitializer initializer;

	@Autowired
	private TenantWageComponentRepository wageComponentRepository;

	@Autowired
	private TenantBankTemplateRepository bankTemplateRepository;

	@Autowired
	private TenantComponentGroupRepository componentGroupRepository;

	@Test
	void provisionsDemoCompanyDefaultCatalog() {
		initializer.run(null);

		long bankTemplateCount = bankTemplateRepository
				.findByTenantIdAndCompanyId(DemoPayrollCatalogInitializer.DEMO_TENANT_ID,
						DemoPayrollCatalogInitializer.DEMO_COMPANY_ID, PageRequest.of(0, 50))
				.getTotalElements();
		assertThat(bankTemplateCount).isEqualTo(11);

		long wageCount = wageComponentRepository
				.findByTenantIdAndCompanyId(DemoPayrollCatalogInitializer.DEMO_TENANT_ID,
						DemoPayrollCatalogInitializer.DEMO_COMPANY_ID, PageRequest.of(0, 100))
				.getTotalElements();
		assertThat(wageCount).isGreaterThanOrEqualTo(26);

		boolean hasDefaultGroup = componentGroupRepository.existsByTenantIdAndCompanyIdAndPlatformGroupTemplate_Id(
				DemoPayrollCatalogInitializer.DEMO_TENANT_ID, DemoPayrollCatalogInitializer.DEMO_COMPANY_ID,
				DefaultPayrollCatalogIds.SR_DEFAULT_COMPONENT_GROUP_TEMPLATE_ID);
		assertThat(hasDefaultGroup).isTrue();

		UUID baseSalaryTemplateId = UUID.fromString("51000000-0000-0000-0000-000000000001");
		assertThat(wageComponentRepository.findByTenantIdAndCompanyIdAndPlatformTemplateId(
				DemoPayrollCatalogInitializer.DEMO_TENANT_ID, DemoPayrollCatalogInitializer.DEMO_COMPANY_ID,
				baseSalaryTemplateId)).isPresent();
	}
}
