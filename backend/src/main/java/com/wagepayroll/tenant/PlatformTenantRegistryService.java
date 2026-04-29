package com.wagepayroll.tenant;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.wagepayroll.api.dto.PlatformTenantCreateRequest;
import com.wagepayroll.api.dto.PlatformTenantPatchRequest;
import com.wagepayroll.api.dto.PlatformTenantRowDto;
import com.wagepayroll.domain.tenant.TenantEntity;
import com.wagepayroll.domain.tenant.TenantRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformTenantRegistryService {

	private static final int MAX_PAGE_SIZE = 100;

	private final TenantRepository tenantRepository;
	private final TenantHandleValidator tenantHandleValidator;

	public PlatformTenantRegistryService(TenantRepository tenantRepository, TenantHandleValidator tenantHandleValidator) {
		this.tenantRepository = tenantRepository;
		this.tenantHandleValidator = tenantHandleValidator;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> list(int page, int size) {
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int safePage = Math.max(page, 0);
		Page<TenantEntity> p = tenantRepository.findAllByOrderByHandleAsc(PageRequest.of(safePage, safeSize));
		List<PlatformTenantRowDto> items = p.getContent().stream().map(PlatformTenantRegistryService::toRow).toList();
		Map<String, Object> out = new HashMap<>();
		out.put("items", items);
		out.put("totalElements", p.getTotalElements());
		out.put("page", p.getNumber());
		out.put("size", p.getSize());
		out.put("totalPages", p.getTotalPages());
		return out;
	}

	@Transactional(readOnly = true)
	public PlatformTenantRowDto get(UUID tenantId) {
		return tenantRepository.findById(tenantId).map(PlatformTenantRegistryService::toRow)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND"));
	}

	@Transactional
	public PlatformTenantRowDto create(PlatformTenantCreateRequest body) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		String handle = tenantHandleValidator.normalizeAndValidate(body.handle());
		String name = validateDisplayName(body.name());
		if (tenantRepository.findByHandle(handle).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "DUPLICATE_TENANT_HANDLE");
		}
		Instant now = Instant.now();
		TenantEntity e = new TenantEntity();
		e.setId(UUID.randomUUID());
		e.setHandle(handle);
		e.setName(name);
		e.setCreatedAt(now);
		e.setUpdatedAt(now);
		tenantRepository.save(e);
		return toRow(e);
	}

	@Transactional
	public PlatformTenantRowDto patch(UUID tenantId, PlatformTenantPatchRequest body) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		TenantEntity e = tenantRepository.findById(tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND"));
		String name = validateDisplayName(body.name());
		e.setName(name);
		e.setUpdatedAt(Instant.now());
		return toRow(e);
	}

	private static String validateDisplayName(String raw) {
		if (raw == null || !StringUtils.hasText(raw.trim())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TENANT_NAME");
		}
		String name = raw.trim();
		if (name.length() > 255) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TENANT_NAME");
		}
		return name;
	}

	private static PlatformTenantRowDto toRow(TenantEntity e) {
		return new PlatformTenantRowDto(e.getId(), e.getHandle(), e.getName(), e.getCreatedAt(), e.getUpdatedAt());
	}
}
