package com.wagepayroll.componentgroup;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.wagepayroll.api.dto.PlatformComponentTranslationDto;
import com.wagepayroll.api.dto.TenantComponentGroupCreateRequest;
import com.wagepayroll.api.dto.TenantComponentGroupPutRequest;
import com.wagepayroll.api.dto.TenantComponentGroupRowDto;
import com.wagepayroll.api.dto.TenantComponentHeaderCreateRequest;
import com.wagepayroll.api.dto.TenantComponentHeaderPutRequest;
import com.wagepayroll.api.dto.TenantComponentHeaderRowDto;
import com.wagepayroll.api.dto.TenantComponentItemCreateRequest;
import com.wagepayroll.api.dto.TenantComponentItemPutRequest;
import com.wagepayroll.api.dto.TenantComponentItemRowDto;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.componentgroup.ComponentGroupingValidation.NameDescriptionPair;
import com.wagepayroll.domain.componentgroup.PlatformComponentGroupTemplateEntity;
import com.wagepayroll.domain.componentgroup.PlatformComponentGroupTemplateRepository;
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
import com.wagepayroll.domain.org.TenantCompanyEntity;
import com.wagepayroll.domain.org.TenantCompanyRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantComponentGroupService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final Set<String> SUPPORTED_READ_LOCALES = Set.of("en", "nl");

	private final TenantComponentGroupRepository groupRepository;
	private final TenantComponentGroupLocaleRepository groupLocaleRepository;
	private final TenantComponentHeaderRepository headerRepository;
	private final TenantComponentHeaderLocaleRepository headerLocaleRepository;
	private final TenantComponentItemRepository itemRepository;
	private final TenantComponentItemLocaleRepository itemLocaleRepository;
	private final TenantCompanyRepository tenantCompanyRepository;
	private final TenantWageComponentRepository tenantWageComponentRepository;
	private final PlatformComponentGroupTemplateRepository platformGroupTemplateRepository;
	private final AuditService auditService;

	public TenantComponentGroupService(TenantComponentGroupRepository groupRepository,
			TenantComponentGroupLocaleRepository groupLocaleRepository, TenantComponentHeaderRepository headerRepository,
			TenantComponentHeaderLocaleRepository headerLocaleRepository, TenantComponentItemRepository itemRepository,
			TenantComponentItemLocaleRepository itemLocaleRepository, TenantCompanyRepository tenantCompanyRepository,
			TenantWageComponentRepository tenantWageComponentRepository,
			PlatformComponentGroupTemplateRepository platformGroupTemplateRepository, AuditService auditService) {
		this.groupRepository = groupRepository;
		this.groupLocaleRepository = groupLocaleRepository;
		this.headerRepository = headerRepository;
		this.headerLocaleRepository = headerLocaleRepository;
		this.itemRepository = itemRepository;
		this.itemLocaleRepository = itemLocaleRepository;
		this.tenantCompanyRepository = tenantCompanyRepository;
		this.tenantWageComponentRepository = tenantWageComponentRepository;
		this.platformGroupTemplateRepository = platformGroupTemplateRepository;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> listGroups(UUID tenantId, UUID companyId, int page, int size, Boolean activeFilter,
			String localeRaw) {
		requireCompany(tenantId, companyId);
		String locale = normalizeReadLocale(localeRaw);
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int safePage = Math.max(page, 0);
		Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id")));
		Page<TenantComponentGroupEntity> p = activeFilter == null
				? groupRepository.findByTenantIdAndCompanyIdOrderBySortOrderAscIdAsc(tenantId, companyId, pageable)
				: groupRepository.findByTenantIdAndCompanyIdAndActiveOrderBySortOrderAscIdAsc(tenantId, companyId,
						activeFilter.booleanValue(), pageable);
		List<UUID> ids = p.getContent().stream().map(TenantComponentGroupEntity::getId).toList();
		Map<UUID, List<TenantComponentGroupLocaleEntity>> locales = loadGroupLocales(ids);
		List<TenantComponentGroupRowDto> items = p.getContent().stream()
				.map(g -> toGroupRow(g, locales.getOrDefault(g.getId(), List.of()), locale))
				.toList();
		return pagePayload(items, p);
	}

	@Transactional(readOnly = true)
	public TenantComponentGroupRowDto getGroup(UUID tenantId, UUID companyId, UUID id, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		TenantComponentGroupEntity g = requireGroup(tenantId, companyId, id);
		List<TenantComponentGroupLocaleEntity> loc = groupLocaleRepository.findByTenantComponentGroupIdIn(List.of(id));
		return toGroupRow(g, loc, locale);
	}

	@Transactional
	public TenantComponentGroupRowDto createGroup(UUID tenantId, TenantComponentGroupCreateRequest body, UUID actorId,
			String correlationId, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		TenantCompanyEntity company = requireCompany(tenantId, body.companyId());
		Map<String, NameDescriptionPair> tr = ComponentGroupingValidation.normalizeTranslations(body.translations());
		PlatformComponentGroupTemplateEntity template = null;
		if (body.platformComponentGroupTemplateId() != null) {
			template = platformGroupTemplateRepository.findById(body.platformComponentGroupTemplateId())
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "GROUP_TEMPLATE_NOT_FOUND"));
			if (!company.getPayrollCountry().equalsIgnoreCase(template.getCountry().getIsoAlpha2())) {
				throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "GROUP_TEMPLATE_COUNTRY_MISMATCH");
			}
		}
		Instant now = Instant.now();
		TenantComponentGroupEntity g = new TenantComponentGroupEntity();
		g.setId(UUID.randomUUID());
		g.setTenantId(tenantId);
		g.setCompanyId(body.companyId());
		g.setPlatformGroupTemplate(template);
		g.setSortOrder(body.sortOrder() == null ? 0 : body.sortOrder().intValue());
		g.setActive(body.active() == null || body.active().booleanValue());
		g.setCreatedAt(now);
		g.setUpdatedAt(now);
		groupRepository.save(g);
		saveGroupLocales(g.getId(), tr);
		auditService.append(tenantId, actorId, AuditActionCodes.TENANT_COMPONENT_GROUP_CREATED,
				AuditResourceTypes.TENANT_COMPONENT_GROUP, g.getId().toString(), correlationId,
				Map.of("companyId", body.companyId().toString()));
		return getGroup(tenantId, body.companyId(), g.getId(), locale);
	}

	@Transactional
	public TenantComponentGroupRowDto updateGroup(UUID tenantId, UUID companyId, UUID id, TenantComponentGroupPutRequest body,
			UUID actorId, String correlationId, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		TenantComponentGroupEntity g = requireGroup(tenantId, companyId, id);
		Map<String, NameDescriptionPair> tr = ComponentGroupingValidation.normalizeTranslations(body.translations());
		g.setSortOrder(body.sortOrder().intValue());
		g.setActive(body.active().booleanValue());
		g.setUpdatedAt(Instant.now());
		groupRepository.save(g);
		saveGroupLocales(id, tr);
		auditService.append(tenantId, actorId, AuditActionCodes.TENANT_COMPONENT_GROUP_UPDATED,
				AuditResourceTypes.TENANT_COMPONENT_GROUP, id.toString(), correlationId, Map.of("id", id.toString()));
		return toGroupRow(g, groupLocaleRepository.findByTenantComponentGroupIdIn(List.of(id)), locale);
	}

	@Transactional
	public void deleteGroup(UUID tenantId, UUID companyId, UUID id, UUID actorId, String correlationId) {
		requireGroup(tenantId, companyId, id);
		groupRepository.deleteById(id);
		auditService.append(tenantId, actorId, AuditActionCodes.TENANT_COMPONENT_GROUP_DELETED,
				AuditResourceTypes.TENANT_COMPONENT_GROUP, id.toString(), correlationId, Map.of("id", id.toString()));
	}

	@Transactional(readOnly = true)
	public Map<String, Object> listHeaders(UUID tenantId, UUID companyId, UUID groupId, int page, int size, String localeRaw) {
		requireGroup(tenantId, companyId, groupId);
		String locale = normalizeReadLocale(localeRaw);
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int safePage = Math.max(page, 0);
		Page<TenantComponentHeaderEntity> p = headerRepository.findByGroup_IdOrderBySortOrderAscIdAsc(groupId,
				PageRequest.of(safePage, safeSize));
		List<UUID> ids = p.getContent().stream().map(TenantComponentHeaderEntity::getId).toList();
		Map<UUID, List<TenantComponentHeaderLocaleEntity>> locales = loadHeaderLocales(ids);
		List<TenantComponentHeaderRowDto> items = p.getContent().stream()
				.map(h -> toHeaderRow(h, locales.getOrDefault(h.getId(), List.of()), locale))
				.toList();
		return pagePayload(items, p);
	}

	@Transactional(readOnly = true)
	public TenantComponentHeaderRowDto getHeader(UUID tenantId, UUID companyId, UUID groupId, UUID headerId, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		TenantComponentHeaderEntity h = requireHeader(tenantId, companyId, groupId, headerId);
		List<TenantComponentHeaderLocaleEntity> loc = headerLocaleRepository.findByTenantComponentHeaderIdIn(List.of(headerId));
		return toHeaderRow(h, loc, locale);
	}

	@Transactional
	public TenantComponentHeaderRowDto createHeader(UUID tenantId, UUID companyId, UUID groupId,
			TenantComponentHeaderCreateRequest body, UUID actorId, String correlationId, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		requireGroup(tenantId, companyId, groupId);
		Map<String, NameDescriptionPair> tr = ComponentGroupingValidation.normalizeTranslations(body.translations());
		Instant now = Instant.now();
		TenantComponentHeaderEntity h = new TenantComponentHeaderEntity();
		h.setId(UUID.randomUUID());
		h.setGroup(groupRepository.getReferenceById(groupId));
		h.setSortOrder(body.sortOrder() == null ? 0 : body.sortOrder().intValue());
		h.setCreatedAt(now);
		h.setUpdatedAt(now);
		headerRepository.save(h);
		saveHeaderLocales(h.getId(), tr);
		auditService.append(tenantId, actorId, AuditActionCodes.TENANT_COMPONENT_HEADER_CREATED,
				AuditResourceTypes.TENANT_COMPONENT_HEADER, h.getId().toString(), correlationId,
				Map.of("groupId", groupId.toString()));
		return getHeader(tenantId, companyId, groupId, h.getId(), locale);
	}

	@Transactional
	public TenantComponentHeaderRowDto updateHeader(UUID tenantId, UUID companyId, UUID groupId, UUID headerId,
			TenantComponentHeaderPutRequest body, UUID actorId, String correlationId, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		TenantComponentHeaderEntity h = requireHeader(tenantId, companyId, groupId, headerId);
		Map<String, NameDescriptionPair> tr = ComponentGroupingValidation.normalizeTranslations(body.translations());
		h.setSortOrder(body.sortOrder().intValue());
		h.setUpdatedAt(Instant.now());
		headerRepository.save(h);
		saveHeaderLocales(headerId, tr);
		auditService.append(tenantId, actorId, AuditActionCodes.TENANT_COMPONENT_HEADER_UPDATED,
				AuditResourceTypes.TENANT_COMPONENT_HEADER, headerId.toString(), correlationId,
				Map.of("groupId", groupId.toString()));
		return toHeaderRow(h, headerLocaleRepository.findByTenantComponentHeaderIdIn(List.of(headerId)), locale);
	}

	@Transactional
	public void deleteHeader(UUID tenantId, UUID companyId, UUID groupId, UUID headerId, UUID actorId, String correlationId) {
		requireHeader(tenantId, companyId, groupId, headerId);
		headerRepository.deleteById(headerId);
		auditService.append(tenantId, actorId, AuditActionCodes.TENANT_COMPONENT_HEADER_DELETED,
				AuditResourceTypes.TENANT_COMPONENT_HEADER, headerId.toString(), correlationId,
				Map.of("groupId", groupId.toString()));
	}

	@Transactional(readOnly = true)
	public Map<String, Object> listItems(UUID tenantId, UUID companyId, UUID groupId, UUID headerId, int page, int size,
			String localeRaw) {
		requireHeader(tenantId, companyId, groupId, headerId);
		String locale = normalizeReadLocale(localeRaw);
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int safePage = Math.max(page, 0);
		Page<TenantComponentItemEntity> p = itemRepository.findByHeader_IdOrderBySortOrderAscIdAsc(headerId,
				PageRequest.of(safePage, safeSize));
		List<UUID> ids = p.getContent().stream().map(TenantComponentItemEntity::getId).toList();
		Map<UUID, List<TenantComponentItemLocaleEntity>> locales = loadItemLocales(ids);
		List<TenantComponentItemRowDto> items = p.getContent().stream()
				.map(it -> toItemRow(it, locales.getOrDefault(it.getId(), List.of()), locale))
				.toList();
		return pagePayload(items, p);
	}

	@Transactional(readOnly = true)
	public TenantComponentItemRowDto getItem(UUID tenantId, UUID companyId, UUID groupId, UUID headerId, UUID itemId,
			String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		TenantComponentItemEntity it = requireItem(tenantId, companyId, groupId, headerId, itemId);
		List<TenantComponentItemLocaleEntity> loc = itemLocaleRepository.findByTenantComponentItemIdIn(List.of(itemId));
		return toItemRow(it, loc, locale);
	}

	@Transactional
	public TenantComponentItemRowDto createItem(UUID tenantId, UUID companyId, UUID groupId, UUID headerId,
			TenantComponentItemCreateRequest body, UUID actorId, String correlationId, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		requireHeader(tenantId, companyId, groupId, headerId);
		TenantWageComponentEntity wage = validateTenantWageComponent(tenantId, companyId, body.tenantWageComponentId());
		if (itemRepository.existsByHeader_IdAndWageComponent_Id(headerId, wage.getId())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "COMPONENT_ITEM_DUPLICATE_WAGE_COMPONENT");
		}
		Map<String, NameDescriptionPair> tr = ComponentGroupingValidation.normalizeTranslations(body.translations());
		Instant now = Instant.now();
		TenantComponentItemEntity it = new TenantComponentItemEntity();
		it.setId(UUID.randomUUID());
		it.setHeader(headerRepository.getReferenceById(headerId));
		it.setWageComponent(wage);
		it.setSortOrder(body.sortOrder() == null ? 0 : body.sortOrder().intValue());
		it.setCreatedAt(now);
		it.setUpdatedAt(now);
		try {
			itemRepository.save(it);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "COMPONENT_ITEM_DUPLICATE_WAGE_COMPONENT");
		}
		saveItemLocales(it.getId(), tr);
		auditService.append(tenantId, actorId, AuditActionCodes.TENANT_COMPONENT_ITEM_CREATED,
				AuditResourceTypes.TENANT_COMPONENT_ITEM, it.getId().toString(), correlationId,
				Map.of("headerId", headerId.toString(), "wageComponentId", wage.getId().toString()));
		return getItem(tenantId, companyId, groupId, headerId, it.getId(), locale);
	}

	@Transactional
	public TenantComponentItemRowDto updateItem(UUID tenantId, UUID companyId, UUID groupId, UUID headerId, UUID itemId,
			TenantComponentItemPutRequest body, UUID actorId, String correlationId, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		TenantComponentItemEntity it = requireItem(tenantId, companyId, groupId, headerId, itemId);
		TenantWageComponentEntity wage = validateTenantWageComponent(tenantId, companyId, body.tenantWageComponentId());
		if (itemRepository.existsByHeader_IdAndWageComponent_IdAndIdNot(headerId, wage.getId(), itemId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "COMPONENT_ITEM_DUPLICATE_WAGE_COMPONENT");
		}
		Map<String, NameDescriptionPair> tr = ComponentGroupingValidation.normalizeTranslations(body.translations());
		it.setWageComponent(wage);
		it.setSortOrder(body.sortOrder().intValue());
		it.setUpdatedAt(Instant.now());
		try {
			itemRepository.save(it);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "COMPONENT_ITEM_DUPLICATE_WAGE_COMPONENT");
		}
		saveItemLocales(itemId, tr);
		auditService.append(tenantId, actorId, AuditActionCodes.TENANT_COMPONENT_ITEM_UPDATED,
				AuditResourceTypes.TENANT_COMPONENT_ITEM, itemId.toString(), correlationId,
				Map.of("headerId", headerId.toString()));
		return toItemRow(it, itemLocaleRepository.findByTenantComponentItemIdIn(List.of(itemId)), locale);
	}

	@Transactional
	public void deleteItem(UUID tenantId, UUID companyId, UUID groupId, UUID headerId, UUID itemId, UUID actorId,
			String correlationId) {
		requireItem(tenantId, companyId, groupId, headerId, itemId);
		itemRepository.deleteById(itemId);
		auditService.append(tenantId, actorId, AuditActionCodes.TENANT_COMPONENT_ITEM_DELETED,
				AuditResourceTypes.TENANT_COMPONENT_ITEM, itemId.toString(), correlationId,
				Map.of("headerId", headerId.toString()));
	}

	private TenantCompanyEntity requireCompany(UUID tenantId, UUID companyId) {
		return tenantCompanyRepository.findByIdAndTenantId(companyId, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private TenantComponentGroupEntity requireGroup(UUID tenantId, UUID companyId, UUID id) {
		return groupRepository.findByIdAndTenantIdAndCompanyId(id, tenantId, companyId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private TenantComponentHeaderEntity requireHeader(UUID tenantId, UUID companyId, UUID groupId, UUID headerId) {
		TenantComponentHeaderEntity h = headerRepository.findByIdAndGroup_Id(headerId, groupId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (!h.getGroup().getTenantId().equals(tenantId) || !h.getGroup().getCompanyId().equals(companyId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return h;
	}

	private TenantComponentItemEntity requireItem(UUID tenantId, UUID companyId, UUID groupId, UUID headerId, UUID itemId) {
		TenantComponentItemEntity it = itemRepository.findByIdAndHeader_IdAndHeader_Group_Id(itemId, headerId, groupId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (!it.getHeader().getGroup().getTenantId().equals(tenantId) || !it.getHeader().getGroup().getCompanyId().equals(companyId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return it;
	}

	private TenantWageComponentEntity validateTenantWageComponent(UUID tenantId, UUID companyId, UUID wageComponentId) {
		return tenantWageComponentRepository.findByIdAndTenantIdAndCompanyId(wageComponentId, tenantId, companyId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "WAGE_COMPONENT_NOT_FOUND"));
	}

	private Map<UUID, List<TenantComponentGroupLocaleEntity>> loadGroupLocales(Collection<UUID> groupIds) {
		if (groupIds.isEmpty()) {
			return Map.of();
		}
		return groupLocaleRepository.findByTenantComponentGroupIdIn(groupIds).stream()
				.collect(Collectors.groupingBy(TenantComponentGroupLocaleEntity::getTenantComponentGroupId));
	}

	private Map<UUID, List<TenantComponentHeaderLocaleEntity>> loadHeaderLocales(Collection<UUID> headerIds) {
		if (headerIds.isEmpty()) {
			return Map.of();
		}
		return headerLocaleRepository.findByTenantComponentHeaderIdIn(headerIds).stream()
				.collect(Collectors.groupingBy(TenantComponentHeaderLocaleEntity::getTenantComponentHeaderId));
	}

	private Map<UUID, List<TenantComponentItemLocaleEntity>> loadItemLocales(Collection<UUID> itemIds) {
		if (itemIds.isEmpty()) {
			return Map.of();
		}
		return itemLocaleRepository.findByTenantComponentItemIdIn(itemIds).stream()
				.collect(Collectors.groupingBy(TenantComponentItemLocaleEntity::getTenantComponentItemId));
	}

	private void saveGroupLocales(UUID groupId, Map<String, NameDescriptionPair> translations) {
		groupLocaleRepository.deleteByTenantComponentGroupId(groupId);
		groupLocaleRepository.flush();
		for (Map.Entry<String, NameDescriptionPair> e : translations.entrySet()) {
			TenantComponentGroupLocaleEntity row = new TenantComponentGroupLocaleEntity();
			row.setId(UUID.randomUUID());
			row.setTenantComponentGroupId(groupId);
			row.setLocale(e.getKey());
			row.setName(e.getValue().name());
			row.setDescription(e.getValue().description());
			groupLocaleRepository.save(row);
		}
	}

	private void saveHeaderLocales(UUID headerId, Map<String, NameDescriptionPair> translations) {
		headerLocaleRepository.deleteByTenantComponentHeaderId(headerId);
		headerLocaleRepository.flush();
		for (Map.Entry<String, NameDescriptionPair> e : translations.entrySet()) {
			TenantComponentHeaderLocaleEntity row = new TenantComponentHeaderLocaleEntity();
			row.setId(UUID.randomUUID());
			row.setTenantComponentHeaderId(headerId);
			row.setLocale(e.getKey());
			row.setName(e.getValue().name());
			row.setDescription(e.getValue().description());
			headerLocaleRepository.save(row);
		}
	}

	private void saveItemLocales(UUID itemId, Map<String, NameDescriptionPair> translations) {
		itemLocaleRepository.deleteByTenantComponentItemId(itemId);
		itemLocaleRepository.flush();
		for (Map.Entry<String, NameDescriptionPair> e : translations.entrySet()) {
			TenantComponentItemLocaleEntity row = new TenantComponentItemLocaleEntity();
			row.setId(UUID.randomUUID());
			row.setTenantComponentItemId(itemId);
			row.setLocale(e.getKey());
			row.setName(e.getValue().name());
			row.setDescription(e.getValue().description());
			itemLocaleRepository.save(row);
		}
	}

	private TenantComponentGroupRowDto toGroupRow(TenantComponentGroupEntity g, List<TenantComponentGroupLocaleEntity> localeRows,
			String locale) {
		TenantCompanyEntity company = tenantCompanyRepository.getReferenceById(g.getCompanyId());
		Map<String, String> names = new HashMap<>();
		Map<String, String> descriptions = new HashMap<>();
		for (TenantComponentGroupLocaleEntity r : localeRows) {
			String k = r.getLocale().toLowerCase(Locale.ROOT);
			names.put(k, r.getName());
			if (r.getDescription() != null) {
				descriptions.put(k, r.getDescription());
			}
		}
		String resolvedName = resolveString(locale, names);
		String resolvedDesc = resolveNullableString(locale, descriptions);
		List<PlatformComponentTranslationDto> dtos = localeRows.stream()
				.sorted((a, b) -> a.getLocale().compareToIgnoreCase(b.getLocale()))
				.map(r -> new PlatformComponentTranslationDto(r.getLocale(), r.getName(), r.getDescription()))
				.toList();
		return new TenantComponentGroupRowDto(g.getId(), g.getCompanyId(), g.getPlatformComponentGroupTemplateId(),
				company.getPayrollCountry(), resolvedName, resolvedDesc, dtos, g.getSortOrder(), g.isActive(), g.getCreatedAt(),
				g.getUpdatedAt());
	}

	private TenantComponentHeaderRowDto toHeaderRow(TenantComponentHeaderEntity h,
			List<TenantComponentHeaderLocaleEntity> localeRows, String locale) {
		Map<String, String> names = new HashMap<>();
		Map<String, String> descriptions = new HashMap<>();
		for (TenantComponentHeaderLocaleEntity r : localeRows) {
			String k = r.getLocale().toLowerCase(Locale.ROOT);
			names.put(k, r.getName());
			if (r.getDescription() != null) {
				descriptions.put(k, r.getDescription());
			}
		}
		String resolvedName = resolveString(locale, names);
		String resolvedDesc = resolveNullableString(locale, descriptions);
		List<PlatformComponentTranslationDto> dtos = localeRows.stream()
				.sorted((a, b) -> a.getLocale().compareToIgnoreCase(b.getLocale()))
				.map(r -> new PlatformComponentTranslationDto(r.getLocale(), r.getName(), r.getDescription()))
				.toList();
		return new TenantComponentHeaderRowDto(h.getId(), h.getGroup().getId(), resolvedName, resolvedDesc, dtos,
				h.getSortOrder(), h.getCreatedAt(), h.getUpdatedAt());
	}

	private TenantComponentItemRowDto toItemRow(TenantComponentItemEntity it, List<TenantComponentItemLocaleEntity> localeRows,
			String locale) {
		TenantWageComponentEntity w = it.getWageComponent();
		Map<String, String> names = new HashMap<>();
		Map<String, String> descriptions = new HashMap<>();
		for (TenantComponentItemLocaleEntity r : localeRows) {
			String k = r.getLocale().toLowerCase(Locale.ROOT);
			names.put(k, r.getName());
			if (r.getDescription() != null) {
				descriptions.put(k, r.getDescription());
			}
		}
		String resolvedName = resolveString(locale, names);
		String resolvedDesc = resolveNullableString(locale, descriptions);
		List<PlatformComponentTranslationDto> dtos = localeRows.stream()
				.sorted((a, b) -> a.getLocale().compareToIgnoreCase(b.getLocale()))
				.map(r -> new PlatformComponentTranslationDto(r.getLocale(), r.getName(), r.getDescription()))
				.toList();
		return new TenantComponentItemRowDto(it.getId(), it.getHeader().getId(), w.getId(), w.getCode(), w.getName(),
				resolvedName, resolvedDesc, dtos, it.getSortOrder(), it.getCreatedAt(), it.getUpdatedAt());
	}

	private static String resolveString(String locale, Map<String, String> byLocale) {
		String direct = byLocale.get(locale);
		if (direct != null) {
			return direct;
		}
		if (byLocale.containsKey("en")) {
			return byLocale.get("en");
		}
		return byLocale.values().stream().findFirst().orElse("-");
	}

	private static String resolveNullableString(String locale, Map<String, String> byLocale) {
		if (byLocale.isEmpty()) {
			return null;
		}
		String direct = byLocale.get(locale);
		if (direct != null) {
			return direct;
		}
		if (byLocale.containsKey("en")) {
			return byLocale.get("en");
		}
		return byLocale.values().stream().findFirst().orElse(null);
	}

	private static String normalizeReadLocale(String raw) {
		if (raw == null || raw.isBlank()) {
			return "en";
		}
		String v = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
		if (!SUPPORTED_READ_LOCALES.contains(v)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_LOCALE");
		}
		return v;
	}

	private static <T> Map<String, Object> pagePayload(List<T> items, Page<?> p) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("items", items);
		out.put("totalElements", p.getTotalElements());
		out.put("page", p.getNumber());
		out.put("size", p.getSize());
		out.put("totalPages", p.getTotalPages());
		return out;
	}
}
