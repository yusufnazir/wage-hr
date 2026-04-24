package com.wagepayroll.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.wagepayroll.auth.Sha256Hex;
import com.wagepayroll.domain.audit.AuditEventEntity;
import com.wagepayroll.domain.audit.AuditEventRepository;
import com.wagepayroll.security.BreakGlassHeaders;

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
class SuperadminBreakGlassAuditIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final UUID ACME_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AuditEventRepository auditEventRepository;

	@Test
	void elevatedTenantPatchAppendsBreakGlassAuditWithReasonHash() throws Exception {
		long before = auditEventRepository.count();
		String reason = "OPS-9911 widen acme probe";
		String body = "{\"entries\":[{\"key\":\"tenant.acme_audit_probe\",\"value\":\"x\"}]}";
		mockMvc.perform(
				patch("/api/v1/tenant/settings").header("Host", "acme.lvh.me").contentType(MediaType.APPLICATION_JSON)
						.content(body).header(BreakGlassHeaders.REASON, reason).with(user(ADMIN_USER_ID)).with(csrf()))
				.andExpect(status().isNoContent());
		assertEquals(before + 2, auditEventRepository.count());
		AuditEventEntity ev = auditEventRepository
				.findFirstByActorUserIdAndActionCodeOrderByOccurredAtDesc(UUID.fromString(ADMIN_USER_ID),
						AuditActionCodes.SUPERADMIN_TENANT_ELEVATED_ACCESS)
				.orElseThrow();
		assertEquals(ACME_TENANT_ID, ev.getTenantId());
		assertTrue(ev.getMetadataJson().contains("\"privilege\":\"TENANT_SETTINGS_EDIT\""));
		assertTrue(ev.getMetadataJson().contains("\"reasonSha256\":\"" + Sha256Hex.ofUtf8String(reason) + "\""));
	}
}
