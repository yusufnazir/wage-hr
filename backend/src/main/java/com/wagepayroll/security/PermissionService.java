package com.wagepayroll.security;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.wagepayroll.domain.membership.MembershipEntity;
import com.wagepayroll.domain.membership.MembershipRepository;
import com.wagepayroll.domain.privilege.PrivilegeEntity;
import com.wagepayroll.domain.privilege.PrivilegeRepository;
import com.wagepayroll.domain.privilege.TenantPrivilegeAllowanceEntity;
import com.wagepayroll.domain.privilege.TenantPrivilegeAllowanceRepository;
import com.wagepayroll.domain.role.RolePrivilegeRepository;
import com.wagepayroll.domain.role.UserRoleRepository;
import com.wagepayroll.domain.user.UserAccountRepository;
import com.wagepayroll.subscription.SubscriptionGatingService;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionService {

	private final MembershipRepository membershipRepository;
	private final PrivilegeRepository privilegeRepository;
	private final TenantPrivilegeAllowanceRepository tenantPrivilegeAllowanceRepository;
	private final UserRoleRepository userRoleRepository;
	private final RolePrivilegeRepository rolePrivilegeRepository;
	private final UserAccountRepository userAccountRepository;
	private final SubscriptionGatingService subscriptionGatingService;

	public PermissionService(MembershipRepository membershipRepository, PrivilegeRepository privilegeRepository,
			TenantPrivilegeAllowanceRepository tenantPrivilegeAllowanceRepository, UserRoleRepository userRoleRepository,
			RolePrivilegeRepository rolePrivilegeRepository, UserAccountRepository userAccountRepository,
			SubscriptionGatingService subscriptionGatingService) {
		this.membershipRepository = membershipRepository;
		this.privilegeRepository = privilegeRepository;
		this.tenantPrivilegeAllowanceRepository = tenantPrivilegeAllowanceRepository;
		this.userRoleRepository = userRoleRepository;
		this.rolePrivilegeRepository = rolePrivilegeRepository;
		this.userAccountRepository = userAccountRepository;
		this.subscriptionGatingService = subscriptionGatingService;
	}

	@Transactional(readOnly = true)
	public PrivilegeGrant evaluateTenantPrivilege(UUID userId, UUID tenantId, String privilegeCode) {
		Optional<MembershipEntity> membership = membershipRepository.findByTenantIdAndUserId(tenantId, userId);
		if (membership.isPresent()) {
			if (membershipRolePoolGrants(userId, tenantId, privilegeCode)) {
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
		PrivilegeEntity priv = privilegeRepository.findByCode(privilegeCode).orElse(null);
		if (priv == null || !effectivePoolContains(tenantId, priv)) {
			return PrivilegeGrant.DENIED;
		}
		return PrivilegeGrant.SUPERADMIN_ELEVATED;
	}

	@Transactional(readOnly = true)
	public boolean hasPrivilege(UUID userId, UUID tenantId, String privilegeCode) {
		return evaluateTenantPrivilege(userId, tenantId, privilegeCode) != PrivilegeGrant.DENIED;
	}

	private boolean membershipRolePoolGrants(UUID userId, UUID tenantId, String privilegeCode) {
		PrivilegeEntity priv = privilegeRepository.findByCode(privilegeCode).orElse(null);
		if (priv == null) {
			return false;
		}
		if (!effectivePoolContains(tenantId, priv)) {
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
			return tenantPoolPrivilegeCodes(tenantId);
		}
		List<UUID> roles = userRoleRepository.findRoleIdsByUserAndTenant(userId, tenantId);
		List<TenantPrivilegeAllowanceEntity> allowances = tenantPrivilegeAllowanceRepository.findByTenantId(tenantId);
		Set<String> codes = new LinkedHashSet<>();
		for (TenantPrivilegeAllowanceEntity a : allowances) {
			PrivilegeEntity p = privilegeRepository.findById(a.getPrivilegeId()).orElse(null);
			if (p == null) {
				continue;
			}
			addIfRoleGrants(tenantId, roles, codes, p);
		}
		for (String code : subscriptionGatingService.subscriptionDerivedPrivilegeCodes(tenantId)) {
			privilegeRepository.findByCode(code).ifPresent(p -> addIfRoleGrants(tenantId, roles, codes, p));
		}
		return new ArrayList<>(codes);
	}

	private void addIfRoleGrants(UUID tenantId, List<UUID> roles, Set<String> codes, PrivilegeEntity p) {
		for (UUID roleId : roles) {
			if (rolePrivilegeRepository.existsByTenantIdAndRoleIdAndPrivilegeId(tenantId, roleId, p.getId())) {
				codes.add(p.getCode());
				return;
			}
		}
	}

	private boolean effectivePoolContains(UUID tenantId, PrivilegeEntity priv) {
		if (tenantPrivilegeAllowanceRepository.existsByTenantIdAndPrivilegeId(tenantId, priv.getId())) {
			return true;
		}
		return subscriptionGatingService.subscriptionCeilingContainsPrivilegeCode(tenantId, priv.getCode());
	}

	/**
	 * Effective tenant privilege <strong>ceiling</strong> codes: union of {@code tenant_privilege_allowance} and
	 * privileges implied by an {@code ACTIVE} commercial subscription ({@link com.wagepayroll.plans.PlanFeaturePrivilegeWiring}),
	 * sorted.
	 */
	@Transactional(readOnly = true)
	public List<String> tenantPoolPrivilegeCodes(UUID tenantId) {
		Set<String> codes = new TreeSet<>();
		for (TenantPrivilegeAllowanceEntity a : tenantPrivilegeAllowanceRepository.findByTenantId(tenantId)) {
			privilegeRepository.findById(a.getPrivilegeId()).map(PrivilegeEntity::getCode).ifPresent(codes::add);
		}
		codes.addAll(subscriptionGatingService.subscriptionDerivedPrivilegeCodes(tenantId));
		return new ArrayList<>(codes);
	}

	private boolean isPlatformSuperadmin(UUID userId) {
		return userAccountRepository.findById(userId).map(u -> u.isPlatformSuperadmin()).orElse(false);
	}
}
