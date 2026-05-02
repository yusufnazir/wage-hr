package com.wagepayroll.security;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.wagepayroll.domain.membership.MembershipEntity;
import com.wagepayroll.domain.membership.MembershipRepository;
import com.wagepayroll.domain.privilege.PrivilegeEntity;
import com.wagepayroll.domain.privilege.PrivilegeRepository;
import com.wagepayroll.domain.role.RolePrivilegeRepository;
import com.wagepayroll.domain.role.UserRoleRepository;
import com.wagepayroll.domain.user.UserAccountRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionService {

	private final MembershipRepository membershipRepository;
	private final PrivilegeRepository privilegeRepository;
	private final UserRoleRepository userRoleRepository;
	private final RolePrivilegeRepository rolePrivilegeRepository;
	private final UserAccountRepository userAccountRepository;

	public PermissionService(MembershipRepository membershipRepository, PrivilegeRepository privilegeRepository,
			UserRoleRepository userRoleRepository, RolePrivilegeRepository rolePrivilegeRepository,
			UserAccountRepository userAccountRepository) {
		this.membershipRepository = membershipRepository;
		this.privilegeRepository = privilegeRepository;
		this.userRoleRepository = userRoleRepository;
		this.rolePrivilegeRepository = rolePrivilegeRepository;
		this.userAccountRepository = userAccountRepository;
	}

	@Transactional(readOnly = true)
	public PrivilegeGrant evaluateTenantPrivilege(UUID userId, UUID tenantId, String privilegeCode) {
		Optional<MembershipEntity> membership = membershipRepository.findByTenantIdAndUserId(tenantId, userId);
		if (membership.isPresent()) {
			if (membershipRoleGrants(userId, tenantId, privilegeCode)) {
				return PrivilegeGrant.NORMAL;
			}
			if (isPlatformSuperadmin(userId) && privilegeRepository.existsByCode(privilegeCode)) {
				return PrivilegeGrant.SUPERADMIN_ELEVATED;
			}
			return PrivilegeGrant.DENIED;
		}
		if (!isPlatformSuperadmin(userId)) {
			throw new AccessDeniedException("No membership for tenant");
		}
		if (!privilegeRepository.existsByCode(privilegeCode)) {
			return PrivilegeGrant.DENIED;
		}
		return PrivilegeGrant.SUPERADMIN_ELEVATED;
	}

	@Transactional(readOnly = true)
	public boolean hasPrivilege(UUID userId, UUID tenantId, String privilegeCode) {
		return evaluateTenantPrivilege(userId, tenantId, privilegeCode) != PrivilegeGrant.DENIED;
	}

	private boolean membershipRoleGrants(UUID userId, UUID tenantId, String privilegeCode) {
		PrivilegeEntity priv = privilegeRepository.findByCode(privilegeCode).orElse(null);
		if (priv == null) {
			return false;
		}
		List<UUID> roles = userRoleRepository.findRoleIdsByUserAndTenant(userId, tenantId);
		for (UUID roleId : roles) {
			if (rolePrivilegeRepository.existsByTenantIdAndRoleIdAndPrivilegeId(tenantId, roleId, priv.getId())) {
				return true;
			}
		}
		return false;
	}

	@Transactional(readOnly = true)
	public List<String> effectivePrivilegeCodes(UUID userId, UUID tenantId) {
		if (membershipRepository.findByTenantIdAndUserId(tenantId, userId).isEmpty()) {
			if (!isPlatformSuperadmin(userId)) {
				throw new AccessDeniedException("No membership for tenant");
			}
			return allPrivilegeCodesSorted();
		}
		List<UUID> roles = userRoleRepository.findRoleIdsByUserAndTenant(userId, tenantId);
		Set<String> codes = new LinkedHashSet<>();
		for (UUID roleId : roles) {
			List<UUID> privilegeIds = rolePrivilegeRepository.findPrivilegeIdsByTenantIdAndRoleId(tenantId, roleId);
			for (UUID privilegeId : privilegeIds) {
				privilegeRepository.findById(privilegeId).map(PrivilegeEntity::getCode).ifPresent(codes::add);
			}
		}
		return new ArrayList<>(codes);
	}

	/**
	 * Global privilege catalog codes sorted ascending.
	 */
	@Transactional(readOnly = true)
	public List<String> tenantPoolPrivilegeCodes(UUID tenantId) {
		return allPrivilegeCodesSorted();
	}

	private List<String> allPrivilegeCodesSorted() {
		return privilegeRepository.findAllByOrderByCodeAsc().stream().map(PrivilegeEntity::getCode).toList();
	}

	private boolean isPlatformSuperadmin(UUID userId) {
		return userAccountRepository.findById(userId).map(u -> u.isPlatformSuperadmin()).orElse(false);
	}
}
