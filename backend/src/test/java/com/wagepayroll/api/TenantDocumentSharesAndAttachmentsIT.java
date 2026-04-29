package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wagepayroll.domain.document.DocumentShareEntity;
import com.wagepayroll.domain.document.DocumentShareRepository;
import com.wagepayroll.domain.document.TenantDocumentEntity;
import com.wagepayroll.domain.document.TenantDocumentRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TenantDocumentSharesAndAttachmentsIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";

	private static final UUID DEMO_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID ADMIN_UUID = UUID.fromString(ADMIN_USER_ID);
	private static final UUID VIEWER_UUID = UUID.fromString(VIEWER_USER_ID);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private TenantDocumentRepository tenantDocumentRepository;

	@Autowired
	private DocumentShareRepository documentShareRepository;

	private TenantDocumentEntity seedAdminDoc() {
		Instant t = Instant.parse("2026-04-10T12:00:00Z");
		TenantDocumentEntity doc = new TenantDocumentEntity();
		doc.setId(UUID.randomUUID());
		doc.setTenantId(DEMO_TENANT_ID);
		doc.setStorageKey("tenants/" + DEMO_TENANT_ID + "/documents/" + doc.getId() + "/seed.pdf");
		doc.setOriginalFilename("seed.pdf");
		doc.setContentType("application/pdf");
		doc.setSizeBytes(1);
		doc.setUploadedByUserId(ADMIN_UUID);
		doc.setCreatedAt(t);
		doc.setUpdatedAt(t);
		return tenantDocumentRepository.save(doc);
	}

	@Test
	void uploaderCreatesShareAndViewerListsAttachments() throws Exception {
		TenantDocumentEntity doc = seedAdminDoc();
		String shareBody = "{\"granteeUserId\":\"" + VIEWER_UUID + "\",\"granteeRoleId\":null}";
		mockMvc.perform(post("/api/v1/tenant/documents/" + doc.getId() + "/shares").header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content(shareBody).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.share.granteeUserId").value(VIEWER_USER_ID));

		mockMvc.perform(get("/api/v1/tenant/documents/" + doc.getId() + "/attachments").header("Host", "demo.lvh.me").with(user(VIEWER_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
	}

	@Test
	void createShareRejectsXor() throws Exception {
		TenantDocumentEntity doc = seedAdminDoc();
		String roleAdmin = "40000000-0000-0000-0000-000000000001";
		String body = "{\"granteeUserId\":\"" + VIEWER_UUID + "\",\"granteeRoleId\":\"" + roleAdmin + "\"}";
		mockMvc.perform(post("/api/v1/tenant/documents/" + doc.getId() + "/shares").header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content(body).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isBadRequest());
	}

	@Test
	void createShareRejectsGranteeNotInTenant() throws Exception {
		TenantDocumentEntity doc = seedAdminDoc();
		String alien = "99999999-9999-9999-9999-999999999999";
		String body = "{\"granteeUserId\":\"" + alien + "\",\"granteeRoleId\":null}";
		mockMvc.perform(post("/api/v1/tenant/documents/" + doc.getId() + "/shares").header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content(body).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isBadRequest());
	}

	@Test
	void viewerCannotMutateShares() throws Exception {
		TenantDocumentEntity doc = seedAdminDoc();
		mockMvc.perform(get("/api/v1/tenant/documents/" + doc.getId() + "/shares").header("Host", "demo.lvh.me").with(user(VIEWER_USER_ID)))
				.andExpect(status().isForbidden());
	}

	@Test
	void attachmentCrudAndDuplicateConflict() throws Exception {
		TenantDocumentEntity doc = seedAdminDoc();
		String attachBody = "{\"entityType\":\"PAYROLL_RUN\",\"entityId\":\"" + UUID.randomUUID() + "\"}";
		MvcResult created = mockMvc
				.perform(post("/api/v1/tenant/documents/" + doc.getId() + "/attachments").header("Host", "demo.lvh.me")
						.contentType(MediaType.APPLICATION_JSON).content(attachBody).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk()).andReturn();
		String attachmentId = objectMapper.readTree(created.getResponse().getContentAsString()).get("data").get("attachment").get("id")
				.asText();

		mockMvc.perform(post("/api/v1/tenant/documents/" + doc.getId() + "/attachments").header("Host", "demo.lvh.me")
				.contentType(MediaType.APPLICATION_JSON).content(attachBody).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isConflict());

		mockMvc.perform(get("/api/v1/tenant/documents/" + doc.getId() + "/attachments").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));

		mockMvc.perform(delete("/api/v1/tenant/documents/" + doc.getId() + "/attachments/" + attachmentId).header("Host", "demo.lvh.me")
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/tenant/documents/" + doc.getId() + "/attachments").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
	}

	@Test
	void deleteShare() throws Exception {
		TenantDocumentEntity doc = seedAdminDoc();
		Instant t = Instant.now();
		DocumentShareEntity share = new DocumentShareEntity();
		share.setId(UUID.randomUUID());
		share.setTenantId(DEMO_TENANT_ID);
		share.setDocumentId(doc.getId());
		share.setGranteeUserId(VIEWER_UUID);
		share.setCreatedByUserId(ADMIN_UUID);
		share.setCreatedAt(t);
		documentShareRepository.save(share);

		mockMvc.perform(delete("/api/v1/tenant/documents/" + doc.getId() + "/shares/" + share.getId()).header("Host", "demo.lvh.me")
				.with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isOk());
	}

	@Test
	void listAttachmentsForbiddenWhenNoAccess() throws Exception {
		TenantDocumentEntity doc = seedAdminDoc();
		mockMvc.perform(get("/api/v1/tenant/documents/" + doc.getId() + "/attachments").header("Host", "demo.lvh.me").with(user(VIEWER_USER_ID)))
				.andExpect(status().isNotFound());
	}
}
