package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wagepayroll.domain.user.UserAccountRepository;

/**
 * M2: invite with {@code USER_INVITE}, token accept (no CSRF), inbox + mark read.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TenantInvitationFlowIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";
	private static final String VIEWER_ROLE_ID = "40000000-0000-0000-0000-000000000002";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Test
	void viewerCannotCreateInvitation() throws Exception {
		String body = "{\"email\":\"x@y.local\",\"roleId\":\"" + VIEWER_ROLE_ID + "\"}";
		mockMvc.perform(post("/api/v1/tenant/invitations").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content(body).with(user(VIEWER_USER_ID)).with(csrf())).andExpect(status().isForbidden());
	}

	@Test
	void createRejectsInvalidEmail() throws Exception {
		String body = "{\"email\":\"not-an-email\",\"roleId\":\"" + VIEWER_ROLE_ID + "\"}";
		mockMvc.perform(post("/api/v1/tenant/invitations").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content(body).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("INVALID_EMAIL"));
	}

	@Test
	void duplicatePendingInviteIsIdempotent() throws Exception {
		String email = "idempotent-" + UUID.randomUUID() + "@testinvite.local";
		String createBody = "{\"email\":\"" + email + "\",\"roleId\":\"" + VIEWER_ROLE_ID + "\"}";
		MvcResult first = mockMvc
				.perform(post("/api/v1/tenant/invitations").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
						.content(createBody).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.idempotentReplay").value(false)).andReturn();
		String invitationId = objectMapper.readTree(first.getResponse().getContentAsString()).get("data").get("invitationId")
				.asText();

		mockMvc.perform(post("/api/v1/tenant/invitations").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content(createBody).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.idempotentReplay").value(true)).andExpect(jsonPath("$.data.invitationId").value(invitationId));
	}

	@Test
	void createInviteAcceptAndSeeNotification() throws Exception {
		String email = "invitee-" + UUID.randomUUID() + "@testinvite.local";
		String createBody = "{\"email\":\"" + email + "\",\"roleId\":\"" + VIEWER_ROLE_ID + "\"}";
		MvcResult created = mockMvc
				.perform(post("/api/v1/tenant/invitations").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
						.content(createBody).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.devPlainToken").exists())
				.andExpect(jsonPath("$.data.idempotentReplay").value(false)).andReturn();
		JsonNode root = objectMapper.readTree(created.getResponse().getContentAsString());
		String token = root.get("data").get("devPlainToken").asText();
		String invitationId = root.get("data").get("invitationId").asText();

		String acceptBody = "{\"token\":\"" + token + "\",\"password\":\"InvitePass!9\"}";
		mockMvc.perform(post("/api/v1/auth/invitations/accept").contentType(MediaType.APPLICATION_JSON).content(acceptBody))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("accepted"));

		String newUserId = userAccountRepository.findByEmailIgnoreCase(email).orElseThrow().getId().toString();

		mockMvc.perform(get("/api/v1/me/notifications").header("Host", "demo.lvh.me").with(user(newUserId))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.total").value(1))
				.andExpect(jsonPath("$.data.limit").value(50)).andExpect(jsonPath("$.data.offset").value(0))
				.andExpect(jsonPath("$.data.items[0].notificationType").value("TENANT_JOINED"))
				.andExpect(jsonPath("$.data.items[0].correlationId").value(invitationId));

		String notifId = objectMapper
				.readTree(mockMvc.perform(get("/api/v1/me/notifications").header("Host", "demo.lvh.me").with(user(newUserId)))
						.andReturn().getResponse().getContentAsString())
				.get("data").get("items").get(0).get("id").asText();

		mockMvc.perform(patch("/api/v1/me/notifications/" + notifId + "/read").header("Host", "demo.lvh.me").with(user(newUserId))
				.with(csrf())).andExpect(status().isNoContent());
	}
}
