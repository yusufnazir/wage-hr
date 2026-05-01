package com.wagepayroll.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.MailTemplateDetailDto;
import com.wagepayroll.api.dto.MailTemplateListItemDto;
import com.wagepayroll.api.dto.MailTemplatePutRequest;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.mail.PlatformMailTemplateService;
import com.wagepayroll.security.PlatformOperatorService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/mail-templates")
public class PlatformMailTemplateController {

	private final PlatformOperatorService platformOperatorService;
	private final PlatformMailTemplateService platformMailTemplateService;
	private final AuditService auditService;

	public PlatformMailTemplateController(PlatformOperatorService platformOperatorService,
			PlatformMailTemplateService platformMailTemplateService, AuditService auditService) {
		this.platformOperatorService = platformOperatorService;
		this.platformMailTemplateService = platformMailTemplateService;
		this.auditService = auditService;
	}

	@GetMapping
	public ApiResponse<Map<String, List<MailTemplateListItemDto>>> list(HttpServletRequest request) {
		requirePlatformSuperadmin();
		List<MailTemplateListItemDto> items = platformMailTemplateService.listAll();
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(Map.of("items", items), rid);
	}

	@GetMapping("/{id}")
	public ApiResponse<Map<String, MailTemplateDetailDto>> one(@PathVariable("id") UUID id, HttpServletRequest request) {
		requirePlatformSuperadmin();
		MailTemplateDetailDto item = platformMailTemplateService.get(id);
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(Map.of("item", item), rid);
	}

	@PutMapping("/{id}")
	public ResponseEntity<Void> put(@PathVariable("id") UUID id, @RequestBody MailTemplatePutRequest body,
			HttpServletRequest request) {
		requirePlatformSuperadmin();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID actor = UUID.fromString(auth.getName());
		platformMailTemplateService.replace(id, body);
		MailTemplateDetailDto after = platformMailTemplateService.get(id);
		auditService.append(null, actor, AuditActionCodes.PLATFORM_MAIL_TEMPLATES_UPDATED, AuditResourceTypes.MAIL_TEMPLATE,
				id.toString(), RequestIdFilter.currentRequestId(request), Map.of("code", after.code(), "contentVersion", after.contentVersion()));
		return ResponseEntity.noContent().build();
	}

	private void requirePlatformSuperadmin() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(userId);
	}
}
