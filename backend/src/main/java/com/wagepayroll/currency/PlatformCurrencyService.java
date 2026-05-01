package com.wagepayroll.currency;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import com.wagepayroll.api.dto.PlatformCurrencyCreateRequest;
import com.wagepayroll.api.dto.PlatformCurrencyDto;
import com.wagepayroll.api.dto.PlatformCurrencyPatchRequest;
import com.wagepayroll.domain.currency.PlatformCurrencyEntity;
import com.wagepayroll.domain.currency.PlatformCurrencyRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformCurrencyService {

	private static final Pattern ISO_4217 = Pattern.compile("^[A-Z]{3}$");

	private final PlatformCurrencyRepository platformCurrencyRepository;

	public PlatformCurrencyService(PlatformCurrencyRepository platformCurrencyRepository) {
		this.platformCurrencyRepository = platformCurrencyRepository;
	}

	@Transactional(readOnly = true)
	public List<PlatformCurrencyDto> list() {
		return platformCurrencyRepository.findAllByOrderBySortOrderAscCodeAsc().stream().map(this::toDto).toList();
	}

	@Transactional
	public PlatformCurrencyDto create(PlatformCurrencyCreateRequest body) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		String code = normalizeCode(body.code());
		if (platformCurrencyRepository.existsByCodeIgnoreCase(code)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "PLATFORM_CURRENCY_CODE_IN_USE");
		}
		String displayName = normalizeDisplayName(body.displayName());
		int sortOrder = body.sortOrder() == null ? 100 : body.sortOrder();
		if (sortOrder < 0 || sortOrder > 10000) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PLATFORM_CURRENCY_SORT_ORDER_INVALID");
		}
		boolean active = body.active() == null ? true : body.active();
		Instant now = Instant.now();

		PlatformCurrencyEntity row = new PlatformCurrencyEntity();
		row.setId(UUID.randomUUID());
		row.setCode(code);
		row.setDisplayName(displayName);
		row.setSortOrder(sortOrder);
		row.setActive(active);
		row.setCreatedAt(now);
		row.setUpdatedAt(now);
		platformCurrencyRepository.save(row);
		return toDto(row);
	}

	@Transactional
	public PlatformCurrencyDto patch(UUID id, PlatformCurrencyPatchRequest body) {
		if (body == null || (body.displayName() == null && body.sortOrder() == null && body.active() == null)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EMPTY_PATCH");
		}
		PlatformCurrencyEntity row = platformCurrencyRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PLATFORM_CURRENCY_NOT_FOUND"));
		if (body.displayName() != null) {
			row.setDisplayName(normalizeDisplayName(body.displayName()));
		}
		if (body.sortOrder() != null) {
			if (body.sortOrder() < 0 || body.sortOrder() > 10000) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PLATFORM_CURRENCY_SORT_ORDER_INVALID");
			}
			row.setSortOrder(body.sortOrder());
		}
		if (body.active() != null) {
			row.setActive(body.active());
		}
		row.setUpdatedAt(Instant.now());
		return toDto(row);
	}

	private PlatformCurrencyDto toDto(PlatformCurrencyEntity row) {
		return new PlatformCurrencyDto(row.getId(), row.getCode(), row.getDisplayName(), row.getSortOrder(), row.isActive(),
				DateTimeFormatter.ISO_INSTANT.format(row.getUpdatedAt()));
	}

	private static String normalizeCode(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PLATFORM_CURRENCY_CODE_REQUIRED");
		}
		String code = raw.trim().toUpperCase();
		if (!ISO_4217.matcher(code).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PLATFORM_CURRENCY_CODE_INVALID");
		}
		return code;
	}

	private static String normalizeDisplayName(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PLATFORM_CURRENCY_DISPLAY_NAME_REQUIRED");
		}
		String value = raw.trim();
		if (value.length() < 2 || value.length() > 128) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PLATFORM_CURRENCY_DISPLAY_NAME_INVALID");
		}
		return value;
	}
}
