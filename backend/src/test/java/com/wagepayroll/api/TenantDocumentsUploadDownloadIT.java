package com.wagepayroll.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
class TenantDocumentsUploadDownloadIT {

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

	@Test
	void uploadSessionCompleteAndDownload() throws Exception {
		String sessionBody = "{\"originalFilename\":\"report.pdf\",\"contentType\":\"application/pdf\",\"sizeBytes\":42}";
		MvcResult session = mockMvc
				.perform(post("/api/v1/tenant/documents/upload-sessions").header("Host", "demo.lvh.me")
						.contentType(MediaType.APPLICATION_JSON).content(sessionBody).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.uploadMethod").value("PUT"))
				.andExpect(jsonPath("$.data.uploadUrl").exists())
				.andExpect(jsonPath("$.data.storageKey", containsString("/documents/")))
				.andReturn();

		JsonNode s = objectMapper.readTree(session.getResponse().getContentAsString()).get("data");
		String documentId = s.get("documentId").asText();
		String storageKey = s.get("storageKey").asText();

		String completeJson = "{\"documentId\":\"%s\",\"storageKey\":\"%s\",\"originalFilename\":\"report.pdf\",\"contentType\":\"application/pdf\",\"sizeBytes\":42}"
				.formatted(documentId, storageKey);
		mockMvc.perform(post("/api/v1/tenant/documents/complete").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content(completeJson).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.documentId").value(documentId));

		mockMvc.perform(get("/api/v1/tenant/documents/" + documentId + "/download-url").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.downloadUrl").exists());

		mockMvc.perform(get("/api/v1/tenant/documents").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1));
	}

	@Test
	void completeRejectsStorageKeyMismatch() throws Exception {
		String sessionBody = "{\"originalFilename\":\"x.txt\",\"contentType\":\"text/plain\",\"sizeBytes\":1}";
		MvcResult session = mockMvc
				.perform(post("/api/v1/tenant/documents/upload-sessions").header("Host", "demo.lvh.me")
						.contentType(MediaType.APPLICATION_JSON).content(sessionBody).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isOk()).andReturn();
		JsonNode s = objectMapper.readTree(session.getResponse().getContentAsString()).get("data");
		String documentId = s.get("documentId").asText();

		String completeJson = "{\"documentId\":\"%s\",\"storageKey\":\"wrong/key\",\"originalFilename\":\"x.txt\",\"contentType\":\"text/plain\",\"sizeBytes\":1}"
				.formatted(documentId);
		mockMvc.perform(post("/api/v1/tenant/documents/complete").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content(completeJson).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isBadRequest());
	}

	@Test
	void downloadForbiddenWhenNotShared() throws Exception {
		Instant t = Instant.parse("2026-03-01T10:00:00Z");
		TenantDocumentEntity doc = new TenantDocumentEntity();
		doc.setId(UUID.randomUUID());
		doc.setTenantId(DEMO_TENANT_ID);
		doc.setStorageKey("tenants/" + DEMO_TENANT_ID + "/documents/" + doc.getId() + "/secret.pdf");
		doc.setOriginalFilename("secret.pdf");
		doc.setContentType("application/pdf");
		doc.setSizeBytes(9);
		doc.setUploadedByUserId(ADMIN_UUID);
		doc.setCreatedAt(t);
		doc.setUpdatedAt(t);
		tenantDocumentRepository.save(doc);

		mockMvc.perform(get("/api/v1/tenant/documents/" + doc.getId() + "/download-url").header("Host", "demo.lvh.me")
				.with(user(VIEWER_USER_ID))).andExpect(status().isNotFound());
	}

	@Test
	void downloadAllowedWhenSharedToUser() throws Exception {
		Instant t = Instant.parse("2026-03-02T10:00:00Z");
		TenantDocumentEntity doc = new TenantDocumentEntity();
		doc.setId(UUID.randomUUID());
		doc.setTenantId(DEMO_TENANT_ID);
		doc.setStorageKey("tenants/" + DEMO_TENANT_ID + "/documents/" + doc.getId() + "/shared.pdf");
		doc.setOriginalFilename("shared.pdf");
		doc.setContentType("application/pdf");
		doc.setSizeBytes(9);
		doc.setUploadedByUserId(ADMIN_UUID);
		doc.setCreatedAt(t);
		doc.setUpdatedAt(t);
		tenantDocumentRepository.save(doc);

		DocumentShareEntity share = new DocumentShareEntity();
		share.setId(UUID.randomUUID());
		share.setTenantId(DEMO_TENANT_ID);
		share.setDocumentId(doc.getId());
		share.setGranteeUserId(VIEWER_UUID);
		share.setCreatedByUserId(ADMIN_UUID);
		share.setCreatedAt(t);
		documentShareRepository.save(share);

		mockMvc.perform(get("/api/v1/tenant/documents/" + doc.getId() + "/download-url").header("Host", "demo.lvh.me")
				.with(user(VIEWER_USER_ID))).andExpect(status().isOk()).andExpect(jsonPath("$.data.downloadUrl").exists());
	}

	@Test
	void uploadSessionForbiddenWithoutDocumentEdit() throws Exception {
		String body = "{\"originalFilename\":\"a.pdf\",\"contentType\":\"application/pdf\",\"sizeBytes\":1}";
		mockMvc.perform(post("/api/v1/tenant/documents/upload-sessions").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content(body).with(user(VIEWER_USER_ID)).with(csrf())).andExpect(status().isForbidden());
	}
}
