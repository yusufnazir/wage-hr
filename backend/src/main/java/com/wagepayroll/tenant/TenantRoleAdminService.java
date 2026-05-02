package com.wagepayroll.tenant;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.wagepayroll.api.dto.TenantRoleCreateRequest;
import com.wagepayroll.api.dto.TenantRoleDetailResponseDto;
import com.wagepayroll.api.dto.TenantRoleDto;
import com.wagepayroll.api.dto.TenantRoleListItemDto;
import com.wagepayroll.api.dto.TenantRolePatchRequest;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.domain.privilege.PrivilegeEntity;
import com.wagepayroll.domain.privilege.PrivilegeRepository;
import com.wagepayroll.domain.role.RoleEntity;
import com.wagepayroll.domain.role.RolePrivilegeEntity;
import com.wagepayroll.domain.role.RolePrivilegeRepository;
import com.wagepayroll.domain.role.RoleRepository;
import com.wagepayroll.domain.role.UserRoleRepository;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantRoleAdminService {

	private static final int ROLE_NAME_MIN_LEN = 1;
	private static final int ROLE_NAME_MAX_LEN = 128;

	private final NamedParameterJdbcTemplate jdbc;
	private final RoleRepository roleRepository;
	private final RolePrivilegeRepository rolePrivilegeRepository;
	private final UserRoleRepository userRoleRepository;
	private final PrivilegeRepository privilegeRepository;
	private final AuditService auditService;

	public TenantRoleAdminService(NamedParameterJdbcTemplate jdbc, RoleRepository roleRepository,
			RolePrivilegeRepository rolePrivilegeRepository, UserRoleRepository userRoleRepository,
			PrivilegeRepository privilegeRepository, AuditService auditService) {
		this.jdbc = jdbc;
		this.roleRepository = roleRepository;
		this.rolePrivilegeRepository = rolePrivilegeRepository;
		this.userRoleRepository = userRoleRepository;
		this.privilegeRepository = privilegeRepository;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> list(UUID tenantId, String q, String sortToken) {
		String safeSort = StringUtils.hasText(sortToken) ? sortToken.trim().toUpperCase() : "NAME_ASC";
		boolean desc = "NAME_DESC".equals(safeSort);
		String orderBy = desc ? "r.name DESC" : "r.name ASC";

		boolean qBlank = !StringUtils.hasText(q);
		MapSqlParameterSource p = new MapSqlParameterSource();
		p.addValue("tenantId", tenantId.toString());
		p.addValue("qBlank", qBlank);
		p.addValue("q", qBlank ? "" : q.trim());

		String sql = """
				SELECT r.id AS role_id, r.name AS role_name,
				  (SELECT GROUP_CONCAT(p2.code ORDER BY p2.code SEPARATOR ',')
				   FROM role_privilege rp2
				   INNER JOIN privilege p2 ON p2.id = rp2.privilege_id
				   WHERE rp2.tenant_id = r.tenant_id AND rp2.role_id = r.id) AS privilege_codes_csv
				FROM role r
				WHERE r.tenant_id = :tenantId
				  AND (:qBlank = TRUE OR LOWER(r.name) LIKE LOWER(CONCAT('%%', :q, '%%')))
				ORDER BY %s, r.id ASC
				""".formatted(orderBy);

		List<TenantRoleListItemDto> items = jdbc.query(sql, p, (rs, rowNum) -> {
			UUID roleId = UUID.fromString(rs.getString("role_id"));
			String name = rs.getString("role_name");
			String csv = rs.getString("privilege_codes_csv");
			List<String> codes = splitCsv(csv);
			return new TenantRoleListItemDto(roleId, name, codes);
		});

		return Map.of("items", items);
	}

	@Transactional(readOnly = true)
	public TenantRoleDetailResponseDto getOne(UUID tenantId, UUID roleId) {
		RoleEntity role = roleRepository.findByTenantIdAndId(tenantId, roleId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND"));
		List<String> privilegeCodes = privilegeCodesForRole(tenantId, roleId);
		List<String> assignable = privilegeRepository.findAllByOrderByCodeAsc().stream().map(PrivilegeEntity::getCode).toList();
		return new TenantRoleDetailResponseDto(new TenantRoleDto(role.getId(), role.getName(), privilegeCodes), assignable);
	}

	@Transactional
	public TenantRoleDto create(UUID tenantId, UUID actorUserId, TenantRoleCreateRequest body, HttpServletRequest request) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
		}
		String name = normalizeAndValidateName(body.name());
		if (roleNameExistsCaseInsensitive(tenantId, name, null)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "ROLE_NAME_IN_USE");
		}

		Instant now = Instant.now();
		RoleEntity role = new RoleEntity();
		role.setId(UUID.randomUUID());
		role.setTenantId(tenantId);
		role.setName(name);
		role.setCreatedAt(now);
		role.setUpdatedAt(now);
		roleRepository.save(role);

		List<String> requestedCodes = body.privilegeCodes() == null ? List.of() : body.privilegeCodes();
		List<String> normalizedCodes = normalizeAndDedupeCodes(requestedCodes);
		replaceRolePrivilegesOrThrow(tenantId, role.getId(), normalizedCodes, actorUserId, true);

		String rid = RequestIdFilter.currentRequestId(request);
		auditService.append(tenantId, actorUserId, AuditActionCodes.TENANT_ROLE_CREATED, AuditResourceTypes.ROLE,
				role.getId().toString(), rid, Map.of("name", name, "privilegeCount", normalizedCodes.size()));

		return new TenantRoleDto(role.getId(), role.getName(), privilegeCodesForRole(tenantId, role.getId()));
	}

	@Transactional
	public TenantRoleDto patch(UUID tenantId, UUID roleId, UUID actorUserId, TenantRolePatchRequest body,
			HttpServletRequest request) {
		if (body == null || (body.name() == null && body.privilegeCodes() == null)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EMPTY_PATCH");
		}
		RoleEntity role = roleRepository.findByTenantIdAndId(tenantId, roleId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND"));

		boolean changed = false;
		Instant now = Instant.now();

		if (body.name() != null) {
			String name = normalizeAndValidateName(body.name());
			if (!name.equals(role.getName())) {
				if (roleNameExistsCaseInsensitive(tenantId, name, roleId)) {
					throw new ResponseStatusException(HttpStatus.CONFLICT, "ROLE_NAME_IN_USE");
				}
				role.setName(name);
				changed = true;
			}
		}

		if (body.privilegeCodes() != null) {
			List<String> normalizedCodes = normalizeAndDedupeCodes(body.privilegeCodes());
			replaceRolePrivilegesOrThrow(tenantId, roleId, normalizedCodes, actorUserId, false);
			changed = true;
		}

		if (changed) {
			role.setUpdatedAt(now);
			roleRepository.save(role);
			String rid = RequestIdFilter.currentRequestId(request);
			auditService.append(tenantId, actorUserId, AuditActionCodes.TENANT_ROLE_UPDATED, AuditResourceTypes.ROLE,
					roleId.toString(), rid,
					Map.of("name", role.getName(), "touched", touchedFields(body), "mutatedAt", Timestamp.from(now).toString()));
		}

		return new TenantRoleDto(role.getId(), role.getName(), privilegeCodesForRole(tenantId, roleId));
	}

	private static List<String> touchedFields(TenantRolePatchRequest body) {
		List<String> fields = new ArrayList<>();
		if (body.name() != null) {
			fields.add("name");
		}
		if (body.privilegeCodes() != null) {
			fields.add("privilegeCodes");
		}
		return fields;
	}

	private List<String> privilegeCodesForRole(UUID tenantId, UUID roleId) {
		List<UUID> privIds = rolePrivilegeRepository.findPrivilegeIdsByTenantIdAndRoleId(tenantId, roleId);
		if (privIds.isEmpty()) {
			return List.of();
		}
		Set<UUID> idSet = new HashSet<>(privIds);
		return privilegeRepository.findAllById(idSet).stream()
				.map(PrivilegeEntity::getCode)
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();
	}

	private void replaceRolePrivilegesOrThrow(UUID tenantId, UUID roleId, List<String> privilegeCodes, UUID actorUserId,
			boolean creating) {
		Map<String, UUID> codeToId = new HashMap<>();
		for (String code : privilegeCodes) {
			PrivilegeEntity p = privilegeRepository.findByCode(code).orElse(null);
			if (p == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_PRIVILEGE_CODE");
			}
			codeToId.put(code, p.getId());
		}

		// v1 self-lockout prevention: do not allow removing the caller's last effective ROLE_EDIT source.
		if (!creating) {
			enforceSelfLockoutPrevention(tenantId, roleId, actorUserId, privilegeCodes, codeToId.get("ROLE_EDIT"));
		}

		rolePrivilegeRepository.deleteByTenantIdAndRoleId(tenantId, roleId);
		for (UUID privId : codeToId.values()) {
			RolePrivilegeEntity rp = new RolePrivilegeEntity();
			rp.setId(UUID.randomUUID());
			rp.setTenantId(tenantId);
			rp.setRoleId(roleId);
			rp.setPrivilegeId(privId);
			// role_privilege table has no timestamps in entity spec; align to schema.
			rolePrivilegeRepository.save(rp);
		}
	}

	private void enforceSelfLockoutPrevention(UUID tenantId, UUID roleId, UUID actorUserId, List<String> requestedCodes,
			UUID roleEditPrivId) {
		// If ROLE_EDIT isn't even a known privilege, deny safe (misconfigured catalog).
		if (roleEditPrivId == null) {
			PrivilegeEntity p = privilegeRepository.findByCode("ROLE_EDIT").orElse(null);
			if (p == null) {
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PRIVILEGE_CATALOG_MISSING_ROLE_EDIT");
			}
			roleEditPrivId = p.getId();
		}
		List<UUID> actorRoleIds = userRoleRepository.findRoleIdsByUserAndTenant(actorUserId, tenantId);
		if (!actorRoleIds.contains(roleId)) {
			return;
		}
		boolean requestedHasRoleEdit = requestedCodes.contains("ROLE_EDIT");
		if (requestedHasRoleEdit) {
			return;
		}
		for (UUID otherRoleId : actorRoleIds) {
			if (otherRoleId.equals(roleId)) {
				continue;
			}
			if (rolePrivilegeRepository.existsByTenantIdAndRoleIdAndPrivilegeId(tenantId, otherRoleId, roleEditPrivId)) {
				return;
			}
		}
		throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CANNOT_LOCK_OUT_SELF");
	}

	private boolean roleNameExistsCaseInsensitive(UUID tenantId, String normalizedName, UUID excludeRoleId) {
		MapSqlParameterSource p = new MapSqlParameterSource();
		p.addValue("tenantId", tenantId.toString());
		p.addValue("name", normalizedName);
		p.addValue("excludeBlank", excludeRoleId == null);
		p.addValue("excludeId", excludeRoleId == null ? "" : excludeRoleId.toString());
		String sql = """
				SELECT COUNT(*) FROM role r
				WHERE r.tenant_id = :tenantId
				  AND LOWER(r.name) = LOWER(:name)
				  AND (:excludeBlank = TRUE OR r.id <> :excludeId)
				""";
		Long n = jdbc.queryForObject(sql, p, Long.class);
		return (n != null && n.longValue() > 0L);
	}

	private static String normalizeAndValidateName(String raw) {
		if (raw == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ROLE_NAME_REQUIRED");
		}
		String name = raw.trim();
		if (name.length() < ROLE_NAME_MIN_LEN || name.length() > ROLE_NAME_MAX_LEN) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ROLE_NAME_INVALID");
		}
		return name;
	}

	private static List<String> normalizeAndDedupeCodes(List<String> raw) {
		if (raw == null) {
			return List.of();
		}
		Set<String> out = new HashSet<>();
		for (String s : raw) {
			if (!StringUtils.hasText(s)) {
				continue;
			}
			out.add(s.trim().toUpperCase());
		}
		return out.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
	}

	private static List<String> splitCsv(String csv) {
		if (csv == null || csv.isEmpty()) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		for (String s : csv.split(",")) {
			String t = s.trim();
			if (!t.isEmpty()) {
				out.add(t);
			}
		}
		out.sort(String.CASE_INSENSITIVE_ORDER);
		return out;
	}
}

