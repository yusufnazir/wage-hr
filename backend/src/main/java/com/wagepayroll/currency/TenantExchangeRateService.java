package com.wagepayroll.currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.wagepayroll.api.dto.TenantExchangeRateCreateRequest;
import com.wagepayroll.api.dto.TenantExchangeRateItemDto;
import com.wagepayroll.api.dto.TenantExchangeRatePatchRequest;
import com.wagepayroll.api.dto.TenantExchangeRateResolveDto;
import com.wagepayroll.domain.currency.PlatformCurrencyEntity;
import com.wagepayroll.domain.currency.PlatformCurrencyRepository;
import com.wagepayroll.domain.currency.TenantCurrencyEntity;
import com.wagepayroll.domain.currency.TenantCurrencyRepository;
import com.wagepayroll.domain.currency.TenantExchangeRateEntity;
import com.wagepayroll.domain.currency.TenantExchangeRateRepository;

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
public class TenantExchangeRateService {

	private static final int MAX_PAGE_SIZE = 100;

	private final TenantExchangeRateRepository tenantExchangeRateRepository;
	private final TenantCurrencyRepository tenantCurrencyRepository;
	private final PlatformCurrencyRepository platformCurrencyRepository;

	public TenantExchangeRateService(TenantExchangeRateRepository tenantExchangeRateRepository,
			TenantCurrencyRepository tenantCurrencyRepository, PlatformCurrencyRepository platformCurrencyRepository) {
		this.tenantExchangeRateRepository = tenantExchangeRateRepository;
		this.tenantCurrencyRepository = tenantCurrencyRepository;
		this.platformCurrencyRepository = platformCurrencyRepository;
	}

	@Transactional(readOnly = true)
	public Page<TenantExchangeRateItemDto> list(UUID tenantId, int page, int size, String sort) {
		int safePage = Math.max(page, 0);
		int safeSize = size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
		Pageable pageable = PageRequest.of(safePage, safeSize, parseSort(sort));
		Page<TenantExchangeRateEntity> rows = tenantExchangeRateRepository.findByTenantId(tenantId, pageable);
		Map<UUID, PlatformCurrencyEntity> currencies = currencyMapFor(rows.getContent());
		return rows.map(row -> toItemDto(row, currencies));
	}

	@Transactional(readOnly = true)
	public TenantExchangeRateItemDto get(UUID tenantId, UUID id) {
		TenantExchangeRateEntity row = tenantExchangeRateRepository.findByIdAndTenantId(id, tenantId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exchange rate not found"));
		Map<UUID, PlatformCurrencyEntity> currencies = currencyMapFor(List.of(row));
		return toItemDto(row, currencies);
	}

	@Transactional
	public TenantExchangeRateItemDto create(UUID tenantId, TenantExchangeRateCreateRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		}
		UUID fromCurrencyId = requiredUuid(request.fromCurrencyId(), "fromCurrencyId is required");
		UUID toCurrencyId = requiredUuid(request.toCurrencyId(), "toCurrencyId is required");
		if (fromCurrencyId.equals(toCurrencyId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromCurrencyId and toCurrencyId must differ");
		}
		BigDecimal rate = normalizeRate(request.rate());
		LocalDate effectiveDate = request.effectiveDate();
		if (effectiveDate == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "effectiveDate is required");
		}
		assertTenantActivatedCurrencies(tenantId, fromCurrencyId, toCurrencyId, HttpStatus.UNPROCESSABLE_ENTITY);
		if (tenantExchangeRateRepository.existsByTenantIdAndFromCurrencyIdAndToCurrencyIdAndEffectiveDate(tenantId,
				fromCurrencyId, toCurrencyId, effectiveDate)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"A rate for this currency pair on this date already exists");
		}

		TenantExchangeRateEntity row = new TenantExchangeRateEntity();
		row.setId(UUID.randomUUID());
		row.setTenantId(tenantId);
		row.setFromCurrencyId(fromCurrencyId);
		row.setToCurrencyId(toCurrencyId);
		row.setRate(rate);
		row.setEffectiveDate(effectiveDate);
		Instant now = Instant.now();
		row.setCreatedAt(now);
		row.setUpdatedAt(now);

		TenantExchangeRateEntity saved = saveOrConflict(row);
		Map<UUID, PlatformCurrencyEntity> currencies = currencyMapFor(List.of(saved));
		return toItemDto(saved, currencies);
	}

	@Transactional
	public UpdateResult patch(UUID tenantId, UUID id, TenantExchangeRatePatchRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		}
		if (request.fromCurrencyId() != null || request.toCurrencyId() != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"fromCurrencyId and toCurrencyId are immutable in patch requests");
		}
		TenantExchangeRateEntity row = tenantExchangeRateRepository.findByIdAndTenantId(id, tenantId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exchange rate not found"));

		boolean hasRate = request.rate() != null;
		boolean hasEffectiveDate = request.effectiveDate() != null;
		if (!hasRate && !hasEffectiveDate) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one mutable field is required");
		}

		Map<String, Object> metadata = new LinkedHashMap<>();
		if (hasRate) {
			BigDecimal oldValue = row.getRate();
			BigDecimal newValue = normalizeRate(request.rate());
			if (oldValue.compareTo(newValue) != 0) {
				Map<String, Object> change = new LinkedHashMap<>();
				change.put("old", oldValue.toPlainString());
				change.put("new", newValue.toPlainString());
				metadata.put("rate", change);
				row.setRate(newValue);
			}
		}
		if (hasEffectiveDate) {
			LocalDate oldDate = row.getEffectiveDate();
			LocalDate newDate = request.effectiveDate();
			if (!oldDate.equals(newDate)) {
				Map<String, Object> change = new LinkedHashMap<>();
				change.put("old", oldDate.toString());
				change.put("new", newDate.toString());
				metadata.put("effectiveDate", change);
				row.setEffectiveDate(newDate);
			}
		}

		if (tenantExchangeRateRepository.existsByTenantIdAndFromCurrencyIdAndToCurrencyIdAndEffectiveDateAndIdNot(tenantId,
				row.getFromCurrencyId(), row.getToCurrencyId(), row.getEffectiveDate(), row.getId())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"A rate for this currency pair on this date already exists");
		}

		row.setUpdatedAt(Instant.now());
		TenantExchangeRateEntity saved = saveOrConflict(row);
		Map<UUID, PlatformCurrencyEntity> currencies = currencyMapFor(List.of(saved));
		TenantExchangeRateItemDto item = toItemDto(saved, currencies);
		return new UpdateResult(item, metadata);
	}

	@Transactional
	public TenantExchangeRateItemDto delete(UUID tenantId, UUID id) {
		TenantExchangeRateEntity row = tenantExchangeRateRepository.findByIdAndTenantId(id, tenantId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exchange rate not found"));
		Map<UUID, PlatformCurrencyEntity> currencies = currencyMapFor(List.of(row));
		TenantExchangeRateItemDto item = toItemDto(row, currencies);
		tenantExchangeRateRepository.delete(row);
		return item;
	}

	@Transactional(readOnly = true)
	public TenantExchangeRateResolveDto resolve(UUID tenantId, String fromCode, String toCode, LocalDate date) {
		if (date == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required");
		}
		String from = normalizedCode(fromCode, "from");
		String to = normalizedCode(toCode, "to");
		if (from.equals(to)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from and to currency must differ");
		}

		PlatformCurrencyEntity fromCurrency = platformCurrencyRepository.findByCodeIgnoreCase(from)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid from currency code"));
		PlatformCurrencyEntity toCurrency = platformCurrencyRepository.findByCodeIgnoreCase(to)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid to currency code"));

		assertTenantActivatedCurrencies(tenantId, fromCurrency.getId(), toCurrency.getId(), HttpStatus.BAD_REQUEST);

		TenantExchangeRateEntity row = tenantExchangeRateRepository
			.findFirstByTenantIdAndFromCurrencyIdAndToCurrencyIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
					tenantId, fromCurrency.getId(), toCurrency.getId(), date)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
					"No exchange rate exists for this pair on or before the requested date"));

		return new TenantExchangeRateResolveDto(fromCurrency.getCode(), toCurrency.getCode(), row.getRate(),
				row.getEffectiveDate());
	}

	private void assertTenantActivatedCurrencies(UUID tenantId, UUID fromCurrencyId, UUID toCurrencyId,
			HttpStatus status) {
		List<TenantCurrencyEntity> assigned = tenantCurrencyRepository.findByTenantIdOrderByCreatedAtAsc(tenantId);
		Set<UUID> assignedCurrencyIds = new HashSet<>();
		for (TenantCurrencyEntity item : assigned) {
			assignedCurrencyIds.add(item.getPlatformCurrencyId());
		}
		if (!assignedCurrencyIds.contains(fromCurrencyId) || !assignedCurrencyIds.contains(toCurrencyId)) {
			if (status == HttpStatus.UNPROCESSABLE_ENTITY) {
				throw new ResponseStatusException(status,
						"One or more selected currencies are not active for this tenant");
			}
			throw new ResponseStatusException(status, "Currency code is not active for this tenant");
		}
	}

	private TenantExchangeRateEntity saveOrConflict(TenantExchangeRateEntity entity) {
		try {
			return tenantExchangeRateRepository.save(entity);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"A rate for this currency pair on this date already exists", ex);
		}
	}

	private Sort parseSort(String sort) {
		if (sort == null || sort.isBlank()) {
			return Sort.by(Sort.Order.desc("effectiveDate"), Sort.Order.desc("updatedAt"));
		}
		String[] parts = sort.split(",");
		if (!"effectiveDate".equalsIgnoreCase(parts[0].trim())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only effectiveDate sorting is supported");
		}
		Sort.Direction direction = Sort.Direction.DESC;
		if (parts.length > 1) {
			String token = parts[1].trim();
			if ("asc".equalsIgnoreCase(token)) {
				direction = Sort.Direction.ASC;
			}
			else if (!"desc".equalsIgnoreCase(token)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Sort direction must be asc or desc");
			}
		}
		return Sort.by(new Sort.Order(direction, "effectiveDate"), Sort.Order.desc("updatedAt"));
	}

	private TenantExchangeRateItemDto toItemDto(TenantExchangeRateEntity row, Map<UUID, PlatformCurrencyEntity> currencies) {
		PlatformCurrencyEntity from = currencies.get(row.getFromCurrencyId());
		PlatformCurrencyEntity to = currencies.get(row.getToCurrencyId());
		if (from == null || to == null) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
					"One or more selected currencies are not active for this tenant");
		}
		return new TenantExchangeRateItemDto(row.getId(), from.getId(), from.getCode(), from.getDisplayName(), to.getId(),
				to.getCode(), to.getDisplayName(), row.getRate(), row.getEffectiveDate(), row.getCreatedAt(),
				row.getUpdatedAt());
	}

	private Map<UUID, PlatformCurrencyEntity> currencyMapFor(Collection<TenantExchangeRateEntity> rows) {
		Set<UUID> ids = new HashSet<>();
		for (TenantExchangeRateEntity row : rows) {
			ids.add(row.getFromCurrencyId());
			ids.add(row.getToCurrencyId());
		}
		Map<UUID, PlatformCurrencyEntity> map = new HashMap<>();
		if (ids.isEmpty()) {
			return map;
		}
		for (PlatformCurrencyEntity currency : platformCurrencyRepository.findAllById(ids)) {
			map.put(currency.getId(), currency);
		}
		return map;
	}

	private UUID requiredUuid(UUID value, String message) {
		if (value == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
		}
		return value;
	}

	private BigDecimal normalizeRate(BigDecimal rate) {
		if (rate == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rate is required");
		}
		if (rate.compareTo(BigDecimal.ZERO) <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rate must be greater than zero");
		}
		if (rate.scale() > 8) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rate supports up to 8 decimal places");
		}
		if (rate.precision() > 18) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rate exceeds DECIMAL(18,8) precision");
		}
		return rate;
	}

	private String normalizedCode(String value, String fieldName) {
		if (value == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
		}
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		if (!normalized.matches("^[A-Z]{3}$")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be an ISO-3 code");
		}
		return normalized;
	}

	public record UpdateResult(TenantExchangeRateItemDto item, Map<String, Object> changedFields) {
	}
}
