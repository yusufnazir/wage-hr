package com.wagepayroll.tenant;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.wagepayroll.api.dto.TenantRoleOptionDto;
import com.wagepayroll.api.dto.TenantUserDetailDto;
import com.wagepayroll.api.dto.TenantUserListItemDto;
import com.wagepayroll.api.dto.TenantUserPatchRequest;
import com.wagepayroll.api.dto.TenantUserRoleAssignmentDto;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.common.email.EmailAddress;
import com.wagepayroll.domain.membership.MembershipEntity;
import com.wagepayroll.domain.membership.MembershipRepository;
import com.wagepayroll.domain.role.RoleEntity;
import com.wagepayroll.domain.role.RoleRepository;
import com.wagepayroll.domain.role.UserRoleEntity;
import com.wagepayroll.domain.role.UserRoleRepository;
import com.wagepayroll.domain.user.UserAccountEntity;
import com.wagepayroll.domain.user.UserAccountRepository;
import com.wagepayroll.security.PermissionService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantUserAdminService {

	public static final int MAX_PAGE_SIZE = 20;

	private final NamedParameterJdbcTemplate jdbc;
	private final MembershipRepository membershipRepository;
	private final UserAccountRepository userAccountRepository;
	private final UserRoleRepository userRoleRepository;
	private final RoleRepository roleRepository;
	private final PermissionService permissionService;
	private final AuditService auditService;

	public TenantUserAdminService(NamedParameterJdbcTemplate jdbc, MembershipRepository membershipRepository,
			UserAccountRepository userAccountRepository, UserRoleRepository userRoleRepository,
			RoleRepository roleRepository, PermissionService permissionService, AuditService auditService) {
		this.jdbc = jdbc;
		this.membershipRepository = membershipRepository;
		this.userAccountRepository = userAccountRepository;
		this.userRoleRepository = userRoleRepository;
		this.roleRepository = roleRepository;
		this.permissionService = permissionService;
		this.auditService = auditService;
	}

	public void assertCanViewUser(UUID actorUserId, UUID tenantId, UUID targetUserId) {
		if (!membershipRepository.findByTenantIdAndUserId(tenantId, targetUserId).isPresent()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_NOT_IN_TENANT");
		}
		boolean self = actorUserId.equals(targetUserId);
		boolean ok = permissionService.hasPrivilege(actorUserId, tenantId, "USER_EDIT")
				|| (self && permissionService.hasPrivilege(actorUserId, tenantId, "USER_VIEW"));
		if (!ok) {
			throw new AccessDeniedException("Missing privilege for user detail");
		}
	}

	@Transactional(readOnly = true)
	public Map<String, Object> list(UUID tenantId, int page, int size, String sortToken, String emailFilter,
			String statusFilter, String roleNameFilter) {
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int safePage = Math.max(page, 0);
		SortSpec sort = SortSpec.parse(sortToken);
		boolean emailBlank = !StringUtils.hasText(emailFilter);
		boolean statusBlank = !StringUtils.hasText(statusFilter);
		boolean roleBlank = !StringUtils.hasText(roleNameFilter);
		MapSqlParameterSource p = new MapSqlParameterSource();
		p.addValue("tenantId", tenantId.toString());
		p.addValue("emailFilterBlank", emailBlank);
		if (!emailBlank) {
			p.addValue("emailFilter", emailFilter.trim());
		}
		else {
			p.addValue("emailFilter", "");
		}
		p.addValue("statusFilterBlank", statusBlank);
		if (!statusBlank) {
			p.addValue("statusFilter", statusFilter.trim());
		}
		else {
			p.addValue("statusFilter", "");
		}
		p.addValue("roleFilterBlank", roleBlank);
		if (!roleBlank) {
			p.addValue("roleFilter", roleNameFilter.trim());
		}
		else {
			p.addValue("roleFilter", "");
		}
		String where = """
				m.tenant_id = :tenantId
				AND (:emailFilterBlank = TRUE OR LOWER(ua.email) LIKE LOWER(CONCAT('%', :emailFilter, '%')))
				AND (:statusFilterBlank = TRUE OR m.status = :statusFilter)
				AND (:roleFilterBlank = TRUE OR EXISTS (
				  SELECT 1 FROM user_role urf INNER JOIN role rf ON rf.id = urf.role_id AND rf.tenant_id = m.tenant_id
				  WHERE urf.tenant_id = m.tenant_id AND urf.user_id = m.user_id AND rf.name = :roleFilter
				))
				""";
		String countSql = "SELECT COUNT(*) FROM membership m INNER JOIN user_account ua ON ua.id = m.user_id WHERE "
				+ where;
		Long total = jdbc.queryForObject(countSql, p, Long.class);
		long totalElements = total == null ? 0L : total;
		int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
		p.addValue("limit", safeSize);
		p.addValue("offset", (long) safePage * safeSize);
		String listSql = """
				SELECT m.user_id AS user_id, ua.email AS email, m.status AS status, m.last_active_at AS last_active_at,
				(SELECT GROUP_CONCAT(r.name ORDER BY r.name SEPARATOR ',')
				 FROM user_role ur INNER JOIN role r ON r.id = ur.role_id AND r.tenant_id = m.tenant_id
				 WHERE ur.tenant_id = m.tenant_id AND ur.user_id = m.user_id) AS role_names_csv
				FROM membership m
				INNER JOIN user_account ua ON ua.id = m.user_id
				WHERE %s
				ORDER BY %s
				LIMIT :limit OFFSET :offset
				""".formatted(where, sort.orderBySql());
		List<TenantUserListItemDto> items = jdbc.query(listSql, p, (rs, rowNum) -> {
			UUID userId = UUID.fromString(rs.getString("user_id"));
			String csv = rs.getString("role_names_csv");
			List<String> roleNames = new ArrayList<>();
			if (csv != null && !csv.isEmpty()) {
				roleNames.addAll(Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
			}
			Timestamp ts = rs.getTimestamp("last_active_at");
			Instant last = ts == null ? null : ts.toInstant();
			return new TenantUserListItemDto(userId, rs.getString("email"), rs.getString("status"), last, roleNames);
		});
		Map<String, Object> out = new HashMap<>();
		out.put("items", items);
		out.put("totalElements", totalElements);
		out.put("page", safePage);
		out.put("size", safeSize);
		out.put("totalPages", totalPages);
		return out;
	}

	@Transactional(readOnly = true)
	public TenantUserDetailDto getDetail(UUID tenantId, UUID targetUserId, UUID actorUserId) {
		MembershipEntity mem = membershipRepository.findByTenantIdAndUserId(tenantId, targetUserId).orElseThrow();
		UserAccountEntity user = userAccountRepository.findById(targetUserId).orElseThrow();
		List<UserRoleEntity> urs = userRoleRepository.findByTenantIdAndUserId(tenantId, targetUserId);
		Map<UUID, String> roleNamesById = roleRepository.findByTenantId(tenantId).stream()
				.collect(Collectors.toMap(RoleEntity::getId, RoleEntity::getName));
		List<TenantUserRoleAssignmentDto> assignments = urs.stream()
				.map(ur -> new TenantUserRoleAssignmentDto(ur.getRoleId(),
						Objects.requireNonNullElse(roleNamesById.get(ur.getRoleId()), "?")))
				.sorted(Comparator.comparing(TenantUserRoleAssignmentDto::roleName, String.CASE_INSENSITIVE_ORDER))
				.toList();
		List<String> roleNames = assignments.stream().map(TenantUserRoleAssignmentDto::roleName).toList();
		boolean editor = permissionService.hasPrivilege(actorUserId, tenantId, "USER_EDIT");
		List<TenantRoleOptionDto> assignable = editor
				? roleRepository.findByTenantId(tenantId).stream()
						.sorted(Comparator.comparing(RoleEntity::getName, String.CASE_INSENSITIVE_ORDER))
						.map(r -> new TenantRoleOptionDto(r.getId(), r.getName()))
						.toList()
				: List.of();
		return new TenantUserDetailDto(user.getId(), user.getEmail(), mem.getStatus(), mem.getLastActiveAt(), roleNames,
				assignments, assignable);
	}

	@Transactional(readOnly = true)
	public List<TenantRoleOptionDto> listTenantRoleOptions(UUID tenantId) {
		return roleRepository.findByTenantId(tenantId).stream()
				.sorted(Comparator.comparing(RoleEntity::getName, String.CASE_INSENSITIVE_ORDER))
				.map(r -> new TenantRoleOptionDto(r.getId(), r.getName()))
				.toList();
	}

	@Transactional
	public void patch(UUID tenantId, UUID targetUserId, UUID actorUserId, TenantUserPatchRequest body,
			HttpServletRequest request) {
		if (body == null || (body.email() == null && body.roleIds() == null)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EMPTY_PATCH");
		}
		MembershipEntity mem = membershipRepository.findByTenantIdAndUserId(tenantId, targetUserId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_NOT_IN_TENANT"));
		UserAccountEntity user = userAccountRepository.findById(targetUserId).orElseThrow();
		List<UUID> currentRoleIds = new ArrayList<>(userRoleRepository.findRoleIdsByUserAndTenant(targetUserId, tenantId));
		Collections.sort(currentRoleIds);
		if (body.roleIds() != null) {
			if (actorUserId.equals(targetUserId)) {
				List<UUID> sortedRequested = new ArrayList<>(body.roleIds());
				Collections.sort(sortedRequested);
				if (!sortedRequested.equals(currentRoleIds)) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CANNOT_CHANGE_OWN_ROLES");
				}
			}
		}
		String rid = RequestIdFilter.currentRequestId(request);
		Instant now = Instant.now();
		if (body.email() != null) {
			String normalized = EmailAddress.normalizeAndValidate(body.email());
			userAccountRepository.findByEmailIgnoreCase(normalized).filter(u -> !u.getId().equals(targetUserId))
					.ifPresent(u -> {
						throw new ResponseStatusException(HttpStatus.CONFLICT, "EMAIL_IN_USE");
					});
			if (!normalized.equalsIgnoreCase(user.getEmail())) {
				user.setEmail(normalized);
				user.setUpdatedAt(now);
				userAccountRepository.save(user);
				auditService.append(tenantId, actorUserId, AuditActionCodes.TENANT_USER_EMAIL_UPDATED,
						AuditResourceTypes.USER_ACCOUNT, targetUserId.toString(), rid, Map.of());
			}
		}
		if (body.roleIds() != null && !actorUserId.equals(targetUserId)) {
			Set<UUID> allowed = roleRepository.findByTenantId(tenantId).stream().map(RoleEntity::getId)
					.collect(Collectors.toSet());
			for (UUID requestedRoleId : body.roleIds()) {
				if (!allowed.contains(requestedRoleId)) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_ROLE");
				}
			}
			List<UUID> sortedNew = new ArrayList<>(new HashSet<>(body.roleIds())).stream().sorted().toList();
			if (!sortedNew.equals(currentRoleIds)) {
				userRoleRepository.deleteByTenantIdAndUserId(tenantId, targetUserId);
				for (UUID roleId : sortedNew) {
					UserRoleEntity ur = new UserRoleEntity();
					ur.setId(UUID.randomUUID());
					ur.setTenantId(tenantId);
					ur.setUserId(targetUserId);
					ur.setRoleId(roleId);
					ur.setCreatedAt(now);
					ur.setUpdatedAt(now);
					userRoleRepository.save(ur);
				}
				mem.setUpdatedAt(now);
				membershipRepository.save(mem);
				auditService.append(tenantId, actorUserId, AuditActionCodes.TENANT_USER_ROLES_REPLACED,
						AuditResourceTypes.USER_ACCOUNT, targetUserId.toString(), rid,
						Map.of("roleCount", sortedNew.size()));
			}
		}
	}

	private enum SortField {
		EMAIL,
		LAST_ACTIVE,
		STATUS,
		ROLES
	}

	private enum SortDir {
		ASC,
		DESC
	}

	private record SortSpec(SortField field, SortDir dir) {

		static SortSpec parse(String token) {
			if (!StringUtils.hasText(token)) {
				return new SortSpec(SortField.EMAIL, SortDir.ASC);
			}
			String t = token.trim().toUpperCase();
			int us = t.lastIndexOf('_');
			if (us < 1 || us >= t.length() - 1) {
				return new SortSpec(SortField.EMAIL, SortDir.ASC);
			}
			String f = t.substring(0, us);
			String d = t.substring(us + 1);
			SortDir dir = "DESC".equals(d) ? SortDir.DESC : SortDir.ASC;
			SortField field = switch (f) {
				case "LAST_ACTIVE" -> SortField.LAST_ACTIVE;
				case "STATUS" -> SortField.STATUS;
				case "ROLES" -> SortField.ROLES;
				case "EMAIL" -> SortField.EMAIL;
				default -> SortField.EMAIL;
			};
			return new SortSpec(field, dir);
		}

		String orderBySql() {
			String direction = dir == SortDir.DESC ? "DESC" : "ASC";
			return switch (field) {
				case EMAIL -> "ua.email " + direction + ", m.user_id ASC";
				case LAST_ACTIVE -> "(m.last_active_at IS NULL) ASC, m.last_active_at " + direction + ", m.user_id ASC";
				case STATUS -> "m.status " + direction + ", m.user_id ASC";
				case ROLES -> rolesSortExpr() + " " + direction + ", m.user_id ASC";
			};
		}

		private static String rolesSortExpr() {
			return "(SELECT MIN(r2.name) FROM user_role ur2 INNER JOIN role r2 ON r2.id = ur2.role_id AND r2.tenant_id = m.tenant_id WHERE ur2.tenant_id = m.tenant_id AND ur2.user_id = m.user_id)";
		}
	}
}
