package com.wagepayroll.payrollbase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.wagepayroll.api.dto.PlatformWageComponentTemplateBaseEffectPutItem;
import com.wagepayroll.api.dto.PlatformWageComponentTemplateBaseEffectRowDto;
import com.wagepayroll.domain.payrollbase.PlatformPayrollBaseEntity;
import com.wagepayroll.domain.payrollbase.PlatformPayrollBaseRepository;
import com.wagepayroll.domain.payrollbase.PlatformWageComponentTemplateBaseEffectEntity;
import com.wagepayroll.domain.payrollbase.PlatformWageComponentTemplateBaseEffectRepository;
import com.wagepayroll.payroll.model.PayrollBaseEffectCalculationType;
import com.wagepayroll.payroll.model.PayrollBaseEffectDirection;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformWageComponentTemplateBaseEffectService {

	private final PlatformWageComponentTemplateBaseEffectRepository effectRepository;
	private final PlatformPayrollBaseRepository payrollBaseRepository;

	public PlatformWageComponentTemplateBaseEffectService(
			PlatformWageComponentTemplateBaseEffectRepository effectRepository,
			PlatformPayrollBaseRepository payrollBaseRepository) {
		this.effectRepository = effectRepository;
		this.payrollBaseRepository = payrollBaseRepository;
	}

	@Transactional(readOnly = true)
	public List<PlatformWageComponentTemplateBaseEffectRowDto> listForTemplate(UUID templateId) {
		return mapByTemplateIds(List.of(templateId)).getOrDefault(templateId, List.of());
	}

	@Transactional(readOnly = true)
	public Map<UUID, List<PlatformWageComponentTemplateBaseEffectRowDto>> mapByTemplateIds(Collection<UUID> templateIds) {
		if (templateIds == null || templateIds.isEmpty()) {
			return Map.of();
		}
		List<UUID> ids = templateIds.stream().distinct().toList();
		List<PlatformWageComponentTemplateBaseEffectEntity> rows = effectRepository
				.findByPlatformWageComponentTemplateIdIn(ids);
		Map<UUID, String> baseCodeById = payrollBaseRepository.findByActiveIsTrueOrderByCodeAsc().stream()
				.collect(Collectors.toMap(PlatformPayrollBaseEntity::getId, PlatformPayrollBaseEntity::getCode));
		Map<UUID, String> baseNameById = payrollBaseRepository.findByActiveIsTrueOrderByCodeAsc().stream()
				.collect(Collectors.toMap(PlatformPayrollBaseEntity::getId, PlatformPayrollBaseEntity::getName));
		Map<UUID, List<PlatformWageComponentTemplateBaseEffectRowDto>> out = new HashMap<>();
		for (PlatformWageComponentTemplateBaseEffectEntity e : rows) {
			if (!e.isActive()) {
				continue;
			}
			out.computeIfAbsent(e.getPlatformWageComponentTemplateId(), k -> new ArrayList<>())
					.add(toRow(e, baseCodeById.get(e.getPlatformPayrollBaseId()),
							baseNameById.get(e.getPlatformPayrollBaseId())));
		}
		for (List<PlatformWageComponentTemplateBaseEffectRowDto> list : out.values()) {
			list.sort(Comparator.comparing(PlatformWageComponentTemplateBaseEffectRowDto::payrollBaseCode));
		}
		return out;
	}

	@Transactional
	public List<PlatformWageComponentTemplateBaseEffectRowDto> replaceForTemplate(UUID templateId,
			List<PlatformWageComponentTemplateBaseEffectPutItem> items) {
		if (items == null) {
			return listForTemplate(templateId);
		}
		List<PlatformWageComponentTemplateBaseEffectPutItem> normalized = normalizeItems(items);
		Instant now = Instant.now();
		Set<UUID> seenBaseIds = new HashSet<>();
		for (PlatformWageComponentTemplateBaseEffectPutItem item : normalized) {
			if (!seenBaseIds.add(item.payrollBaseId())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DUPLICATE_PAYROLL_BASE_IN_EFFECTS");
			}
			PlatformPayrollBaseEntity base = payrollBaseRepository.findById(item.payrollBaseId())
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_PAYROLL_BASE"));
			if (!base.isActive()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAYROLL_BASE_INACTIVE");
			}
		}
		List<PlatformWageComponentTemplateBaseEffectEntity> existing = effectRepository
				.findByPlatformWageComponentTemplateId(templateId);
		Map<UUID, PlatformWageComponentTemplateBaseEffectEntity> existingByBase = existing.stream()
				.collect(Collectors.toMap(PlatformWageComponentTemplateBaseEffectEntity::getPlatformPayrollBaseId, e -> e,
						(a, b) -> a));
		Set<UUID> retainedBaseIds = new HashSet<>();
		for (PlatformWageComponentTemplateBaseEffectPutItem item : normalized) {
			PayrollBaseEffectDirection direction = parseDirection(item.effectDirection());
			PayrollBaseEffectCalculationType calcType = parseCalculationType(item.effectCalculationType());
			BigDecimal value = resolveEffectValue(direction, calcType, item.effectValue());
			int priority = item.priority() != null ? item.priority() : 0;
			PlatformWageComponentTemplateBaseEffectEntity e = existingByBase.get(item.payrollBaseId());
			if (e == null) {
				e = new PlatformWageComponentTemplateBaseEffectEntity();
				e.setId(UUID.randomUUID());
				e.setPlatformWageComponentTemplateId(templateId);
				e.setPlatformPayrollBaseId(item.payrollBaseId());
				e.setCreatedAt(now);
			}
			e.setEffectDirection(direction);
			e.setEffectCalculationType(calcType);
			e.setEffectValue(value);
			e.setPriority(priority);
			e.setActive(true);
			e.setUpdatedAt(now);
			effectRepository.save(e);
			retainedBaseIds.add(item.payrollBaseId());
		}
		for (PlatformWageComponentTemplateBaseEffectEntity e : existing) {
			if (!retainedBaseIds.contains(e.getPlatformPayrollBaseId())) {
				effectRepository.delete(e);
			}
		}
		return listForTemplate(templateId);
	}

	private static List<PlatformWageComponentTemplateBaseEffectPutItem> normalizeItems(
			List<PlatformWageComponentTemplateBaseEffectPutItem> items) {
		return items.stream()
				.filter(i -> i != null && i.payrollBaseId() != null)
				.toList();
	}

	private static PayrollBaseEffectDirection parseDirection(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_EFFECT_DIRECTION");
		}
		try {
			return PayrollBaseEffectDirection.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_EFFECT_DIRECTION");
		}
	}

	private static PayrollBaseEffectCalculationType parseCalculationType(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_EFFECT_CALCULATION_TYPE");
		}
		try {
			return PayrollBaseEffectCalculationType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_EFFECT_CALCULATION_TYPE");
		}
	}

	private static BigDecimal resolveEffectValue(PayrollBaseEffectDirection direction,
			PayrollBaseEffectCalculationType calcType, BigDecimal raw) {
		if (direction == PayrollBaseEffectDirection.IGNORE) {
			return raw != null ? raw : BigDecimal.ZERO;
		}
		return switch (calcType) {
			case FULL -> raw != null ? raw : new BigDecimal("100");
			case PERCENTAGE, FIXED -> {
				if (raw == null) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EFFECT_VALUE_REQUIRED");
				}
				yield raw;
			}
			case FORMULA -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EFFECT_FORMULA_NOT_SUPPORTED");
		};
	}

	private static PlatformWageComponentTemplateBaseEffectRowDto toRow(PlatformWageComponentTemplateBaseEffectEntity e,
			String baseCode, String baseName) {
		return new PlatformWageComponentTemplateBaseEffectRowDto(e.getId(), e.getPlatformPayrollBaseId(), baseCode,
				baseName, e.getEffectDirection().name(), e.getEffectCalculationType().name(), e.getEffectValue(),
				e.getPriority(), e.isActive());
	}
}
