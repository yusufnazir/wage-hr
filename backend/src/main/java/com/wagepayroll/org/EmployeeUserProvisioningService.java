package com.wagepayroll.org;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wagepayroll.auth.Sha256Hex;
import com.wagepayroll.common.email.EmailAddress;
import com.wagepayroll.domain.employeeactivation.EmployeeAccountActivationTokenEntity;
import com.wagepayroll.domain.employeeactivation.EmployeeAccountActivationTokenRepository;
import com.wagepayroll.domain.membership.MembershipEntity;
import com.wagepayroll.domain.membership.MembershipRepository;
import com.wagepayroll.domain.org.TenantCompanyEntity;
import com.wagepayroll.domain.org.TenantCompanyRepository;
import com.wagepayroll.domain.org.TenantEmployeeEntity;
import com.wagepayroll.domain.org.TenantEmployeeRepository;
import com.wagepayroll.domain.role.RoleEntity;
import com.wagepayroll.domain.role.RoleRepository;
import com.wagepayroll.domain.role.UserRoleEntity;
import com.wagepayroll.domain.role.UserRoleRepository;
import com.wagepayroll.domain.user.UserAccountEntity;
import com.wagepayroll.domain.user.UserAccountRepository;
import com.wagepayroll.mail.EmployeeAccountMailPort;
import com.wagepayroll.settings.PlatformBrandingService;
import com.wagepayroll.settings.PlatformUrlJoin;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmployeeUserProvisioningService {

	private static final String EMPLOYEE_ROLE_NAME = "Employee";
	private static final int ACTIVATION_TOKEN_DAYS = 7;
	private static final int RAW_TOKEN_BYTES = 32;

	private final TenantEmployeeRepository employeeRepository;
	private final TenantCompanyRepository companyRepository;
	private final UserAccountRepository userAccountRepository;
	private final MembershipRepository membershipRepository;
	private final UserRoleRepository userRoleRepository;
	private final RoleRepository roleRepository;
	private final EmployeeAccountActivationTokenRepository activationTokenRepository;
	private final EmployeeAccountMailPort employeeAccountMailPort;
	private final PasswordEncoder passwordEncoder;
	private final PlatformBrandingService platformBrandingService;
	private final SecureRandom random = new SecureRandom();

	public EmployeeUserProvisioningService(TenantEmployeeRepository employeeRepository,
			TenantCompanyRepository companyRepository, UserAccountRepository userAccountRepository,
			MembershipRepository membershipRepository, UserRoleRepository userRoleRepository, RoleRepository roleRepository,
			EmployeeAccountActivationTokenRepository activationTokenRepository, EmployeeAccountMailPort employeeAccountMailPort,
			PasswordEncoder passwordEncoder, PlatformBrandingService platformBrandingService) {
		this.employeeRepository = employeeRepository;
		this.companyRepository = companyRepository;
		this.userAccountRepository = userAccountRepository;
		this.membershipRepository = membershipRepository;
		this.userRoleRepository = userRoleRepository;
		this.roleRepository = roleRepository;
		this.activationTokenRepository = activationTokenRepository;
		this.employeeAccountMailPort = employeeAccountMailPort;
		this.passwordEncoder = passwordEncoder;
		this.platformBrandingService = platformBrandingService;
	}

	@Transactional
	public void provisionIfRequested(UUID tenantId, String tenantHandle, TenantEmployeeEntity employee, boolean enableUserAccount) {
		if (!enableUserAccount) {
			return;
		}
		if (employee.getUserId() != null) {
			return;
		}
		String rawEmail = employee.getEmail();
		if (rawEmail == null || rawEmail.isBlank()) {
			throw badRequest("Employee email is required to create a user account");
		}
		final String email;
		try {
			email = EmailAddress.normalizeAndValidate(rawEmail);
		}
		catch (IllegalArgumentException ex) {
			throw badRequest("INVALID_EMAIL");
		}
		TenantCompanyEntity company = companyRepository.findByIdAndTenantId(employee.getCompanyId(), tenantId)
				.orElseThrow(() -> badRequest("Company not found"));
		RoleEntity employeeRole = requireEmployeeRole(tenantId);
		Optional<UserAccountEntity> existingUser = userAccountRepository.findByEmailIgnoreCase(email);
		if (existingUser.isPresent()) {
			linkExistingUser(tenantId, tenantHandle, employee, company, employeeRole, existingUser.get());
			return;
		}
		createNewUserWithActivation(tenantId, tenantHandle, employee, company, employeeRole, email);
	}

	private void linkExistingUser(UUID tenantId, String tenantHandle, TenantEmployeeEntity employee,
			TenantCompanyEntity company, RoleEntity employeeRole, UserAccountEntity user) {
		UUID userId = user.getId();
		if (employeeRepository.existsByTenantIdAndUserIdAndIdNot(tenantId, userId, employee.getId())) {
			throw conflict("USER_ALREADY_LINKED_TO_EMPLOYEE");
		}
		ensureMembership(tenantId, userId);
		ensureRole(tenantId, userId, employeeRole.getId());
		employee.setUserId(userId);
		employeeRepository.save(employee);
		String firstName = pickFirstName(employee, user);
		employeeAccountMailPort.sendLinkedEmail(user.getEmail(), firstName, company.getName(), tenantHandle,
				employeeRole.getName(), safeLocale(user.getPreferredLocale()));
	}

	private void createNewUserWithActivation(UUID tenantId, String tenantHandle, TenantEmployeeEntity employee,
			TenantCompanyEntity company, RoleEntity employeeRole, String email) {
		Instant now = Instant.now();
		UserAccountEntity user = new UserAccountEntity();
		user.setId(UUID.randomUUID());
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
		user.setPlatformSuperadmin(false);
		user.setPreferredLocale("en");
		user.setFirstName(trimToNull(employee.getFirstName()));
		user.setLastName(trimToNull(employee.getLastName()));
		user.setCreatedAt(now);
		user.setUpdatedAt(now);
		user.setEmailVerifiedAt(null);
		userAccountRepository.save(user);

		ensureMembership(tenantId, user.getId());
		ensureRole(tenantId, user.getId(), employeeRole.getId());
		employee.setUserId(user.getId());
		employeeRepository.save(employee);

		byte[] raw = new byte[RAW_TOKEN_BYTES];
		random.nextBytes(raw);
		String plainToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
		EmployeeAccountActivationTokenEntity token = new EmployeeAccountActivationTokenEntity();
		token.setId(UUID.randomUUID());
		token.setTenantId(tenantId);
		token.setEmployeeId(employee.getId());
		token.setUserAccountId(user.getId());
		token.setTokenSha256(Sha256Hex.ofUtf8String(plainToken));
		token.setExpiresAt(now.plus(ACTIVATION_TOKEN_DAYS, ChronoUnit.DAYS));
		token.setCreatedAt(now);
		activationTokenRepository.save(token);

		String base = platformBrandingService.publicBaseUrl();
		String activationUrl = PlatformUrlJoin.joinPublicBaseAndPath(base, "/activate-account?token=" + plainToken);
		String firstName = pickFirstName(employee, user);
		employeeAccountMailPort.sendActivationEmail(email, firstName, company.getName(), tenantHandle, employeeRole.getName(),
				activationUrl, safeLocale(user.getPreferredLocale()));
	}

	private void ensureMembership(UUID tenantId, UUID userId) {
		if (membershipRepository.findByTenantIdAndUserId(tenantId, userId).isPresent()) {
			return;
		}
		Instant now = Instant.now();
		MembershipEntity membership = new MembershipEntity();
		membership.setId(UUID.randomUUID());
		membership.setTenantId(tenantId);
		membership.setUserId(userId);
		membership.setStatus("ACTIVE");
		membership.setCreatedAt(now);
		membership.setUpdatedAt(now);
		membershipRepository.save(membership);
	}

	private void ensureRole(UUID tenantId, UUID userId, UUID roleId) {
		List<UUID> roles = userRoleRepository.findRoleIdsByUserAndTenant(userId, tenantId);
		if (roles.stream().anyMatch(roleId::equals)) {
			return;
		}
		Instant now = Instant.now();
		UserRoleEntity userRole = new UserRoleEntity();
		userRole.setId(UUID.randomUUID());
		userRole.setTenantId(tenantId);
		userRole.setUserId(userId);
		userRole.setRoleId(roleId);
		userRole.setCreatedAt(now);
		userRole.setUpdatedAt(now);
		userRoleRepository.save(userRole);
	}

	private RoleEntity requireEmployeeRole(UUID tenantId) {
		return roleRepository.findByTenantId(tenantId).stream()
				.filter(r -> EMPLOYEE_ROLE_NAME.equalsIgnoreCase(r.getName()))
				.findFirst()
				.orElseThrow(() -> badRequest("EMPLOYEE_ROLE_NOT_FOUND"));
	}

	private static String pickFirstName(TenantEmployeeEntity employee, UserAccountEntity user) {
		if (employee.getFirstName() != null && !employee.getFirstName().isBlank()) {
			return employee.getFirstName().trim();
		}
		if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
			return user.getFirstName().trim();
		}
		return "there";
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static String safeLocale(String locale) {
		return locale == null || locale.isBlank() ? "en" : locale;
	}

	private static ResponseStatusException badRequest(String code) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, code);
	}

	private static ResponseStatusException conflict(String code) {
		return new ResponseStatusException(HttpStatus.CONFLICT, code);
	}
}
