package com.wagepayroll.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.wagepayroll.domain.membership.MembershipRepository;
import com.wagepayroll.domain.role.RoleEntity;
import com.wagepayroll.domain.role.RolePrivilegeRepository;
import com.wagepayroll.domain.role.RoleRepository;
import com.wagepayroll.domain.role.UserRoleRepository;
import com.wagepayroll.domain.user.UserAccountRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegistrationTenantBootstrapIT {

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

	@Test
	void registerCreatesTenantMembershipAndAdminRoleAssignment() throws Exception {
		String email = "new.user+" + System.currentTimeMillis() + "@example.test";
		String body = "{\"email\":\"" + email + "\",\"password\":\"ChangeMe!1\"}";

		mockMvc.perform(post("/api/v1/auth/register").header("Host", "auth.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content(body).with(csrf())).andExpect(status().isCreated());

		var user = userAccountRepository.findByEmailIgnoreCase(email).orElseThrow();
		var memberships = membershipRepository.findByUserIdOrderByTenantIdAsc(user.getId());
		assertThat(memberships).hasSize(1);
		UUID tenantId = memberships.get(0).getTenantId();

		List<RoleEntity> roles = roleRepository.findByTenantId(tenantId);
		assertThat(roles).extracting(RoleEntity::getName).contains("Admin", "Employee");

		UUID adminRoleId = roles.stream().filter(r -> "Admin".equals(r.getName())).map(RoleEntity::getId).findFirst().orElseThrow();

		assertThat(userRoleRepository.findByTenantIdAndUserId(tenantId, user.getId()))
				.anySatisfy(ur -> assertThat(ur.getRoleId()).isEqualTo(adminRoleId));

		// Admin template must include ROLE_EDIT, copied into tenant role_privilege.
		assertThat(rolePrivilegeRepository.findPrivilegeIdsByTenantIdAndRoleId(tenantId, adminRoleId)).isNotEmpty();
	}
}

