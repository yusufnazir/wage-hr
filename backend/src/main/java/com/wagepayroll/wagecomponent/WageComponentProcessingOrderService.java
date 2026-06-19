package com.wagepayroll.wagecomponent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentTemplateEntity;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentTemplateRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.payroll.model.WageComponentSortOrder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies {@link WageComponentSortOrder} to platform templates and tenant wage components.
 */
@Service
public class WageComponentProcessingOrderService {

	private final ObjectMapper objectMapper;
	private final PlatformWageComponentTemplateRepository templateRepository;
	private final TenantWageComponentRepository tenantWageComponentRepository;

	public WageComponentProcessingOrderService(ObjectMapper objectMapper,
			PlatformWageComponentTemplateRepository templateRepository,
			TenantWageComponentRepository tenantWageComponentRepository) {
		this.objectMapper = objectMapper;
		this.templateRepository = templateRepository;
		this.tenantWageComponentRepository = tenantWageComponentRepository;
	}

	@Transactional
	public int realignPlatformTemplatesForCountry(String countryCode) {
		if (countryCode == null || countryCode.isBlank()) {
			return 0;
		}
		List<PlatformWageComponentTemplateEntity> templates = templateRepository
				.findByCountryCodeAndActiveIsTrueOrderByTemplateCodeAsc(countryCode.trim().toUpperCase());
		Instant now = Instant.now();
		int updated = 0;
		for (PlatformWageComponentTemplateEntity template : templates) {
			int order = WageComponentSortOrder.forTemplateCode(template.getTemplateCode());
			if (template.getProcessingOrderHint() != null && template.getProcessingOrderHint() == order
					&& jsonOrderMatches(template.getDefinitionDefaultsJson(), order)) {
				continue;
			}
			template.setProcessingOrderHint(order);
			template.setDefinitionDefaultsJson(patchJsonProcessingOrder(template.getDefinitionDefaultsJson(), order));
			template.setUpdatedAt(now);
			templateRepository.save(template);
			updated++;
		}
		return updated;
	}

	@Transactional
	public int realignTenantCompany(UUID tenantId, UUID companyId) {
		List<TenantWageComponentEntity> components = tenantWageComponentRepository
				.findByTenantIdAndCompanyId(tenantId, companyId, org.springframework.data.domain.Pageable.unpaged())
				.getContent();
		Instant now = Instant.now();
		int updated = 0;
		for (TenantWageComponentEntity component : components) {
			String templateCode = null;
			if (component.getPlatformTemplateId() != null) {
				templateCode = templateRepository.findById(component.getPlatformTemplateId())
						.map(PlatformWageComponentTemplateEntity::getTemplateCode)
						.orElse(null);
			}
			int order = WageComponentSortOrder.resolve(component.getComponentType(), component.getPhase(),
					component.getCategory(), component.isTaxableWageTax(), templateCode, component.getProcessingOrder());
			if (component.getProcessingOrder() == order) {
				continue;
			}
			component.setProcessingOrder(order);
			component.setUpdatedAt(now);
			tenantWageComponentRepository.save(component);
			updated++;
		}
		return updated;
	}

	private boolean jsonOrderMatches(String json, int expected) {
		try {
			JsonNode root = objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
			return root.has("processingOrder") && root.get("processingOrder").asInt() == expected;
		}
		catch (Exception ex) {
			return false;
		}
	}

	public String patchJsonProcessingOrder(String json, int order) {
		try {
			JsonNode root = objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
			if (root instanceof ObjectNode objectNode) {
				objectNode.put("processingOrder", order);
				return objectMapper.writeValueAsString(objectNode);
			}
		}
		catch (Exception ignored) {
			// fall through
		}
		return "{\"processingOrder\":" + order + "}";
	}
}
