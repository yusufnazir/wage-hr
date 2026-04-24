package com.wagepayroll.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.ErasureRequestDto;
import com.wagepayroll.api.dto.TenantSummaryDto;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.auth.Sha256Hex;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.domain.user.UserAccountEntity;
import com.wagepayroll.domain.user.UserAccountRepository;
import com.wagepayroll.tenant.TenantDirectoryService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/privacy")
public class MePrivacyController {

	public static final int EXPORT_SCHEMA_VERSION = 1;

	private static final int ERASURE_NOTE_MAX = 500;

	private final UserAccountRepository userAccountRepository;
	private final TenantDirectoryService tenantDirectoryService;
	private final AuditService auditService;

	public MePrivacyController(UserAccountRepository userAccountRepository, TenantDirectoryService tenantDirectoryService,
			AuditService auditService) {
		this.userAccountRepository = userAccountRepository;
		this.tenantDirectoryService = tenantDirectoryService;
		this.auditService = auditService;
	}

	@GetMapping("/export")
	public ApiResponse<Map<String, Object>> export(HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		UserAccountEntity user = userAccountRepository.findById(userId).orElseThrow();
		List<TenantSummaryDto> tenants = tenantDirectoryService.listTenantSummaries(userId);
		String rid = RequestIdFilter.currentRequestId(request);

		Map<String, Object> account = new LinkedHashMap<>();
		account.put("userId", userId.toString());
		account.put("email", user.getEmail());
		account.put("preferredLocale", user.getPreferredLocale());
		account.put("platformSuperadmin", user.isPlatformSuperadmin());
		account.put("createdAt", user.getCreatedAt().toString());
		account.put("updatedAt", user.getUpdatedAt().toString());

		Map<String, Object> export = new LinkedHashMap<>();
		export.put("exportSchemaVersion", EXPORT_SCHEMA_VERSION);
		export.put("generatedAt", Instant.now().toString());
		export.put("account", account);
		export.put("tenantMemberships", tenants);

		auditService.append(null, userId, AuditActionCodes.SUBJECT_DATA_EXPORTED, AuditResourceTypes.USER_ACCOUNT,
				userId.toString(), rid, Map.of("exportSchemaVersion", EXPORT_SCHEMA_VERSION));

		return ApiResponse.of(Map.of("export", export), rid);
	}

	@PostMapping("/erasure-request")
	public ResponseEntity<ApiResponse<Map<String, String>>> erasureRequest(@RequestBody(required = false) ErasureRequestDto body,
			HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		String rid = RequestIdFilter.currentRequestId(request);
		Map<String, Object> meta = new LinkedHashMap<>();
		if (body != null && body.note() != null) {
			String note = body.note().trim();
			if (note.length() > ERASURE_NOTE_MAX) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ERASURE_NOTE_TOO_LONG");
			}
			if (!note.isEmpty()) {
				meta.put("noteLength", note.length());
				meta.put("noteSha256", Sha256Hex.ofUtf8String(note));
			}
		}
		auditService.append(null, userId, AuditActionCodes.SUBJECT_ERASURE_REQUESTED, AuditResourceTypes.USER_ACCOUNT,
				userId.toString(), rid, meta.isEmpty() ? null : meta);
		return ResponseEntity.accepted()
				.body(ApiResponse.of(
						Map.of("status", "accepted", "detail", "Erasure is not automated in M1; this request was logged for operators."),
						rid));
	}
}
