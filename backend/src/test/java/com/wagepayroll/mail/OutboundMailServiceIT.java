package com.wagepayroll.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wagepayroll.settings.MailApiSettingsMergeService;
import com.wagepayroll.settings.PlatformBrandingService;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboundMailServiceIT {

	@Mock
	private MailApiSettingsMergeService mailApiSettingsMergeService;

	@Mock
	private PlatformBrandingService platformBrandingService;

	@Mock
	private MailTemplateCatalogService mailTemplateCatalogService;

	private MockWebServer mockWebServer;
	private OutboundMailService service;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() throws IOException {
		mockWebServer = new MockWebServer();
		mockWebServer.start();
		service = new OutboundMailService(mailApiSettingsMergeService, platformBrandingService, mailTemplateCatalogService);
		when(mailApiSettingsMergeService.resolve()).thenReturn(new MailApiSettingsMergeService.MergedMailApiSettings(
				mockWebServer.url("/").toString(), "project-1", "user", "pass"));
	}

	@AfterEach
	void tearDown() throws IOException {
		mockWebServer.shutdown();
	}

	@Test
	void verificationUsesRenderedHtmlWhenCatalogProvidesTemplate() throws Exception {
		mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
		when(mailTemplateCatalogService.tryRenderEmailVerification(anyString(), anyMap()))
				.thenReturn(Optional.of(new RenderedCatalogEmail("Verify", "<h1>Verify</h1><a href=\"https://verify\">Open</a>",
						"Verify text")));

		service.sendEmailVerificationLink("ada@example.test", "https://verify", "Ada", "demo", "nl-sr");

		RecordedRequest req = mockWebServer.takeRequest();
		JsonNode body = objectMapper.readTree(req.getBody().readUtf8());
		assertThat(req.getPath()).isEqualTo("/rest/v1/api/register-mail");
		assertThat(body.get("projectId").asText()).isEqualTo("project-1");
		assertThat(body.get("text").asText()).contains("https://verify");
		assertThat(body.get("to").isArray()).isTrue();
		assertThat(body.get("to").get(0).asText()).isEqualTo("ada@example.test");
	}

	@Test
	void verificationFallsBackToTextOnlyWhenCatalogMissing() throws Exception {
		mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
		when(mailTemplateCatalogService.tryRenderEmailVerification(anyString(), anyMap())).thenReturn(Optional.empty());

		service.sendEmailVerificationLink("ada@example.test", "https://verify", "Ada", "demo", "en");

		RecordedRequest req = mockWebServer.takeRequest();
		JsonNode body = objectMapper.readTree(req.getBody().readUtf8());
		assertThat(req.getPath()).isEqualTo("/rest/v1/api/register-mail");
		assertThat(body.get("projectId").asText()).isEqualTo("project-1");
		assertThat(body.get("text").asText()).contains("https://verify");
		assertThat(body.get("to").isArray()).isTrue();
		assertThat(body.get("to").get(0).asText()).isEqualTo("ada@example.test");
	}

	@Test
	void passwordResetUsesRenderedHtmlWithResetLinkAndExpiryMinutes() throws Exception {
		mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
		when(mailTemplateCatalogService.tryRenderPasswordResetRequest(anyString(), anyMap()))
				.thenReturn(Optional.of(new RenderedCatalogEmail("Reset", "<h1>Reset</h1><a href=\"https://reset\">Go</a><p>60</p>",
						"Reset text")));

		service.sendPasswordResetLink("lin@example.test", "https://reset", "Lin", "nl", "60");

		RecordedRequest req = mockWebServer.takeRequest();
		JsonNode body = objectMapper.readTree(req.getBody().readUtf8());
		assertThat(req.getPath()).isEqualTo("/rest/v1/api/register-mail");
		assertThat(body.get("projectId").asText()).isEqualTo("project-1");
		assertThat(body.get("text").asText()).contains("https://reset").contains("60");
		assertThat(body.get("to").isArray()).isTrue();
		assertThat(body.get("to").get(0).asText()).isEqualTo("lin@example.test");
	}

	@Test
	void passwordResetFallsBackToTextOnlyWhenCatalogMissing() throws Exception {
		mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
		when(mailTemplateCatalogService.tryRenderPasswordResetRequest(anyString(), anyMap())).thenReturn(Optional.empty());

		service.sendPasswordResetLink("lin@example.test", "https://reset", "Lin", "en", "60");

		RecordedRequest req = mockWebServer.takeRequest();
		JsonNode body = objectMapper.readTree(req.getBody().readUtf8());
		assertThat(req.getPath()).isEqualTo("/rest/v1/api/register-mail");
		assertThat(body.get("projectId").asText()).isEqualTo("project-1");
		assertThat(body.get("text").asText()).contains("https://reset");
		assertThat(body.get("to").isArray()).isTrue();
		assertThat(body.get("to").get(0).asText()).isEqualTo("lin@example.test");
	}
}
