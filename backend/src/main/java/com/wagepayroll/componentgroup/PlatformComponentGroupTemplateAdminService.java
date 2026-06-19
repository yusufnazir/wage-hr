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

import com.wagepayroll.api.dto.PlatformComponentGroupCreateRequest;
import com.wagepayroll.api.dto.PlatformComponentGroupPutRequest;
import com.wagepayroll.api.dto.PlatformComponentGroupRowDto;
import com.wagepayroll.api.dto.PlatformComponentHeaderCreateRequest;
import com.wagepayroll.api.dto.PlatformComponentHeaderPutRequest;
import com.wagepayroll.api.dto.PlatformComponentHeaderRowDto;
import com.wagepayroll.api.dto.PlatformComponentItemCreateRequest;
import com.wagepayroll.api.dto.PlatformComponentItemPutRequest;
import com.wagepayroll.api.dto.PlatformComponentItemRowDto;
import com.wagepayroll.api.dto.PlatformComponentTranslationDto;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.componentgroup.ComponentGroupingValidation.NameDescriptionPair;
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
import com.wagepayroll.domain.country.PlatformCountryEntity;
import com.wagepayroll.domain.country.PlatformCountryRepository;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentTemplateEntity;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentTemplateRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformComponentGroupTemplateAdminService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final Set<String> SUPPORTED_READ_LOCALES = Set.of("en", "nl");

	private final PlatformComponentGroupTemplateRepository groupRepository;
	private final PlatformComponentGroupTemplateLocaleRepository groupLocaleRepository;
	private final PlatformComponentHeaderTemplateRepository headerRepository;
	private final PlatformComponentHeaderTemplateLocaleRepository headerLocaleRepository;
	private final PlatformComponentItemTemplateRepository itemRepository;
	private final PlatformComponentItemTemplateLocaleRepository itemLocaleRepository;
	private final PlatformCountryRepository platformCountryRepository;
	private final PlatformWageComponentTemplateRepository wageComponentTemplateRepository;
	private final AuditService auditService;

	public PlatformComponentGroupTemplateAdminService(PlatformComponentGroupTemplateRepository groupRepository,
			PlatformComponentGroupTemplateLocaleRepository groupLocaleRepository,
			PlatformComponentHeaderTemplateRepository headerRepository,
			PlatformComponentHeaderTemplateLocaleRepository headerLocaleRepository,
			PlatformComponentItemTemplateRepository itemRepository,
			PlatformComponentItemTemplateLocaleRepository itemLocaleRepository,
			PlatformCountryRepository platformCountryRepository,
			PlatformWageComponentTemplateRepository wageComponentTemplateRepository, AuditService auditService) {
		this.groupRepository = groupRepository;
		this.groupLocaleRepository = groupLocaleRepository;
		this.headerRepository = headerRepository;
		this.headerLocaleRepository = headerLocaleRepository;
		this.itemRepository = itemRepository;
		this.itemLocaleRepository = itemLocaleRepository;
		this.platformCountryRepository = platformCountryRepository;
		this.wageComponentTemplateRepository = wageComponentTemplateRepository;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> listGroups(int page, int size, String countryIso2, Boolean activeFilter, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int safePage = Math.max(page, 0);
		String iso = countryIso2 == null || countryIso2.isBlank() ? null : countryIso2.trim().toUpperCase(Locale.ROOT);
		Page<PlatformComponentGroupTemplateEntity> p = groupRepository.search(iso, activeFilter,
				PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"))));
		List<UUID> ids = p.getContent().stream().map(PlatformComponentGroupTemplateEntity::getId).toList();
		Map<UUID, List<PlatformComponentGroupTemplateLocaleEntity>> locales = loadGroupLocales(ids);
		List<PlatformComponentGroupRowDto> items = p.getContent().stream()
				.map(g -> toGroupRow(g, locales.getOrDefault(g.getId(), List.of()), locale))
				.toList();
		return pagePayload(items, p);
	}

	@Transactional(readOnly = true)
	public PlatformComponentGroupRowDto getGroup(UUID id, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		PlatformComponentGroupTemplateEntity g = requireGroup(id);
		List<PlatformComponentGroupTemplateLocaleEntity> loc = groupLocaleRepository
				.findByPlatformComponentGroupTemplateIdIn(List.of(id));
		return toGroupRow(g, loc, locale);
	}

	@Transactional
	public PlatformComponentGroupRowDto createGroup(PlatformComponentGroupCreateRequest body, UUID actorId,
			String correlationId, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		Map<String, NameDescriptionPair> tr = ComponentGroupingValidation.normalizeTranslations(body.translations());
		PlatformCountryEntity country = requirePayrollEnabledCountry(body.platformCountryId());
		Instant now = Instant.now();
		PlatformComponentGroupTemplateEntity g = new PlatformComponentGroupTemplateEntity();
		g.setId(UUID.randomUUID());
		g.setCountry(country);
		g.setSortOrder(body.sortOrder() == null ? 0 : body.sortOrder().intValue());
		g.setActive(body.active() == null || body.active().booleanValue());
		g.setCreatedAt(now);
		g.setUpdatedAt(now);
		groupRepository.save(g);
		saveGroupLocales(g.getId(), tr);
		auditService.append(null, actorId, AuditActionCodes.PLATFORM_COMPONENT_GROUP_TEMPLATE_CREATED,
				AuditResourceTypes.PLATFORM_COMPONENT_GROUP_TEMPLATE, g.getId().toString(), correlationId,
				Map.of("countryCode", country.getIsoAlpha2()));
		return getGroup(g.getId(), locale);
	}

	@Transactional
	public PlatformComponentGroupRowDto updateGroup(UUID id, PlatformComponentGroupPutRequest body, UUID actorId,
			String correlationId, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		PlatformComponentGroupTemplateEntity g = requireGroup(id);
		Map<String, NameDescriptionPair> tr = ComponentGroupingValidation.normalizeTranslations(body.translations());
		g.setSortOrder(body.sortOrder().intValue());
		g.setActive(body.active().booleanValue());
		g.setUpdatedAt(Instant.now());
		groupRepository.save(g);
		saveGroupLocales(id, tr);
		auditService.append(null, actorId, AuditActionCodes.PLATFORM_COMPONENT_GROUP_TEMPLATE_UPDATED,
				AuditResourceTypes.PLATFORM_COMPONENT_GROUP_TEMPLATE, id.toString(), correlationId, Map.of("id", id.toString()));
		return toGroupRow(g, groupLocaleRepository.findByPlatformComponentGroupTemplateIdIn(List.of(id)), locale);
	}

	@Transactional
	public void deleteGroup(UUID id, UUID actorId, String correlationId) {
		PlatformComponentGroupTemplateEntity g = requireGroup(id);
		String countryCode = g.getCountry().getIsoAlpha2();
		groupRepository.deleteById(id);
		auditService.append(null, actorId, AuditActionCodes.PLATFORM_COMPONENT_GROUP_TEMPLATE_DELETED,
				AuditResourceTypes.PLATFORM_COMPONENT_GROUP_TEMPLATE, id.toString(), correlationId,
				Map.of("id", id.toString(), "countryCode", countryCode));
	}

	@Transactional(readOnly = true)
	public Map<String, Object> listHeaders(UUID groupId, int page, int size, String localeRaw) {
		requireGroup(groupId);
		String locale = normalizeReadLocale(localeRaw);
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int safePage = Math.max(page, 0);
		Page<PlatformComponentHeaderTemplateEntity> p = headerRepository.findByGroup_IdOrderBySortOrderAscIdAsc(groupId,
				PageRequest.of(safePage, safeSize));
		List<UUID> ids = p.getContent().stream().map(PlatformComponentHeaderTemplateEntity::getId).toList();
		Map<UUID, List<PlatformComponentHeaderTemplateLocaleEntity>> locales = loadHeaderLocales(ids);
		List<PlatformComponentHeaderRowDto> items = p.getContent().stream()
				.map(h -> toHeaderRow(h, locales.getOrDefault(h.getId(), List.of()), locale))
				.toList();
		return pagePayload(items, p);
	}

	@Transactional(readOnly = true)
	public PlatformComponentHeaderRowDto getHeader(UUID groupId, UUID headerId, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		PlatformComponentHeaderTemplateEntity h = requireHeader(groupId, headerId);
		List<PlatformComponentHeaderTemplateLocaleEntity> loc = headerLocaleRepository
				.findByPlatformComponentHeaderTemplateIdIn(List.of(headerId));
		return toHeaderRow(h, loc, locale);
	}

	@Transactional
	public PlatformComponentHeaderRowDto createHeader(UUID groupId, PlatformComponentHeaderCreateRequest body, UUID actorId,
			String correlationId, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		requireGroup(groupId);
		Map<String, NameDescriptionPair> tr = ComponentGroupingValidation.normalizeTranslations(body.translations());
		Instant now = Instant.now();
		PlatformComponentHeaderTemplateEntity h = new PlatformComponentHeaderTemplateEntity();
		h.setId(UUID.randomUUID());
		h.setGroup(groupRepository.getReferenceById(groupId));
		h.setSortOrder(body.sortOrder() == null ? 0 : body.sortOrder().intValue());
		h.setCreatedAt(now);
		h.setUpdatedAt(now);
		headerRepository.save(h);
		saveHeaderLocales(h.getId(), tr);
		auditService.append(null, actorId, AuditActionCodes.PLATFORM_COMPONENT_HEADER_TEMPLATE_CREATED,
				AuditResourceTypes.PLATFORM_COMPONENT_HEADER_TEMPLATE, h.getId().toString(), correlationId,
				Map.of("groupId", groupId.toString()));
		return getHeader(groupId, h.getId(), locale);
	}

	@Transactional
	public PlatformComponentHeaderRowDto updateHeader(UUID groupId, UUID headerId, PlatformComponentHeaderPutRequest body,
			UUID actorId, String correlationId, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		PlatformComponentHeaderTemplateEntity h = requireHeader(groupId, headerId);
		Map<String, NameDescriptionPair> tr = ComponentGroupingValidation.normalizeTranslations(body.translations());
		h.setSortOrder(body.sortOrder().intValue());
		h.setUpdatedAt(Instant.now());
		headerRepository.save(h);
		saveHeaderLocales(headerId, tr);
		auditService.append(null, actorId, AuditActionCodes.PLATFORM_COMPONENT_HEADER_TEMPLATE_UPDATED,
				AuditResourceTypes.PLATFORM_COMPONENT_HEADER_TEMPLATE, headerId.toString(), correlationId,
				Map.of("groupId", groupId.toString()));
		return toHeaderRow(h, headerLocaleRepository.findByPlatformComponentHeaderTemplateIdIn(List.of(headerId)), locale);
	}

	@Transactional
	public void deleteHeader(UUID groupId, UUID headerId, UUID actorId, String correlationId) {
		requireHeader(groupId, headerId);
		headerRepository.deleteById(headerId);
		auditService.append(null, actorId, AuditActionCodes.PLATFORM_COMPONENT_HEADER_TEMPLATE_DELETED,
				AuditResourceTypes.PLATFORM_COMPONENT_HEADER_TEMPLATE, headerId.toString(), correlationId,
				Map.of("groupId", groupId.toString()));
	}

	@Transactional(readOnly = true)
	public Map<String, Object> listItems(UUID groupId, UUID headerId, int page, int size, String localeRaw) {
		requireHeader(groupId, headerId);
		String locale = normalizeReadLocale(localeRaw);
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int safePage = Math.max(page, 0);
		Page<PlatformComponentItemTemplateEntity> p = itemRepository.findByHeader_IdOrderBySortOrderAscIdAsc(headerId,
				PageRequest.of(safePage, safeSize));
		List<UUID> ids = p.getContent().stream().map(PlatformComponentItemTemplateEntity::getId).toList();
		Map<UUID, List<PlatformComponentItemTemplateLocaleEntity>> locales = loadItemLocales(ids);
		List<PlatformComponentItemRowDto> items = p.getContent().stream()
				.map(it -> toItemRow(it, locales.getOrDefault(it.getId(), List.of()), locale))
				.toList();
		return pagePayload(items, p);
	}

	@Transactional(readOnly = true)
	public PlatformComponentItemRowDto getItem(UUID groupId, UUID headerId, UUID itemId, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		PlatformComponentItemTemplateEntity it = requireItem(groupId, headerId, itemId);
		List<PlatformComponentItemTemplateLocaleEntity> loc = itemLocaleRepository
				.findByPlatformComponentItemTemplateIdIn(List.of(itemId));
		return toItemRow(it, loc, locale);
	}

	@Transactional
	public PlatformComponentItemRowDto createItem(UUID groupId, UUID headerId, PlatformComponentItemCreateRequest body,
			UUID actorId, String correlationId, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		PlatformComponentHeaderTemplateEntity header = requireHeader(groupId, headerId);
		PlatformCountryEntity country = header.getGroup().getCountry();
		PlatformWageComponentTemplateEntity wageTemplate = validateWageComponentTemplate(country,
				body.platformWageComponentTemplateId());
		if (itemRepository.existsByHeader_IdAndWageComponentTemplate_Id(headerId, wageTemplate.getId())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "COMPONENT_ITEM_DUPLICATE_WAGE_COMPONENT");
		}
		Map<String, NameDescriptionPair> tr = ComponentGroupingValidation.normalizeTranslations(body.translations());
		Instant now = Instant.now();
		PlatformComponentItemTemplateEntity it = new PlatformComponentItemTemplateEntity();
		it.setId(UUID.randomUUID());
		it.setHeader(headerRepository.getReferenceById(headerId));
		it.setWageComponentTemplate(wageTemplate);
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
		auditService.append(null, actorId, AuditActionCodes.PLATFORM_COMPONENT_ITEM_TEMPLATE_CREATED,
				AuditResourceTypes.PLATFORM_COMPONENT_ITEM_TEMPLATE, it.getId().toString(), correlationId,
				Map.of("headerId", headerId.toString(), "wageComponentTemplateId", wageTemplate.getId().toString()));
		return getItem(groupId, headerId, it.getId(), locale);
	}

	@Transactional
	public PlatformComponentItemRowDto updateItem(UUID groupId, UUID headerId, UUID itemId, PlatformComponentItemPutRequest body,
			UUID actorId, String correlationId, String localeRaw) {
		String locale = normalizeReadLocale(localeRaw);
		PlatformComponentItemTemplateEntity it = requireItem(groupId, headerId, itemId);
		PlatformCountryEntity country = it.getHeader().getGroup().getCountry();
		PlatformWageComponentTemplateEntity wageTemplate = validateWageComponentTemplate(country,
				body.platformWageComponentTemplateId());
		if (itemRepository.existsByHeader_IdAndWageComponentTemplate_IdAndIdNot(headerId, wageTemplate.getId(), itemId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "COMPONENT_ITEM_DUPLICATE_WAGE_COMPONENT");
		}
		Map<String, NameDescriptionPair> tr = ComponentGroupingValidation.normalizeTranslations(body.translations());
		it.setWageComponentTemplate(wageTemplate);
		it.setSortOrder(body.sortOrder().intValue());
		it.setUpdatedAt(Instant.now());
		try {
			itemRepository.save(it);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "COMPONENT_ITEM_DUPLICATE_WAGE_COMPONENT");
		}
		saveItemLocales(itemId, tr);
		auditService.append(null, actorId, AuditActionCodes.PLATFORM_COMPONENT_ITEM_TEMPLATE_UPDATED,
				AuditResourceTypes.PLATFORM_COMPONENT_ITEM_TEMPLATE, itemId.toString(), correlationId,
				Map.of("headerId", headerId.toString()));
		return toItemRow(it, itemLocaleRepository.findByPlatformComponentItemTemplateIdIn(List.of(itemId)), locale);
	}

	@Transactional
	public void deleteItem(UUID groupId, UUID headerId, UUID itemId, UUID actorId, String correlationId) {
		requireItem(groupId, headerId, itemId);
		itemRepository.deleteById(itemId);
		auditService.append(null, actorId, AuditActionCodes.PLATFORM_COMPONENT_ITEM_TEMPLATE_DELETED,
				AuditResourceTypes.PLATFORM_COMPONENT_ITEM_TEMPLATE, itemId.toString(), correlationId,
				Map.of("headerId", headerId.toString()));
	}

	private PlatformComponentGroupTemplateEntity requireGroup(UUID id) {
		return groupRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private PlatformComponentHeaderTemplateEntity requireHeader(UUID groupId, UUID headerId) {
		return headerRepository.findByIdAndGroup_Id(headerId, groupId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private PlatformComponentItemTemplateEntity requireItem(UUID groupId, UUID headerId, UUID itemId) {
		return itemRepository.findByIdAndHeader_IdAndHeader_Group_Id(itemId, headerId, groupId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private PlatformCountryEntity requirePayrollEnabledCountry(UUID countryId) {
		PlatformCountryEntity c = platformCountryRepository.findById(countryId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "COUNTRY_NOT_FOUND"));
		if (!c.isActive() || !c.isPayrollEnabled()) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "COUNTRY_NOT_PAYROLL_ENABLED");
		}
		return c;
	}

	private PlatformWageComponentTemplateEntity validateWageComponentTemplate(PlatformCountryEntity country,
			UUID wageComponentTemplateId) {
		PlatformWageComponentTemplateEntity t = wageComponentTemplateRepository.findById(wageComponentTemplateId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "WAGE_COMPONENT_TEMPLATE_NOT_FOUND"));
		if (!t.isActive()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "WAGE_COMPONENT_TEMPLATE_INACTIVE");
		}
		if (!country.getIsoAlpha2().equalsIgnoreCase(t.getCountryCode())) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "WAGE_COMPONENT_TEMPLATE_COUNTRY_MISMATCH");
		}
		return t;
	}

	private Map<UUID, List<PlatformComponentGroupTemplateLocaleEntity>> loadGroupLocales(Collection<UUID> groupIds) {
		if (groupIds.isEmpty()) {
			return Map.of();
		}
		return groupLocaleRepository.findByPlatformComponentGroupTemplateIdIn(groupIds).stream()
				.collect(Collectors.groupingBy(PlatformComponentGroupTemplateLocaleEntity::getPlatformComponentGroupTemplateId));
	}

	private Map<UUID, List<PlatformComponentHeaderTemplateLocaleEntity>> loadHeaderLocales(Collection<UUID> headerIds) {
		if (headerIds.isEmpty()) {
			return Map.of();
		}
		return headerLocaleRepository.findByPlatformComponentHeaderTemplateIdIn(headerIds).stream()
				.collect(Collectors.groupingBy(PlatformComponentHeaderTemplateLocaleEntity::getPlatformComponentHeaderTemplateId));
	}

	private Map<UUID, List<PlatformComponentItemTemplateLocaleEntity>> loadItemLocales(Collection<UUID> itemIds) {
		if (itemIds.isEmpty()) {
			return Map.of();
		}
		return itemLocaleRepository.findByPlatformComponentItemTemplateIdIn(itemIds).stream()
				.collect(Collectors.groupingBy(PlatformComponentItemTemplateLocaleEntity::getPlatformComponentItemTemplateId));
	}

	private void saveGroupLocales(UUID groupId, Map<String, NameDescriptionPair> translations) {
		groupLocaleRepository.deleteByPlatformComponentGroupTemplateId(groupId);
		groupLocaleRepository.flush();
		for (Map.Entry<String, NameDescriptionPair> e : translations.entrySet()) {
			PlatformComponentGroupTemplateLocaleEntity row = new PlatformComponentGroupTemplateLocaleEntity();
			row.setId(UUID.randomUUID());
			row.setPlatformComponentGroupTemplateId(groupId);
			row.setLocale(e.getKey());
			row.setName(e.getValue().name());
			row.setDescription(e.getValue().description());
			groupLocaleRepository.save(row);
		}
	}

	private void saveHeaderLocales(UUID headerId, Map<String, NameDescriptionPair> translations) {
		headerLocaleRepository.deleteByPlatformComponentHeaderTemplateId(headerId);
		headerLocaleRepository.flush();
		for (Map.Entry<String, NameDescriptionPair> e : translations.entrySet()) {
			PlatformComponentHeaderTemplateLocaleEntity row = new PlatformComponentHeaderTemplateLocaleEntity();
			row.setId(UUID.randomUUID());
			row.setPlatformComponentHeaderTemplateId(headerId);
			row.setLocale(e.getKey());
			row.setName(e.getValue().name());
			row.setDescription(e.getValue().description());
			headerLocaleRepository.save(row);
		}
	}

	private void saveItemLocales(UUID itemId, Map<String, NameDescriptionPair> translations) {
		itemLocaleRepository.deleteByPlatformComponentItemTemplateId(itemId);
		itemLocaleRepository.flush();
		for (Map.Entry<String, NameDescriptionPair> e : translations.entrySet()) {
			PlatformComponentItemTemplateLocaleEntity row = new PlatformComponentItemTemplateLocaleEntity();
			row.setId(UUID.randomUUID());
			row.setPlatformComponentItemTemplateId(itemId);
			row.setLocale(e.getKey());
			row.setName(e.getValue().name());
			row.setDescription(e.getValue().description());
			itemLocaleRepository.save(row);
		}
	}

	private PlatformComponentGroupRowDto toGroupRow(PlatformComponentGroupTemplateEntity g,
			List<PlatformComponentGroupTemplateLocaleEntity> localeRows, String locale) {
		Map<String, String> names = new HashMap<>();
		Map<String, String> descriptions = new HashMap<>();
		for (PlatformComponentGroupTemplateLocaleEntity r : localeRows) {
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
		return new PlatformComponentGroupRowDto(g.getId(), g.getCountry().getId(), g.getCountry().getIsoAlpha2(), resolvedName,
				resolvedDesc, dtos, g.getSortOrder(), g.isActive(), g.getCreatedAt(), g.getUpdatedAt());
	}

	private PlatformComponentHeaderRowDto toHeaderRow(PlatformComponentHeaderTemplateEntity h,
			List<PlatformComponentHeaderTemplateLocaleEntity> localeRows, String locale) {
		Map<String, String> names = new HashMap<>();
		Map<String, String> descriptions = new HashMap<>();
		for (PlatformComponentHeaderTemplateLocaleEntity r : localeRows) {
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
		return new PlatformComponentHeaderRowDto(h.getId(), h.getGroup().getId(), resolvedName, resolvedDesc, dtos,
				h.getSortOrder(), h.getCreatedAt(), h.getUpdatedAt());
	}

	private PlatformComponentItemRowDto toItemRow(PlatformComponentItemTemplateEntity it,
			List<PlatformComponentItemTemplateLocaleEntity> localeRows, String locale) {
		PlatformWageComponentTemplateEntity w = it.getWageComponentTemplate();
		Map<String, String> names = new HashMap<>();
		Map<String, String> descriptions = new HashMap<>();
		for (PlatformComponentItemTemplateLocaleEntity r : localeRows) {
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
		return new PlatformComponentItemRowDto(it.getId(), it.getHeader().getId(), w.getId(), w.getTemplateCode(), w.getName(),
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
