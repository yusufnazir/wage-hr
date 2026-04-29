package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.wagepayroll.domain.document.TenantDocumentEntity;
import com.wagepayroll.domain.document.TenantDocumentRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TenantDocumentSoftDeleteIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";

	private static final UUID DEMO_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID ADMIN_UUID = UUID.fromString(ADMIN_USER_ID);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TenantDocumentRepository tenantDocumentRepository;

	@Test
	void uploaderSoftDeletesAndHubHidesDocument() throws Exception {
		Instant t = Instant.parse("2026-05-01T12:00:00Z");
		TenantDocumentEntity doc = new TenantDocumentEntity();
		doc.setId(UUID.randomUUID());
		doc.setTenantId(DEMO_TENANT_ID);
		doc.setStorageKey("tenants/" + DEMO_TENANT_ID + "/documents/" + doc.getId() + "/x.pdf");
		doc.setOriginalFilename("x.pdf");
		doc.setContentType("application/pdf");
		doc.setSizeBytes(1);
		doc.setUploadedByUserId(ADMIN_UUID);
		doc.setCreatedAt(t);
		doc.setUpdatedAt(t);
		tenantDocumentRepository.save(doc);

		mockMvc.perform(get("/api/v1/tenant/documents").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1));

		mockMvc.perform(delete("/api/v1/tenant/documents/" + doc.getId()).header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID))
				.with(csrf())).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/tenant/documents").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(0));
	}

	@Test
	void viewerCannotDeleteOthersDocument() throws Exception {
		Instant t = Instant.parse("2026-05-02T12:00:00Z");
		TenantDocumentEntity doc = new TenantDocumentEntity();
		doc.setId(UUID.randomUUID());
		doc.setTenantId(DEMO_TENANT_ID);
		doc.setStorageKey("tenants/" + DEMO_TENANT_ID + "/documents/" + doc.getId() + "/owner.pdf");
		doc.setOriginalFilename("owner.pdf");
		doc.setContentType("application/pdf");
		doc.setSizeBytes(1);
		doc.setUploadedByUserId(ADMIN_UUID);
		doc.setCreatedAt(t);
		doc.setUpdatedAt(t);
		tenantDocumentRepository.save(doc);

		mockMvc.perform(delete("/api/v1/tenant/documents/" + doc.getId()).header("Host", "demo.lvh.me").with(user(VIEWER_USER_ID))
				.with(csrf())).andExpect(status().isForbidden());
	}
}
