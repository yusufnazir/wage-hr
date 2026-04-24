package com.wagepayroll.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.wagepayroll.domain.audit.AuditEventEntity;
import com.wagepayroll.domain.audit.AuditEventRepository;

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
class AuditAppendIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String VIEWER_USER_ID = "30000000-0000-0000-0000-000000000002";
	private static final UUID DEMO_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AuditEventRepository auditEventRepository;

	@Test
	void patchLocaleAppendsAuditEvent() throws Exception {
		long before = auditEventRepository.count();
		mockMvc.perform(
				patch("/api/v1/me/locale").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
						.content("{\"locale\":\"nl\"}").with(user(VIEWER_USER_ID)).with(csrf()))
				.andExpect(status().isNoContent());
		assertEquals(before + 1, auditEventRepository.count());
		AuditEventEntity ev = auditEventRepository
				.findFirstByActorUserIdAndActionCodeOrderByOccurredAtDesc(UUID.fromString(VIEWER_USER_ID),
						AuditActionCodes.USER_LOCALE_CHANGED)
				.orElseThrow();
		assertNull(ev.getTenantId());
		assertEquals(AuditResourceTypes.USER_ACCOUNT, ev.getResourceType());
		assertTrue(ev.getMetadataJson().contains("nl"));
	}

	@Test
	void patchTenantSettingsAppendsAuditEvent() throws Exception {
		long before = auditEventRepository.count();
		String body = "{\"entries\":[{\"key\":\"tenant.demo_flag\",\"value\":\"2\"}]}";
		mockMvc.perform(
				patch("/api/v1/tenant/settings").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
						.content(body).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isNoContent());
		assertEquals(before + 1, auditEventRepository.count());
		AuditEventEntity ev = auditEventRepository
				.findFirstByActorUserIdAndActionCodeOrderByOccurredAtDesc(UUID.fromString(ADMIN_USER_ID),
						AuditActionCodes.TENANT_SETTINGS_PATCHED)
				.orElseThrow();
		assertEquals(DEMO_TENANT_ID, ev.getTenantId());
		assertTrue(ev.getMetadataJson().contains("tenant.demo_flag"));
	}
}
