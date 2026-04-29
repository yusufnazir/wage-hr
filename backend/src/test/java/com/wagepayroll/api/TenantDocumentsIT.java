package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.wagepayroll.domain.document.DocumentShareEntity;
import com.wagepayroll.domain.document.DocumentShareRepository;
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
class TenantDocumentsIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";
	private static final String NOCODE_USER_ID = "30000000-0000-0000-0000-000000000003";

	private static final UUID DEMO_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID ADMIN_UUID = UUID.fromString(ADMIN_USER_ID);
	private static final UUID VIEWER_UUID = UUID.fromString(VIEWER_USER_ID);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TenantDocumentRepository tenantDocumentRepository;

	@Autowired
	private DocumentShareRepository documentShareRepository;

	@Test
	void documentsHubForbiddenWithoutTenantHost() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/documents").with(user(ADMIN_USER_ID))).andExpect(status().isForbidden());
	}

	@Test
	void documentsHubForbiddenWithoutDocumentView() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/documents").header("Host", "demo.lvh.me").with(user(NOCODE_USER_ID)))
				.andExpect(status().isForbidden());
	}

	@Test
	void documentsHubReturnsEmptyForAdmin() throws Exception {
		mockMvc.perform(get("/api/v1/tenant/documents").header("Host", "demo.lvh.me").with(user(ADMIN_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
	}

	@Test
	void documentsHubMergesOwnedAndShared() throws Exception {
		Instant t0 = Instant.parse("2026-01-01T12:00:00Z");
		Instant t1 = Instant.parse("2026-01-02T12:00:00Z");

		TenantDocumentEntity adminOwned = newDoc(DEMO_TENANT_ID, "k1", "a.pdf", ADMIN_UUID, t0);
		TenantDocumentEntity sharedToViewer = newDoc(DEMO_TENANT_ID, "k2", "b.pdf", ADMIN_UUID, t1);
		tenantDocumentRepository.save(adminOwned);
		tenantDocumentRepository.save(sharedToViewer);

		TenantDocumentEntity viewerOwned = newDoc(DEMO_TENANT_ID, "k3", "c.pdf", VIEWER_UUID, t1.plusSeconds(5));
		tenantDocumentRepository.save(viewerOwned);

		DocumentShareEntity share = new DocumentShareEntity();
		share.setId(UUID.randomUUID());
		share.setTenantId(DEMO_TENANT_ID);
		share.setDocumentId(sharedToViewer.getId());
		share.setGranteeUserId(VIEWER_UUID);
		share.setCreatedByUserId(ADMIN_UUID);
		share.setCreatedAt(t1);
		documentShareRepository.save(share);

		mockMvc.perform(get("/api/v1/tenant/documents").header("Host", "demo.lvh.me").with(user(VIEWER_USER_ID)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.items[0].hubSource").value("OWNED"))
				.andExpect(jsonPath("$.data.items[0].originalFilename").value("c.pdf"))
				.andExpect(jsonPath("$.data.items[1].hubSource").value("SHARED"))
				.andExpect(jsonPath("$.data.items[1].originalFilename").value("b.pdf"));
	}

	private static TenantDocumentEntity newDoc(UUID tenantId, String storageKeySuffix, String filename, UUID uploader,
			Instant createdAt) {
		TenantDocumentEntity e = new TenantDocumentEntity();
		e.setId(UUID.randomUUID());
		e.setTenantId(tenantId);
		e.setStorageKey("tenants/" + tenantId + "/documents/" + e.getId() + "/" + storageKeySuffix);
		e.setOriginalFilename(filename);
		e.setContentType("application/pdf");
		e.setSizeBytes(10);
		e.setUploadedByUserId(uploader);
		e.setCreatedAt(createdAt);
		e.setUpdatedAt(createdAt);
		return e;
	}
}
