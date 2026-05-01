package com.wagepayroll.currency;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.wagepayroll.api.dto.TenantCurrenciesDto;
import com.wagepayroll.api.dto.TenantCurrenciesReplaceRequest;
import com.wagepayroll.api.dto.TenantCurrencyItemDto;
import com.wagepayroll.domain.currency.PlatformCurrencyEntity;
import com.wagepayroll.domain.currency.PlatformCurrencyRepository;
import com.wagepayroll.domain.currency.TenantCurrencyEntity;
import com.wagepayroll.domain.currency.TenantCurrencyRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantCurrencyService {

	private final PlatformCurrencyRepository platformCurrencyRepository;
	private final TenantCurrencyRepository tenantCurrencyRepository;

	public TenantCurrencyService(PlatformCurrencyRepository platformCurrencyRepository,
			TenantCurrencyRepository tenantCurrencyRepository) {
		this.platformCurrencyRepository = platformCurrencyRepository;
		this.tenantCurrencyRepository = tenantCurrencyRepository;
	}

	@Transactional(readOnly = true)
	public TenantCurrenciesDto get(UUID tenantId) {
		List<PlatformCurrencyEntity> catalog = platformCurrencyRepository.findByActiveTrueOrderBySortOrderAscCodeAsc();
		Set<UUID> assignedIds = new LinkedHashSet<>();
		Set<String> assignedCodes = new LinkedHashSet<>();
		for (TenantCurrencyEntity row : tenantCurrencyRepository.findByTenantIdOrderByCreatedAtAsc(tenantId)) {
			assignedIds.add(row.getPlatformCurrencyId());
		}
		List<TenantCurrencyItemDto> items = new ArrayList<>();
		for (PlatformCurrencyEntity c : catalog) {
			boolean assigned = assignedIds.contains(c.getId());
			if (assigned) {
				assignedCodes.add(c.getCode());
			}
			items.add(new TenantCurrencyItemDto(c.getId(), c.getCode(), c.getDisplayName(), c.getSortOrder(), assigned));
		}
		List<String> codes = new ArrayList<>(assignedCodes);
		codes.sort(String::compareToIgnoreCase);
		return new TenantCurrenciesDto(items, codes);
	}

	@Transactional
	public List<String> replace(UUID tenantId, TenantCurrenciesReplaceRequest body) {
		if (body == null || body.codes() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TENANT_CURRENCY_CODES_REQUIRED");
		}
		Set<String> normalized = new LinkedHashSet<>();
		for (String code : body.codes()) {
			if (code == null || code.isBlank()) {
				continue;
			}
			normalized.add(code.trim().toUpperCase());
		}
		Instant now = Instant.now();
		if (normalized.isEmpty()) {
			tenantCurrencyRepository.deleteByTenantId(tenantId);
			return List.of();
		}

		Map<String, PlatformCurrencyEntity> byCode = new HashMap<>();
		for (PlatformCurrencyEntity c : platformCurrencyRepository.findByCodeIn(normalized)) {
			byCode.put(c.getCode(), c);
		}
		for (String code : normalized) {
			PlatformCurrencyEntity row = byCode.get(code);
			if (row == null || !row.isActive()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_OR_INACTIVE_PLATFORM_CURRENCY_CODE");
			}
		}

		tenantCurrencyRepository.deleteByTenantId(tenantId);
		for (String code : normalized) {
			PlatformCurrencyEntity platform = byCode.get(code);
			TenantCurrencyEntity row = new TenantCurrencyEntity();
			row.setId(UUID.randomUUID());
			row.setTenantId(tenantId);
			row.setPlatformCurrencyId(platform.getId());
			row.setCreatedAt(now);
			row.setUpdatedAt(now);
			tenantCurrencyRepository.save(row);
		}
		List<String> out = new ArrayList<>(normalized);
		out.sort(String::compareToIgnoreCase);
		return out;
	}
}
