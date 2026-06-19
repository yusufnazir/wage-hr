package com.wagepayroll.payroll.base;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wagepayroll.domain.wagecomponent.PlatformWageComponentTemplateDependencyEntity;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentTemplateDependencyRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentDependencyEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentDependencyRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;

@Service
public class ComponentDependencyCopyService {

	private final PlatformWageComponentTemplateDependencyRepository templateDependencyRepository;
	private final TenantWageComponentDependencyRepository tenantDependencyRepository;
	private final TenantWageComponentRepository tenantWageComponentRepository;

	public ComponentDependencyCopyService(PlatformWageComponentTemplateDependencyRepository templateDependencyRepository,
			TenantWageComponentDependencyRepository tenantDependencyRepository,
			TenantWageComponentRepository tenantWageComponentRepository) {
		this.templateDependencyRepository = templateDependencyRepository;
		this.tenantDependencyRepository = tenantDependencyRepository;
		this.tenantWageComponentRepository = tenantWageComponentRepository;
	}

	@Transactional
	public void copyTemplateDependenciesToTenantComponent(UUID tenantId, UUID companyId, UUID platformTemplateId,
			UUID tenantWageComponentId) {
		List<PlatformWageComponentTemplateDependencyEntity> templateEdges = templateDependencyRepository
				.findByPlatformWageComponentTemplateId(platformTemplateId);
		if (templateEdges.isEmpty()) {
			return;
		}
		Instant now = Instant.now();
		for (PlatformWageComponentTemplateDependencyEntity edge : templateEdges) {
			tenantWageComponentRepository
					.findByTenantIdAndCompanyIdAndPlatformTemplateId(tenantId, companyId, edge.getDependsOnTemplateId())
					.ifPresent(prerequisite -> {
						if (tenantDependencyRepository.existsByTenantIdAndTenantWageComponentIdAndDependsOnTenantWageComponentId(
								tenantId, tenantWageComponentId, prerequisite.getId())) {
							return;
						}
						TenantWageComponentDependencyEntity row = new TenantWageComponentDependencyEntity();
						row.setId(UUID.randomUUID());
						row.setTenantId(tenantId);
						row.setTenantWageComponentId(tenantWageComponentId);
						row.setDependsOnTenantWageComponentId(prerequisite.getId());
						row.setCreatedAt(now);
						row.setUpdatedAt(now);
						tenantDependencyRepository.save(row);
					});
		}
	}
}
