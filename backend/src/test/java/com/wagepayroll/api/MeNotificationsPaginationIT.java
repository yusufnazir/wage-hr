package com.wagepayroll.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.wagepayroll.notification.NotificationEmitCommand;
import com.wagepayroll.notification.NotificationService;
import com.wagepayroll.notification.NotificationType;

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
class MeNotificationsPaginationIT {

	private static final String ADMIN_USER_ID = "30000000-0000-0000-0000-000000000001";
	private static final String DEMO_TENANT_ID = "10000000-0000-0000-0000-000000000001";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private NotificationService notificationService;

	@Test
	void returnsTotalLimitOffsetAndSlices() throws Exception {
		UUID admin = UUID.fromString(ADMIN_USER_ID);
		UUID tenant = UUID.fromString(DEMO_TENANT_ID);
		for (int i = 0; i < 3; i++) {
			notificationService.emit(new NotificationEmitCommand(tenant, admin, NotificationType.TENANT_JOINED,
					NotificationService.TEMPLATE_VERSION_M2_1, UUID.randomUUID(), false));
			Thread.sleep(2);
		}

		mockMvc.perform(get("/api/v1/me/notifications").param("limit", "2").param("offset", "0").header("Host", "demo.lvh.me")
				.with(user(ADMIN_USER_ID))).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.total").value(3)).andExpect(jsonPath("$.data.limit").value(2))
				.andExpect(jsonPath("$.data.offset").value(0));

		mockMvc.perform(get("/api/v1/me/notifications").param("limit", "2").param("offset", "2").header("Host", "demo.lvh.me")
				.with(user(ADMIN_USER_ID))).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.total").value(3)).andExpect(jsonPath("$.data.offset").value(2));
	}
}
