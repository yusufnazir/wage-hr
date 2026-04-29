package com.wagepayroll.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformPublicSurfaceIT {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void publicSurfaceIsPermittedWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/platform/public-surface")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.applicationName").value("Wage Payroll"))
				.andExpect(jsonPath("$.data.dateFormat").value("yyyy-MM-dd"))
				.andExpect(jsonPath("$.data.publicBaseUrl").value("http://auth.lvh.me:3007"));
	}
}
