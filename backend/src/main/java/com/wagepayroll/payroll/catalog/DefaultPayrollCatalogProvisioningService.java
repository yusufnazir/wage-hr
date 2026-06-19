package com.wagepayroll.payroll.catalog;

import static com.wagepayroll.payroll.catalog.DefaultPayrollCatalogIds.SR_DEFAULT_COMPONENT_GROUP_TEMPLATE_ID;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.wagepayroll.domain.componentgroup.PlatformComponentGroupTemplateEntity;
import com.wagepayroll.domain.componentgroup.PlatformComponentGroupTemplateLocaleEntity;
import com.wagepayroll.domain.componentgroup.PlatformComponentGroupTemplateLocaleRepository;
import com.wagepayroll.domain.componentgroup.PlatformComponentGroupTemplateRepository;
import com.wagepayroll.domain.componentgroup.PlatformComponentHeaderTemplateEntity;
import com.wagepayroll.domain.componentgroup.PlatformComponentHeaderTemplateLocaleEntity;
import com.wagepayroll.domain.componentgroup.PlatformComponentHeaderTemplateLocaleRepository;
import com.wagepayroll.domain.componentgroup.PlatformComponentHeaderTemplateRepository;
import com.wagepayroll.domain.componentgroup.PlatformComponentItemTemplateEntity;
import com.wagepayroll.domain.componentgroup.PlatformComponentItemTemplateLocaleEntity;
import com.wagepayroll.domain.componentgroup.PlatformComponentItemTemplateLocaleRepository;
import com.wagepayroll.domain.componentgroup.PlatformComponentItemTemplateRepository;
import com.wagepayroll.domain.componentgroup.TenantComponentGroupEntity;
import com.wagepayroll.domain.componentgroup.TenantComponentGroupLocaleEntity;
import com.wagepayroll.domain.componentgroup.TenantComponentGroupLocaleRepository;
import com.wagepayroll.domain.componentgroup.TenantComponentGroupRepository;
import com.wagepayroll.domain.componentgroup.TenantComponentHeaderEntity;
import com.wagepayroll.domain.componentgroup.TenantComponentHeaderLocaleEntity;
import com.wagepayroll.domain.componentgroup.TenantComponentHeaderLocaleRepository;
import com.wagepayroll.domain.componentgroup.TenantComponentHeaderRepository;
import com.wagepayroll.domain.componentgroup.TenantComponentItemEntity;
import com.wagepayroll.domain.componentgroup.TenantComponentItemLocaleEntity;
import com.wagepayroll.domain.componentgroup.TenantComponentItemLocaleRepository;
import com.wagepayroll.domain.componentgroup.TenantComponentItemRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.wagecomponent.TenantWageComponentService;
import com.wagepayroll.wagecomponent.WageComponentProcessingOrderService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultPayrollCatalogProvisioningService {

	private final PlatformComponentGroupTemplateRepository platformGroupRepository;
	private final PlatformComponentGroupTemplateLocaleRepository platformGroupLocaleRepository;
	private final PlatformComponentHeaderTemplateRepository platformHeaderRepository;
	private final PlatformComponentHeaderTemplateLocaleRepository platformHeaderLocaleRepository;
	private final PlatformComponentItemTemplateRepository platformItemRepository;
	private final PlatformComponentItemTemplateLocaleRepository platformItemLocaleRepository;
	private final TenantComponentGroupRepository tenantGroupRepository;
	private final TenantComponentGroupLocaleRepository tenantGroupLocaleRepository;
	private final TenantComponentHeaderRepository tenantHeaderRepository;
	private final TenantComponentHeaderLocaleRepository tenantHeaderLocaleRepository;
	private final TenantComponentItemRepository tenantItemRepository;
	private final TenantComponentItemLocaleRepository tenantItemLocaleRepository;
	private final TenantWageComponentService tenantWageComponentService;
	private final TenantWageComponentRepository tenantWageComponentRepository;
	private final WageComponentProcessingOrderService processingOrderService;

	public DefaultPayrollCatalogProvisioningService(PlatformComponentGroupTemplateRepository platformGroupRepository,
			PlatformComponentGroupTemplateLocaleRepository platformGroupLocaleRepository,
			PlatformComponentHeaderTemplateRepository platformHeaderRepository,
			PlatformComponentHeaderTemplateLocaleRepository platformHeaderLocaleRepository,
			PlatformComponentItemTemplateRepository platformItemRepository,
			PlatformComponentItemTemplateLocaleRepository platformItemLocaleRepository,
			TenantComponentGroupRepository tenantGroupRepository,
			TenantComponentGroupLocaleRepository tenantGroupLocaleRepository,
			TenantComponentHeaderRepository tenantHeaderRepository,
			TenantComponentHeaderLocaleRepository tenantHeaderLocaleRepository,
			TenantComponentItemRepository tenantItemRepository,
			TenantComponentItemLocaleRepository tenantItemLocaleRepository,
			TenantWageComponentService tenantWageComponentService, TenantWageComponentRepository tenantWageComponentRepository,
			WageComponentProcessingOrderService processingOrderService) {
		this.platformGroupRepository = platformGroupRepository;
		this.platformGroupLocaleRepository = platformGroupLocaleRepository;
		this.platformHeaderRepository = platformHeaderRepository;
		this.platformHeaderLocaleRepository = platformHeaderLocaleRepository;
		this.platformItemRepository = platformItemRepository;
		this.platformItemLocaleRepository = platformItemLocaleRepository;
		this.tenantGroupRepository = tenantGroupRepository;
		this.tenantGroupLocaleRepository = tenantGroupLocaleRepository;
		this.tenantHeaderRepository = tenantHeaderRepository;
		this.tenantHeaderLocaleRepository = tenantHeaderLocaleRepository;
		this.tenantItemRepository = tenantItemRepository;
		this.tenantItemLocaleRepository = tenantItemLocaleRepository;
		this.tenantWageComponentService = tenantWageComponentService;
		this.tenantWageComponentRepository = tenantWageComponentRepository;
		this.processingOrderService = processingOrderService;
	}

	@Transactional
	public void provisionForCompany(UUID tenantId, UUID companyId, String payrollCountry) {
		if (tenantId == null || companyId == null || payrollCountry == null || payrollCountry.isBlank()) {
			return;
		}
		String country = payrollCountry.trim().toUpperCase(Locale.ROOT);
		PlatformComponentGroupTemplateEntity platformGroup = platformGroupRepository
				.findById(SR_DEFAULT_COMPONENT_GROUP_TEMPLATE_ID)
				.filter(PlatformComponentGroupTemplateEntity::isActive)
				.orElse(null);
		if (platformGroup == null) {
			return;
		}
		if (!country.equalsIgnoreCase(platformGroup.getCountry().getIsoAlpha2())) {
			return;
		}
		Set<UUID> platformTemplateIds = collectPlatformWageTemplateIds(platformGroup.getId());
		Map<UUID, UUID> tenantWageByPlatformTemplate = new HashMap<>();
		for (UUID platformTemplateId : platformTemplateIds) {
			UUID tenantWageId = tenantWageComponentService.provisionFromPlatformTemplateIfAbsent(tenantId, companyId,
					platformTemplateId);
			tenantWageByPlatformTemplate.put(platformTemplateId, tenantWageId);
		}
		if (!tenantGroupRepository.existsByTenantIdAndCompanyIdAndPlatformGroupTemplate_Id(tenantId, companyId,
				platformGroup.getId())) {
			copyComponentGroupStructure(tenantId, companyId, platformGroup, tenantWageByPlatformTemplate);
		}
		processingOrderService.realignPlatformTemplatesForCountry(country);
		processingOrderService.realignTenantCompany(tenantId, companyId);
	}

	private Set<UUID> collectPlatformWageTemplateIds(UUID platformGroupId) {
		Set<UUID> ids = new LinkedHashSet<>();
		List<PlatformComponentHeaderTemplateEntity> headers = platformHeaderRepository
				.findByGroup_IdOrderBySortOrderAscIdAsc(platformGroupId);
		for (PlatformComponentHeaderTemplateEntity header : headers) {
			List<PlatformComponentItemTemplateEntity> items = platformItemRepository
					.findByHeader_IdOrderBySortOrderAscIdAsc(header.getId());
			for (PlatformComponentItemTemplateEntity item : items) {
				ids.add(item.getPlatformWageComponentTemplateId());
			}
		}
		return ids;
	}

	private void copyComponentGroupStructure(UUID tenantId, UUID companyId, PlatformComponentGroupTemplateEntity platformGroup,
			Map<UUID, UUID> tenantWageByPlatformTemplate) {
		Instant now = Instant.now();
		TenantComponentGroupEntity tenantGroup = new TenantComponentGroupEntity();
		tenantGroup.setId(UUID.randomUUID());
		tenantGroup.setTenantId(tenantId);
		tenantGroup.setCompanyId(companyId);
		tenantGroup.setPlatformGroupTemplate(platformGroup);
		tenantGroup.setSortOrder(platformGroup.getSortOrder());
		tenantGroup.setActive(true);
		tenantGroup.setCreatedAt(now);
		tenantGroup.setUpdatedAt(now);
		tenantGroupRepository.save(tenantGroup);
		copyGroupLocales(platformGroup.getId(), tenantGroup.getId());

		List<PlatformComponentHeaderTemplateEntity> platformHeaders = platformHeaderRepository
				.findByGroup_IdOrderBySortOrderAscIdAsc(platformGroup.getId());
		for (PlatformComponentHeaderTemplateEntity platformHeader : platformHeaders) {
			TenantComponentHeaderEntity tenantHeader = new TenantComponentHeaderEntity();
			tenantHeader.setId(UUID.randomUUID());
			tenantHeader.setGroup(tenantGroup);
			tenantHeader.setSortOrder(platformHeader.getSortOrder());
			tenantHeader.setCreatedAt(now);
			tenantHeader.setUpdatedAt(now);
			tenantHeaderRepository.save(tenantHeader);
			copyHeaderLocales(platformHeader.getId(), tenantHeader.getId());

			List<PlatformComponentItemTemplateEntity> platformItems = platformItemRepository
					.findByHeader_IdOrderBySortOrderAscIdAsc(platformHeader.getId());
			for (PlatformComponentItemTemplateEntity platformItem : platformItems) {
				UUID platformTemplateId = platformItem.getPlatformWageComponentTemplateId();
				UUID tenantWageId = tenantWageByPlatformTemplate.get(platformTemplateId);
				if (tenantWageId == null) {
					continue;
				}
				TenantComponentItemEntity tenantItem = new TenantComponentItemEntity();
				tenantItem.setId(UUID.randomUUID());
				tenantItem.setHeader(tenantHeader);
				tenantItem.setWageComponent(tenantWageComponentRepository.getReferenceById(tenantWageId));
				tenantItem.setSortOrder(platformItem.getSortOrder());
				tenantItem.setCreatedAt(now);
				tenantItem.setUpdatedAt(now);
				tenantItemRepository.save(tenantItem);
				copyItemLocales(platformItem.getId(), tenantItem.getId());
			}
		}
	}

	private void copyGroupLocales(UUID platformGroupId, UUID tenantGroupId) {
		List<PlatformComponentGroupTemplateLocaleEntity> rows = platformGroupLocaleRepository
				.findByPlatformComponentGroupTemplateIdIn(List.of(platformGroupId));
		for (PlatformComponentGroupTemplateLocaleEntity row : rows) {
			TenantComponentGroupLocaleEntity loc = new TenantComponentGroupLocaleEntity();
			loc.setId(UUID.randomUUID());
			loc.setTenantComponentGroupId(tenantGroupId);
			loc.setLocale(row.getLocale());
			loc.setName(row.getName());
			loc.setDescription(row.getDescription());
			tenantGroupLocaleRepository.save(loc);
		}
	}

	private void copyHeaderLocales(UUID platformHeaderId, UUID tenantHeaderId) {
		List<PlatformComponentHeaderTemplateLocaleEntity> rows = platformHeaderLocaleRepository
				.findByPlatformComponentHeaderTemplateIdIn(List.of(platformHeaderId));
		for (PlatformComponentHeaderTemplateLocaleEntity row : rows) {
			TenantComponentHeaderLocaleEntity loc = new TenantComponentHeaderLocaleEntity();
			loc.setId(UUID.randomUUID());
			loc.setTenantComponentHeaderId(tenantHeaderId);
			loc.setLocale(row.getLocale());
			loc.setName(row.getName());
			loc.setDescription(row.getDescription());
			tenantHeaderLocaleRepository.save(loc);
		}
	}

	private void copyItemLocales(UUID platformItemId, UUID tenantItemId) {
		List<PlatformComponentItemTemplateLocaleEntity> rows = platformItemLocaleRepository
				.findByPlatformComponentItemTemplateIdIn(List.of(platformItemId));
		for (PlatformComponentItemTemplateLocaleEntity row : rows) {
			TenantComponentItemLocaleEntity loc = new TenantComponentItemLocaleEntity();
			loc.setId(UUID.randomUUID());
			loc.setTenantComponentItemId(tenantItemId);
			loc.setLocale(row.getLocale());
			loc.setName(row.getName());
			loc.setDescription(row.getDescription());
			tenantItemLocaleRepository.save(loc);
		}
	}
}
