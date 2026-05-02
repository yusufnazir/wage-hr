package com.wagepayroll.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.wagepayroll.auth.EmailVerificationMailPort;
import com.wagepayroll.auth.Sha256Hex;
import com.wagepayroll.domain.emailverification.EmailVerificationTokenRepository;
import com.wagepayroll.domain.membership.MembershipRepository;
import com.wagepayroll.domain.role.RoleEntity;
import com.wagepayroll.domain.role.RolePrivilegeRepository;
import com.wagepayroll.domain.role.RoleRepository;
import com.wagepayroll.domain.role.UserRoleRepository;
import com.wagepayroll.domain.user.UserAccountRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(RegistrationTenantBootstrapIT.EmailCaptureConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegistrationTenantBootstrapIT {

	static final AtomicReference<String> LAST_VERIFY_URL = new AtomicReference<>();

	@TestConfiguration
	static class EmailCaptureConfig {
		@Bean
		@Primary
		EmailVerificationMailPort capturingEmailVerificationPort() {
			return (email, url, firstName, tenantHandle, preferredLocaleForEmail) -> LAST_VERIFY_URL.set(url);
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private MembershipRepository membershipRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private UserRoleRepository userRoleRepository;

	@Autowired
	private RolePrivilegeRepository rolePrivilegeRepository;

	@Autowired
	private EmailVerificationTokenRepository emailVerificationTokenRepository;

	@BeforeEach
	void clearCapturedUrl() {
		LAST_VERIFY_URL.set(null);
	}

	private static String registerBody(String email, String handle) {
		return ("{\"email\":\"" + email + "\",\"password\":\"ChangeMe!1\",\"tenantHandle\":\"" + handle
				+ "\",\"firstName\":\"Test\",\"lastName\":\"User\",\"agreeToTermsOfService\":true,\"agreeToPrivacyPolicy\":true}");
	}

	@Test
	void registerCreatesTenantMembershipAdminRoleAndPendingVerification() throws Exception {
		String email = "new.user+" + System.currentTimeMillis() + "@example.test";
		String handle = "reg" + (System.currentTimeMillis() % 1_000_000_000);

		mockMvc.perform(post("/api/v1/auth/register").header("Host", "auth.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content(registerBody(email, handle)).with(csrf())).andExpect(status().isCreated());

		var user = userAccountRepository.findByEmailIgnoreCase(email).orElseThrow();
		assertThat(user.getEmailVerifiedAt()).isNull();
		assertThat(user.getFirstName()).isEqualTo("Test");
		assertThat(user.getLastName()).isEqualTo("User");

		assertThat(emailVerificationTokenRepository.findByUserAccount_Id(user.getId())).isNotEmpty();

		var memberships = membershipRepository.findByUserIdOrderByTenantIdAsc(user.getId());
		assertThat(memberships).hasSize(1);
		UUID tenantId = memberships.get(0).getTenantId();

		List<RoleEntity> roles = roleRepository.findByTenantId(tenantId);
		assertThat(roles).extracting(RoleEntity::getName).contains("Admin", "Employee");

		UUID adminRoleId = roles.stream().filter(r -> "Admin".equals(r.getName())).map(RoleEntity::getId).findFirst().orElseThrow();

		assertThat(userRoleRepository.findByTenantIdAndUserId(tenantId, user.getId()))
				.anySatisfy(ur -> assertThat(ur.getRoleId()).isEqualTo(adminRoleId));

		assertThat(rolePrivilegeRepository.findPrivilegeIdsByTenantIdAndRoleId(tenantId, adminRoleId)).isNotEmpty();
	}

	@Test
	void registerWithoutConsentReturns400() throws Exception {
		String email = "noconsent+" + System.currentTimeMillis() + "@example.test";
		String handle = "nc" + (System.currentTimeMillis() % 1_000_000_000);
		String body = "{\"email\":\"" + email + "\",\"password\":\"ChangeMe!1\",\"tenantHandle\":\"" + handle
				+ "\",\"firstName\":\"A\",\"lastName\":\"B\",\"agreeToTermsOfService\":true,\"agreeToPrivacyPolicy\":false}";

		mockMvc.perform(post("/api/v1/auth/register").header("Host", "auth.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content(body).with(csrf())).andExpect(status().isBadRequest());
	}

	@Test
	void verifyThenLogin() throws Exception {
		String email = "verify.user+" + System.currentTimeMillis() + "@example.test";
		String handle = "vrf" + (System.currentTimeMillis() % 1_000_000_000);

		mockMvc.perform(post("/api/v1/auth/register").header("Host", "auth.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content(registerBody(email, handle)).with(csrf())).andExpect(status().isCreated());

		String verifyUrl = LAST_VERIFY_URL.get();
		assertThat(verifyUrl).isNotNull();
		String rawToken = new URI(verifyUrl).getQuery().split("token=")[1];

		mockMvc.perform(post("/api/v1/auth/verify-email").header("Host", "auth.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\":\"" + rawToken + "\"}").with(csrf())).andExpect(status().isNoContent());

		var user = userAccountRepository.findByEmailIgnoreCase(email).orElseThrow();
		assertThat(user.getEmailVerifiedAt()).isNotNull();

		String sha = Sha256Hex.ofUtf8String(rawToken);
		assertThat(emailVerificationTokenRepository.findByTokenSha256(sha)).isPresent();

		mockMvc.perform(post("/api/v1/auth/login").header("Host", "auth.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"wrong\"}").with(csrf())).andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/auth/login").header("Host", "auth.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"ChangeMe!1\"}").with(csrf())).andExpect(status().isOk());
	}

	@Test
	void loginBeforeVerifyReturns403() throws Exception {
		String email = "unverified+" + System.currentTimeMillis() + "@example.test";
		String handle = "uv" + (System.currentTimeMillis() % 1_000_000_000);

		mockMvc.perform(post("/api/v1/auth/register").header("Host", "auth.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content(registerBody(email, handle)).with(csrf())).andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/auth/login").header("Host", "auth.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"ChangeMe!1\"}").with(csrf())).andExpect(status().isForbidden());
	}
}
