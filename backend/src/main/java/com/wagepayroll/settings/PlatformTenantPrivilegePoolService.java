package com.wagepayroll.settings;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import com.wagepayroll.domain.privilege.PrivilegeEntity;
import com.wagepayroll.domain.privilege.PrivilegeRepository;
import com.wagepayroll.domain.privilege.TenantPrivilegeAllowanceEntity;
import com.wagepayroll.domain.privilege.TenantPrivilegeAllowanceRepository;
import com.wagepayroll.domain.tenant.TenantRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformTenantPrivilegePoolService {

	private final TenantRepository tenantRepository;
	private final PrivilegeRepository privilegeRepository;
	private final TenantPrivilegeAllowanceRepository tenantPrivilegeAllowanceRepository;

	public PlatformTenantPrivilegePoolService(TenantRepository tenantRepository, PrivilegeRepository privilegeRepository,
			TenantPrivilegeAllowanceRepository tenantPrivilegeAllowanceRepository) {
		this.tenantRepository = tenantRepository;
		this.privilegeRepository = privilegeRepository;
		this.tenantPrivilegeAllowanceRepository = tenantPrivilegeAllowanceRepository;
	}

	/**
	 * Replaces all rows in {@code tenant_privilege_allowance} for the tenant. {@code codes} must reference
	 * existing global {@code privilege.code} values (non-empty).
	 */
	@Transactional
	public List<String> replacePool(UUID tenantId, List<String> codes) {
		if (codes == null || codes.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PRIVILEGE_POOL_NON_EMPTY");
		}
		tenantRepository.findById(tenantId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "UNKNOWN_TENANT"));
		LinkedHashSet<String> unique = new LinkedHashSet<>();
		for (String c : codes) {
			if (c == null || c.isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PRIVILEGE_CODE");
			}
			unique.add(c.trim());
		}
		List<PrivilegeEntity> resolved = new ArrayList<>();
		for (String code : unique) {
			PrivilegeEntity p = privilegeRepository.findByCode(code).orElseThrow(
					() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_PRIVILEGE_CODE"));
			resolved.add(p);
		}
		tenantPrivilegeAllowanceRepository.deleteByTenantId(tenantId);
		tenantPrivilegeAllowanceRepository.flush();
		Instant now = Instant.now();
		for (PrivilegeEntity p : resolved) {
			TenantPrivilegeAllowanceEntity row = new TenantPrivilegeAllowanceEntity();
			row.setId(UUID.randomUUID());
			row.setTenantId(tenantId);
			row.setPrivilegeId(p.getId());
			row.setCreatedAt(now);
			row.setUpdatedAt(now);
			tenantPrivilegeAllowanceRepository.save(row);
		}
		List<String> out = new ArrayList<>(unique);
		out.sort(String::compareTo);
		return out;
	}
}
