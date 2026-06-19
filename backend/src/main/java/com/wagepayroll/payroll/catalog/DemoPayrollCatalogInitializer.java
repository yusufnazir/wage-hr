package com.wagepayroll.payroll.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wagepayroll.banktemplate.BankTemplateCopyService;
import com.wagepayroll.domain.org.TenantCompanyEntity;
import com.wagepayroll.domain.org.TenantCompanyRepository;
import com.wagepayroll.domain.org.TenantEmployeeEntity;
import com.wagepayroll.domain.org.TenantEmployeeRepository;
import com.wagepayroll.payrollstanding.TenantEmployeePayrollStandingProvisionService;
import com.wagepayroll.wagecomponent.WageComponentProcessingOrderService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * After migrations, ensures the demo tenant company has the same platform-derived catalog as newly created SR
 * companies: bank templates ({@link BankTemplateCopyService}) and wage components
 * ({@link DefaultPayrollCatalogProvisioningService}).
 */
@Component
@Order(100)
public class DemoPayrollCatalogInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoPayrollCatalogInitializer.class);

	public static final UUID DEMO_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
	public static final UUID DEMO_COMPANY_ID = UUID.fromString("5fa00000-0000-4000-8000-000000000001");

	private final TenantCompanyRepository companyRepository;
	private final TenantEmployeeRepository employeeRepository;
	private final BankTemplateCopyService bankTemplateCopyService;
	private final DefaultPayrollCatalogProvisioningService provisioningService;
	private final TenantEmployeePayrollStandingProvisionService payrollStandingProvisionService;
	private final DemoVariablePayStandingSeeder variablePayStandingSeeder;
	private final WageComponentProcessingOrderService processingOrderService;

	public DemoPayrollCatalogInitializer(TenantCompanyRepository companyRepository,
			TenantEmployeeRepository employeeRepository, BankTemplateCopyService bankTemplateCopyService,
			DefaultPayrollCatalogProvisioningService provisioningService,
			TenantEmployeePayrollStandingProvisionService payrollStandingProvisionService,
			DemoVariablePayStandingSeeder variablePayStandingSeeder,
			WageComponentProcessingOrderService processingOrderService) {
		this.companyRepository = companyRepository;
		this.employeeRepository = employeeRepository;
		this.bankTemplateCopyService = bankTemplateCopyService;
		this.provisioningService = provisioningService;
		this.payrollStandingProvisionService = payrollStandingProvisionService;
		this.variablePayStandingSeeder = variablePayStandingSeeder;
		this.processingOrderService = processingOrderService;
	}

	@Override
	public void run(ApplicationArguments args) {
		Optional<TenantCompanyEntity> company = companyRepository.findByIdAndTenantId(DEMO_COMPANY_ID, DEMO_TENANT_ID);
		if (company.isEmpty()) {
			return;
		}
		TenantCompanyEntity c = company.get();
		if (c.getPayrollCountry() == null || c.getPayrollCountry().isBlank()) {
			return;
		}
		log.info("Provisioning demo company catalogs for {}", DEMO_COMPANY_ID);
		bankTemplateCopyService.copyForCompany(c.getTenantId(), c.getId(), c.getPayrollCountry());
		provisioningService.provisionForCompany(c.getTenantId(), c.getId(), c.getPayrollCountry());
		int templatesAligned = processingOrderService.realignPlatformTemplatesForCountry(c.getPayrollCountry());
		int componentsAligned = processingOrderService.realignTenantCompany(DEMO_TENANT_ID, DEMO_COMPANY_ID);
		if (templatesAligned > 0 || componentsAligned > 0) {
			log.info("Aligned processing order for {} platform template(s) and {} tenant component(s)",
					templatesAligned, componentsAligned);
		}

		List<TenantEmployeeEntity> employees = employeeRepository.findByTenantIdAndCompanyIdOrderByBadgeNumberAsc(
				DEMO_TENANT_ID, DEMO_COMPANY_ID);
		if (employees.isEmpty()) {
			return;
		}
		int provisioned = payrollStandingProvisionService.syncActiveWageComponentsForCompany(DEMO_TENANT_ID,
				DEMO_COMPANY_ID, employees);
		if (provisioned > 0) {
			log.info("Auto-provisioned {} payroll standing instruction(s) for demo company employees", provisioned);
		}
		int variablePaySeeded = variablePayStandingSeeder.seedDemoCompany(DEMO_TENANT_ID, DEMO_COMPANY_ID, employees);
		if (variablePaySeeded > 0) {
			log.info(
					"Seeded {} demo payroll-input standing row(s) (vacation, bonus, child allowance, lump sum, extra earnings, overtime)",
					variablePaySeeded);
		}
	}
}
