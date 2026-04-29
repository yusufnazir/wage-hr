package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = { "app.storage.minio.endpoint=", "app.storage.minio.access-key=", "app.storage.minio.secret-key=",
		"app.storage.minio.bucket=" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantDocumentsStorageDisabledIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void uploadSessionReturns503WhenStorageNotConfigured() throws Exception {
		String body = "{\"originalFilename\":\"a.pdf\",\"contentType\":\"application/pdf\",\"sizeBytes\":1}";
		mockMvc.perform(post("/api/v1/tenant/documents/upload-sessions").header("Host", "demo.lvh.me").contentType(MediaType.APPLICATION_JSON)
				.content(body).with(user(ADMIN_USER_ID)).with(csrf())).andExpect(status().isServiceUnavailable());
	}
}
