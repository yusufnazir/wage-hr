package com.wagepayroll.payrollbase;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.wagepayroll.api.dto.PlatformPayrollBaseCreateRequest;
import com.wagepayroll.api.dto.PlatformPayrollBasePutRequest;
import com.wagepayroll.api.dto.PlatformPayrollBaseRowDto;
import com.wagepayroll.domain.payrollbase.PlatformPayrollBaseEntity;
import com.wagepayroll.domain.payrollbase.PlatformPayrollBaseRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformPayrollBaseAdminService {

	private static final int MAX_PAGE_SIZE = 100;

	private static final int MAX_CODE_LEN = 50;

	private static final int MAX_NAME_LEN = 255;

	private static final Set<String> ALLOWED_CATEGORIES = Set.of("TAX", "CONTRIBUTION", "ACCRUAL", "NET", "GROSS",
			"STATUTORY");

	private final PlatformPayrollBaseRepository repository;

	public PlatformPayrollBaseAdminService(PlatformPayrollBaseRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> list(int page, int size, String categoryFilter, Boolean activeFilter, String search) {
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int safePage = Math.max(page, 0);
		Specification<PlatformPayrollBaseEntity> spec = (root, q, cb) -> cb.conjunction();
		if (categoryFilter != null && !categoryFilter.isBlank()) {
			String category = normalizeCategory(categoryFilter);
			spec = spec.and((root, q, cb) -> cb.equal(root.get("category"), category));
		}
		if (activeFilter != null) {
			spec = spec.and((root, q, cb) -> cb.equal(root.get("active"), activeFilter.booleanValue()));
		}
		if (search != null && !search.isBlank()) {
			String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
			spec = spec.and((root, q, cb) -> cb.or(
					cb.like(cb.lower(root.get("code")), term),
					cb.like(cb.lower(root.get("name")), term)));
		}
		Page<PlatformPayrollBaseEntity> p = repository.findAll(spec,
				PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.asc("code"))));
		List<PlatformPayrollBaseRowDto> items = p.getContent().stream().map(this::toRow).toList();
		Map<String, Object> out = new HashMap<>();
		out.put("items", items);
		out.put("totalElements", p.getTotalElements());
		out.put("page", p.getNumber());
		out.put("size", p.getSize());
		out.put("totalPages", p.getTotalPages());
		return out;
	}

	@Transactional(readOnly = true)
	public PlatformPayrollBaseRowDto get(UUID id) {
		return toRow(repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
	}

	@Transactional
	public PlatformPayrollBaseRowDto create(PlatformPayrollBaseCreateRequest body) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		String code = normalizeCode(body.code());
		String name = requireName(body.name());
		String category = normalizeOptionalCategory(body.category());
		boolean active = body.active() == null || body.active().booleanValue();
		if (repository.existsByCode(code)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "PLATFORM_PAYROLL_BASE_CODE_EXISTS");
		}
		Instant now = Instant.now();
		PlatformPayrollBaseEntity e = new PlatformPayrollBaseEntity();
		e.setId(UUID.randomUUID());
		e.setCode(code);
		e.setName(name);
		e.setCategory(category);
		e.setActive(active);
		e.setCreatedAt(now);
		e.setUpdatedAt(now);
		return toRow(repository.save(e));
	}

	@Transactional
	public PlatformPayrollBaseRowDto update(UUID id, PlatformPayrollBasePutRequest body) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		PlatformPayrollBaseEntity e = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		e.setName(requireName(body.name()));
		e.setCategory(normalizeOptionalCategory(body.category()));
		if (body.active() != null) {
			e.setActive(body.active().booleanValue());
		}
		e.setUpdatedAt(Instant.now());
		return toRow(repository.save(e));
	}

	@Transactional
	public PlatformPayrollBaseRowDto activate(UUID id) {
		PlatformPayrollBaseEntity e = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		e.setActive(true);
		e.setUpdatedAt(Instant.now());
		return toRow(repository.save(e));
	}

	@Transactional
	public PlatformPayrollBaseRowDto deactivate(UUID id) {
		PlatformPayrollBaseEntity e = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		e.setActive(false);
		e.setUpdatedAt(Instant.now());
		return toRow(repository.save(e));
	}

	private PlatformPayrollBaseRowDto toRow(PlatformPayrollBaseEntity e) {
		return new PlatformPayrollBaseRowDto(e.getId(), e.getCode(), e.getName(), e.getCategory(), e.isActive(),
				e.getCreatedAt(), e.getUpdatedAt());
	}

	private static String normalizeCode(String raw) {
		if (!StringUtils.hasText(raw)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_CODE");
		}
		String code = raw.trim().toUpperCase(Locale.ROOT);
		if (code.length() > MAX_CODE_LEN || !code.matches("[A-Z0-9_]+")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_CODE");
		}
		return code;
	}

	private static String requireName(String raw) {
		if (!StringUtils.hasText(raw)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_NAME");
		}
		String name = raw.trim();
		if (name.length() > MAX_NAME_LEN) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_NAME");
		}
		return name;
	}

	private static String normalizeOptionalCategory(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		return normalizeCategory(raw);
	}

	private static String normalizeCategory(String raw) {
		String category = raw.trim().toUpperCase(Locale.ROOT);
		if (!ALLOWED_CATEGORIES.contains(category)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY");
		}
		return category;
	}
}
