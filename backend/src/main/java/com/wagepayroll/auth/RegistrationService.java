package com.wagepayroll.auth;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.wagepayroll.domain.membership.MembershipEntity;
import com.wagepayroll.domain.membership.MembershipRepository;
import com.wagepayroll.domain.privilege.PrivilegeEntity;
import com.wagepayroll.domain.privilege.PrivilegeRepository;
import com.wagepayroll.domain.privilege.TenantPrivilegeAllowanceEntity;
import com.wagepayroll.domain.privilege.TenantPrivilegeAllowanceRepository;
import com.wagepayroll.domain.role.RoleEntity;
import com.wagepayroll.domain.role.RolePrivilegeEntity;
import com.wagepayroll.domain.role.RolePrivilegeRepository;
import com.wagepayroll.domain.role.RoleRepository;
import com.wagepayroll.domain.role.UserRoleEntity;
import com.wagepayroll.domain.role.UserRoleRepository;
import com.wagepayroll.domain.roletemplate.RoleTemplateEntity;
import com.wagepayroll.domain.roletemplate.RoleTemplatePrivilegeRepository;
import com.wagepayroll.domain.roletemplate.RoleTemplateRepository;
import com.wagepayroll.domain.tenant.TenantEntity;
import com.wagepayroll.domain.tenant.TenantRepository;
import com.wagepayroll.domain.user.UserAccountEntity;
import com.wagepayroll.domain.user.UserAccountRepository;
import com.wagepayroll.tenant.TenantHandleValidator;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegistrationService {

	private final UserAccountRepository users;
	private final PasswordEncoder passwordEncoder;
	private final TenantRepository tenantRepository;
	private final TenantHandleValidator tenantHandleValidator;
	private final MembershipRepository membershipRepository;
	private final RoleRepository roleRepository;
	private final RolePrivilegeRepository rolePrivilegeRepository;
	private final UserRoleRepository userRoleRepository;
	private final RoleTemplateRepository roleTemplateRepository;
	private final RoleTemplatePrivilegeRepository roleTemplatePrivilegeRepository;
	private final PrivilegeRepository privilegeRepository;
	private final TenantPrivilegeAllowanceRepository tenantPrivilegeAllowanceRepository;

	public RegistrationService(UserAccountRepository users, PasswordEncoder passwordEncoder, TenantRepository tenantRepository,
			TenantHandleValidator tenantHandleValidator, MembershipRepository membershipRepository, RoleRepository roleRepository,
			RolePrivilegeRepository rolePrivilegeRepository, UserRoleRepository userRoleRepository,
			RoleTemplateRepository roleTemplateRepository, RoleTemplatePrivilegeRepository roleTemplatePrivilegeRepository,
			PrivilegeRepository privilegeRepository, TenantPrivilegeAllowanceRepository tenantPrivilegeAllowanceRepository) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tenantRepository = tenantRepository;
		this.tenantHandleValidator = tenantHandleValidator;
		this.membershipRepository = membershipRepository;
		this.roleRepository = roleRepository;
		this.rolePrivilegeRepository = rolePrivilegeRepository;
		this.userRoleRepository = userRoleRepository;
		this.roleTemplateRepository = roleTemplateRepository;
		this.roleTemplatePrivilegeRepository = roleTemplatePrivilegeRepository;
		this.privilegeRepository = privilegeRepository;
		this.tenantPrivilegeAllowanceRepository = tenantPrivilegeAllowanceRepository;
	}

	@Transactional
	public void register(String email, String rawPassword) {
		String normalized = email.trim().toLowerCase();
		if (users.findByEmailIgnoreCase(normalized).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED");
		}
		Instant now = Instant.now();
		UserAccountEntity u = new UserAccountEntity();
		u.setId(UUID.randomUUID());
		u.setEmail(normalized);
		u.setPasswordHash(passwordEncoder.encode(rawPassword));
		u.setPlatformSuperadmin(false);
		u.setPreferredLocale("en");
		u.setCreatedAt(now);
		u.setUpdatedAt(now);
		users.save(u);

		bootstrapTenantForNewAccount(u.getId(), normalized, now);
	}

	private void bootstrapTenantForNewAccount(UUID userId, String normalizedEmail, Instant now) {
		// 1) Tenant
		TenantEntity tenant = new TenantEntity();
		tenant.setId(UUID.randomUUID());
		tenant.setHandle(generateUniqueHandle(normalizedEmail));
		tenant.setName("Tenant " + tenant.getHandle());
		tenant.setCreatedAt(now);
		tenant.setUpdatedAt(now);
		tenantRepository.save(tenant);

		UUID tenantId = tenant.getId();

		// 2) Membership
		MembershipEntity m = new MembershipEntity();
		m.setId(UUID.randomUUID());
		m.setTenantId(tenantId);
		m.setUserId(userId);
		m.setCreatedAt(now);
		m.setUpdatedAt(now);
		m.setStatus("ACTIVE");
		membershipRepository.save(m);

		// 3) Copy templates → tenant roles + role_privilege
		List<RoleTemplateEntity> templates = roleTemplateRepository.findAll();
		templates.sort((a, b) -> a.getCode().compareToIgnoreCase(b.getCode()));

		Map<UUID, String> templateCodeById = new HashMap<>();
		for (RoleTemplateEntity t : templates) {
			templateCodeById.put(t.getId(), t.getCode());
		}

		// Resolve privilege ids for all codes once (fast enough for v1).
		Map<UUID, PrivilegeEntity> privilegeById = new HashMap<>();
		for (PrivilegeEntity p : privilegeRepository.findAll()) {
			privilegeById.put(p.getId(), p);
		}

		Map<UUID, UUID> templateIdToTenantRoleId = new HashMap<>();
		LinkedHashSet<UUID> ceilingPrivilegeIds = new LinkedHashSet<>();

		for (RoleTemplateEntity t : templates) {
			RoleEntity r = new RoleEntity();
			r.setId(UUID.randomUUID());
			r.setTenantId(tenantId);
			r.setName(t.getDisplayName());
			r.setCreatedAt(now);
			r.setUpdatedAt(now);
			roleRepository.save(r);
			templateIdToTenantRoleId.put(t.getId(), r.getId());

			List<UUID> privilegeIds = roleTemplatePrivilegeRepository.findPrivilegeIdsByTemplateId(t.getId());
			for (UUID privId : privilegeIds) {
				PrivilegeEntity p = privilegeById.get(privId);
				if (p == null) {
					continue;
				}
				ceilingPrivilegeIds.add(privId);
				RolePrivilegeEntity rp = new RolePrivilegeEntity();
				rp.setId(UUID.randomUUID());
				rp.setTenantId(tenantId);
				rp.setRoleId(r.getId());
				rp.setPrivilegeId(privId);
				rolePrivilegeRepository.save(rp);
			}
		}

		// 4) Tenant privilege allowance ceiling defaults: union of template privileges (v1).
		for (UUID privId : ceilingPrivilegeIds) {
			TenantPrivilegeAllowanceEntity a = new TenantPrivilegeAllowanceEntity();
			a.setId(UUID.randomUUID());
			a.setTenantId(tenantId);
			a.setPrivilegeId(privId);
			a.setCreatedAt(now);
			a.setUpdatedAt(now);
			tenantPrivilegeAllowanceRepository.save(a);
		}

		// 5) Assign new user the copied tenant Admin role
		UUID adminTemplateId = templates.stream().filter(t -> "ADMIN".equalsIgnoreCase(t.getCode())).map(RoleTemplateEntity::getId)
				.findFirst().orElse(null);
		if (adminTemplateId == null) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ROLE_TEMPLATE_MISSING_ADMIN");
		}
		UUID tenantAdminRoleId = templateIdToTenantRoleId.get(adminTemplateId);
		if (tenantAdminRoleId == null) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ROLE_TEMPLATE_COPY_FAILED");
		}
		UserRoleEntity ur = new UserRoleEntity();
		ur.setId(UUID.randomUUID());
		ur.setTenantId(tenantId);
		ur.setUserId(userId);
		ur.setRoleId(tenantAdminRoleId);
		ur.setCreatedAt(now);
		ur.setUpdatedAt(now);
		userRoleRepository.save(ur);

		// Defensive: ensure at least ROLE_VIEW / ROLE_EDIT are in the global catalog when templates include them.
		List<String> missingCodes = new ArrayList<>();
		for (UUID pid : ceilingPrivilegeIds) {
			if (!privilegeById.containsKey(pid)) {
				continue;
			}
			String code = privilegeById.get(pid).getCode();
			if (code == null || code.isBlank()) {
				continue;
			}
			if (privilegeRepository.findByCode(code).isEmpty()) {
				missingCodes.add(code);
			}
		}
		if (!missingCodes.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "PRIVILEGE_CATALOG_INCONSISTENT");
		}
	}

	private String generateUniqueHandle(String normalizedEmail) {
		String local = normalizedEmail;
		int at = normalizedEmail.indexOf('@');
		if (at > 0) {
			local = normalizedEmail.substring(0, at);
		}

		// Try a deterministic handle first, then suffix with randomness until unique.
		String base = local.replaceAll("[^a-z0-9-]+", "-").replaceAll("^-+", "").replaceAll("-+$", "");
		if (base.length() < 3) {
			base = "tenant";
		}
		if (base.length() > 32) {
			base = base.substring(0, 32);
		}

		for (int i = 0; i < 10; i++) {
			String candidate = i == 0 ? base : (base + "-" + UUID.randomUUID().toString().substring(0, 6));
			String validated;
			try {
				validated = tenantHandleValidator.normalizeAndValidate(candidate);
			}
			catch (ResponseStatusException e) {
				continue;
			}
			if (tenantRepository.findByHandle(validated).isEmpty()) {
				return validated;
			}
		}
		return tenantHandleValidator.normalizeAndValidate("t-" + UUID.randomUUID().toString().substring(0, 8));
	}
}
