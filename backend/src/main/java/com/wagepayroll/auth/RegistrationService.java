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
import com.wagepayroll.domain.navmenu.NavMenuItemEntity;
import com.wagepayroll.domain.navmenu.NavMenuItemRepository;
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
import com.wagepayroll.domain.setting.PlatformSettingRepository;
import com.wagepayroll.domain.tenant.TenantEntity;
import com.wagepayroll.domain.tenant.TenantRepository;
import com.wagepayroll.api.dto.RegisterRequest;
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

	public static final String PLATFORM_KEY_DEFAULT_ROLE_TEMPLATE = "auth.registration.default_role_template_code";

	private final UserAccountRepository users;
	private final PasswordEncoder passwordEncoder;
	private final TenantRepository tenantRepository;
	private final TenantHandleValidator tenantHandleValidator;
	private final MembershipRepository membershipRepository;
	private final RoleRepository roleRepository;
	private final RolePrivilegeRepository rolePrivilegeRepository;
	private final UserRoleRepository userRoleRepository;
	private final NavMenuItemRepository navMenuItemRepository;
	private final RoleTemplateRepository roleTemplateRepository;
	private final RoleTemplatePrivilegeRepository roleTemplatePrivilegeRepository;
	private final PrivilegeRepository privilegeRepository;
	private final TenantPrivilegeAllowanceRepository tenantPrivilegeAllowanceRepository;
	private final PlatformSettingRepository platformSettingRepository;
	private final EmailVerificationService emailVerificationService;

	public RegistrationService(UserAccountRepository users, PasswordEncoder passwordEncoder, TenantRepository tenantRepository,
			TenantHandleValidator tenantHandleValidator, MembershipRepository membershipRepository, RoleRepository roleRepository,
			RolePrivilegeRepository rolePrivilegeRepository, UserRoleRepository userRoleRepository,
			NavMenuItemRepository navMenuItemRepository,
			RoleTemplateRepository roleTemplateRepository, RoleTemplatePrivilegeRepository roleTemplatePrivilegeRepository,
			PrivilegeRepository privilegeRepository, TenantPrivilegeAllowanceRepository tenantPrivilegeAllowanceRepository,
			PlatformSettingRepository platformSettingRepository, EmailVerificationService emailVerificationService) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tenantRepository = tenantRepository;
		this.tenantHandleValidator = tenantHandleValidator;
		this.membershipRepository = membershipRepository;
		this.roleRepository = roleRepository;
		this.rolePrivilegeRepository = rolePrivilegeRepository;
		this.userRoleRepository = userRoleRepository;
		this.navMenuItemRepository = navMenuItemRepository;
		this.roleTemplateRepository = roleTemplateRepository;
		this.roleTemplatePrivilegeRepository = roleTemplatePrivilegeRepository;
		this.privilegeRepository = privilegeRepository;
		this.tenantPrivilegeAllowanceRepository = tenantPrivilegeAllowanceRepository;
		this.platformSettingRepository = platformSettingRepository;
		this.emailVerificationService = emailVerificationService;
	}

	@Transactional
	public String register(RegisterRequest req) {
		if (!Boolean.TRUE.equals(req.agreeToTermsOfService()) || !Boolean.TRUE.equals(req.agreeToPrivacyPolicy())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "REGISTRATION_CONSENT_REQUIRED");
		}
		String first = req.firstName().trim();
		String last = req.lastName().trim();
		if (first.isEmpty() || last.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		String normalized = req.email().trim().toLowerCase();
		if (users.findByEmailIgnoreCase(normalized).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED");
		}
		String handle = tenantHandleValidator.normalizeAndValidate(req.tenantHandle());
		if (tenantRepository.findByHandle(handle).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_HANDLE_TAKEN");
		}
		Instant now = Instant.now();
		UserAccountEntity u = new UserAccountEntity();
		u.setId(UUID.randomUUID());
		u.setEmail(normalized);
		u.setFirstName(first);
		u.setLastName(last);
		u.setPasswordHash(passwordEncoder.encode(req.password()));
		u.setPlatformSuperadmin(false);
		u.setPreferredLocale("en");
		u.setEmailVerifiedAt(null);
		u.setCreatedAt(now);
		u.setUpdatedAt(now);
		users.save(u);

		UUID defaultTemplateId = resolveDefaultRoleTemplateIdForRegistration();
		bootstrapTenantForNewAccount(u.getId(), handle, now, defaultTemplateId);
		emailVerificationService.issueNewTokenAndSendMail(u, handle);
		return handle;
	}

	private UUID resolveDefaultRoleTemplateIdForRegistration() {
		String configured = platformSettingRepository.findByKey(PLATFORM_KEY_DEFAULT_ROLE_TEMPLATE).map(e -> e.getValueText())
				.map(String::trim).filter(s -> !s.isEmpty()).orElse("ADMIN");
		return roleTemplateRepository.findByCodeIgnoreCase(configured)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ROLE_TEMPLATE_CONFIGURATION_INVALID"))
				.getId();
	}

	private void bootstrapTenantForNewAccount(UUID userId, String normalizedHandle, Instant now, UUID defaultRoleTemplateId) {
		TenantEntity tenant = new TenantEntity();
		tenant.setId(UUID.randomUUID());
		tenant.setHandle(normalizedHandle);
		tenant.setName("Tenant " + normalizedHandle);
		tenant.setCreatedAt(now);
		tenant.setUpdatedAt(now);
		tenantRepository.save(tenant);

		UUID tenantId = tenant.getId();

		MembershipEntity m = new MembershipEntity();
		m.setId(UUID.randomUUID());
		m.setTenantId(tenantId);
		m.setUserId(userId);
		m.setCreatedAt(now);
		m.setUpdatedAt(now);
		m.setStatus("ACTIVE");
		membershipRepository.save(m);

		List<RoleTemplateEntity> templates = roleTemplateRepository.findAll();
		templates.sort((a, b) -> a.getCode().compareToIgnoreCase(b.getCode()));

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

		for (UUID privId : ceilingPrivilegeIds) {
			TenantPrivilegeAllowanceEntity a = new TenantPrivilegeAllowanceEntity();
			a.setId(UUID.randomUUID());
			a.setTenantId(tenantId);
			a.setPrivilegeId(privId);
			a.setCreatedAt(now);
			a.setUpdatedAt(now);
			tenantPrivilegeAllowanceRepository.save(a);
		}

		UUID tenantDefaultRoleId = templateIdToTenantRoleId.get(defaultRoleTemplateId);
		if (tenantDefaultRoleId == null) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ROLE_TEMPLATE_CONFIGURATION_INVALID");
		}
		bootstrapDefaultNavigation(tenantId, now);

		UserRoleEntity ur = new UserRoleEntity();
		ur.setId(UUID.randomUUID());
		ur.setTenantId(tenantId);
		ur.setUserId(userId);
		ur.setRoleId(tenantDefaultRoleId);
		ur.setCreatedAt(now);
		ur.setUpdatedAt(now);
		userRoleRepository.save(ur);

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

	private void bootstrapDefaultNavigation(UUID tenantId, Instant now) {
		createNavItem(tenantId, "/app", "nav.dashboard", 0, null, now);
		createNavItem(tenantId, "/app/users", "nav.users", 10, "USER_VIEW", now);
		createNavItem(tenantId, "/app/roles", "nav.roles", 12, "ROLE_VIEW", now);
		createNavItem(tenantId, "/app/tenant-currencies", "nav.tenant_currencies", 16, "TENANT_CURRENCY_VIEW", now);
		createNavItem(tenantId, "/app/documents", "nav.documents", 15, "DOCUMENT_VIEW", now);
		createNavItem(tenantId, "/app/settings", "nav.tenant_settings", 20, "TENANT_SETTINGS_EDIT", now);
	}

	private void createNavItem(UUID tenantId, String path, String labelKey, int sortOrder, String requiredPrivilegeCode,
			Instant now) {
		NavMenuItemEntity item = new NavMenuItemEntity();
		item.setId(UUID.randomUUID());
		item.setTenantId(tenantId);
		item.setParentId(null);
		item.setPath(path);
		item.setLabelKey(labelKey);
		item.setSortOrder(sortOrder);
		item.setRequiredPrivilegeCode(requiredPrivilegeCode);
		item.setRequiredPlanFeatureCode(null);
		item.setCreatedAt(now);
		item.setUpdatedAt(now);
		navMenuItemRepository.save(item);
	}
}
