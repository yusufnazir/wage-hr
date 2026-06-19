package com.wagepayroll.wagecomponent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.wagepayroll.api.dto.PlatformWageComponentTemplateDependencyPutItem;
import com.wagepayroll.api.dto.PlatformWageComponentTemplateDependencyRowDto;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentTemplateDependencyEntity;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentTemplateDependencyRepository;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentTemplateEntity;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentTemplateRepository;
import com.wagepayroll.payroll.engine.ComponentDependencyValidation;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformWageComponentTemplateDependencyService {

	private final PlatformWageComponentTemplateDependencyRepository dependencyRepository;
	private final PlatformWageComponentTemplateRepository templateRepository;

	public PlatformWageComponentTemplateDependencyService(
			PlatformWageComponentTemplateDependencyRepository dependencyRepository,
			PlatformWageComponentTemplateRepository templateRepository) {
		this.dependencyRepository = dependencyRepository;
		this.templateRepository = templateRepository;
	}

	@Transactional(readOnly = true)
	public List<PlatformWageComponentTemplateDependencyRowDto> listForTemplate(UUID templateId) {
		return mapByTemplateIds(List.of(templateId)).getOrDefault(templateId, List.of());
	}

	@Transactional(readOnly = true)
	public Map<UUID, List<PlatformWageComponentTemplateDependencyRowDto>> mapByTemplateIds(Collection<UUID> templateIds) {
		if (templateIds == null || templateIds.isEmpty()) {
			return Map.of();
		}
		List<UUID> ids = templateIds.stream().distinct().toList();
		List<PlatformWageComponentTemplateDependencyEntity> rows = dependencyRepository.findTouchingTemplates(ids);
		List<UUID> prerequisiteTemplateIds = rows.stream().map(PlatformWageComponentTemplateDependencyEntity::getDependsOnTemplateId)
				.distinct().toList();
		Map<UUID, PlatformWageComponentTemplateEntity> templatesById = templateRepository.findAllById(prerequisiteTemplateIds)
				.stream()
				.collect(Collectors.toMap(PlatformWageComponentTemplateEntity::getId, t -> t));
		Map<UUID, List<PlatformWageComponentTemplateDependencyRowDto>> out = new HashMap<>();
		for (PlatformWageComponentTemplateDependencyEntity row : rows) {
			if (!ids.contains(row.getPlatformWageComponentTemplateId())) {
				continue;
			}
			PlatformWageComponentTemplateEntity prereq = templatesById.get(row.getDependsOnTemplateId());
			String code = prereq != null ? prereq.getTemplateCode() : "";
			String name = prereq != null ? prereq.getName() : "";
			out.computeIfAbsent(row.getPlatformWageComponentTemplateId(), k -> new ArrayList<>())
					.add(new PlatformWageComponentTemplateDependencyRowDto(row.getId(), row.getDependsOnTemplateId(),
							code, name));
		}
		for (List<PlatformWageComponentTemplateDependencyRowDto> list : out.values()) {
			list.sort(java.util.Comparator.comparing(PlatformWageComponentTemplateDependencyRowDto::dependsOnTemplateCode));
		}
		return out;
	}

	@Transactional(readOnly = true)
	public Set<String> prerequisiteTemplateCodesFor(UUID dependentTemplateId) {
		return listForTemplate(dependentTemplateId).stream()
				.map(PlatformWageComponentTemplateDependencyRowDto::dependsOnTemplateCode)
				.filter(c -> c != null && !c.isBlank())
				.collect(Collectors.toSet());
	}

	@Transactional
	public List<PlatformWageComponentTemplateDependencyRowDto> replaceForTemplate(UUID dependentTemplateId,
			List<PlatformWageComponentTemplateDependencyPutItem> items) {
		PlatformWageComponentTemplateEntity dependent = templateRepository.findById(dependentTemplateId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		List<PlatformWageComponentTemplateDependencyPutItem> normalized = items == null ? List.of() : items.stream()
				.filter(i -> i != null && i.dependsOnTemplateId() != null)
				.toList();
		Set<UUID> prerequisiteIds = new HashSet<>();
		for (PlatformWageComponentTemplateDependencyPutItem item : normalized) {
			if (!prerequisiteIds.add(item.dependsOnTemplateId())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DUPLICATE_DEPENDENCY");
			}
			if (dependentTemplateId.equals(item.dependsOnTemplateId())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DEPENDENCY_SELF_LOOP");
			}
			PlatformWageComponentTemplateEntity prereq = templateRepository.findById(item.dependsOnTemplateId())
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "DEPENDENCY_UNKNOWN_TEMPLATE"));
			if (!dependent.getCountryCode().equalsIgnoreCase(prereq.getCountryCode())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DEPENDENCY_COUNTRY_MISMATCH");
			}
		}
		assertCountryTemplateGraphAcyclic(dependent.getCountryCode(), dependentTemplateId, prerequisiteIds);
		dependencyRepository.deleteByPlatformWageComponentTemplateId(dependentTemplateId);
		Instant now = Instant.now();
		for (UUID prerequisiteId : prerequisiteIds) {
			PlatformWageComponentTemplateDependencyEntity row = new PlatformWageComponentTemplateDependencyEntity();
			row.setId(UUID.randomUUID());
			row.setPlatformWageComponentTemplateId(dependentTemplateId);
			row.setDependsOnTemplateId(prerequisiteId);
			row.setCreatedAt(now);
			row.setUpdatedAt(now);
			dependencyRepository.save(row);
		}
		return listForTemplate(dependentTemplateId);
	}

	private void assertCountryTemplateGraphAcyclic(String countryCode, UUID dependentTemplateId, Set<UUID> newPrerequisites) {
		List<PlatformWageComponentTemplateEntity> countryTemplates = templateRepository
				.findByCountryCodeAndActiveIsTrueOrderByTemplateCodeAsc(countryCode);
		Set<UUID> countryTemplateIds = countryTemplates.stream().map(PlatformWageComponentTemplateEntity::getId)
				.collect(Collectors.toSet());
		List<PlatformWageComponentTemplateDependencyEntity> allEdges = dependencyRepository
				.findTouchingTemplates(countryTemplateIds);
		Map<UUID, Set<UUID>> prerequisiteToDependents = new HashMap<>();
		for (PlatformWageComponentTemplateDependencyEntity edge : allEdges) {
			if (!countryTemplateIds.contains(edge.getPlatformWageComponentTemplateId())
					|| !countryTemplateIds.contains(edge.getDependsOnTemplateId())) {
				continue;
			}
			if (edge.getPlatformWageComponentTemplateId().equals(dependentTemplateId)) {
				continue;
			}
			prerequisiteToDependents.computeIfAbsent(edge.getDependsOnTemplateId(), k -> new HashSet<>())
					.add(edge.getPlatformWageComponentTemplateId());
		}
		for (UUID prerequisiteId : newPrerequisites) {
			prerequisiteToDependents.computeIfAbsent(prerequisiteId, k -> new HashSet<>()).add(dependentTemplateId);
		}
		if (ComponentDependencyValidation.hasCycle(prerequisiteToDependents, countryTemplateIds)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DEPENDENCY_CYCLE");
		}
	}
}
