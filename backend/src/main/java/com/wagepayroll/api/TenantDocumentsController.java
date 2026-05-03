package com.wagepayroll.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.wagepayroll.api.dto.CreateDocumentAttachmentRequestDto;
import com.wagepayroll.api.dto.CreateDocumentShareRequestDto;
import com.wagepayroll.api.dto.DocumentAttachmentListItemDto;
import com.wagepayroll.api.dto.DocumentHubItemDto;
import com.wagepayroll.api.dto.DocumentShareListItemDto;
import com.wagepayroll.api.dto.DocumentUploadCompleteRequestDto;
import com.wagepayroll.api.dto.DocumentUploadSessionRequestDto;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.document.DocumentHubService;
import com.wagepayroll.document.TenantDocumentAttachmentService;
import com.wagepayroll.document.TenantDocumentLifecycleService;
import com.wagepayroll.document.TenantDocumentShareService;
import com.wagepayroll.document.TenantDocumentUploadService;
import com.wagepayroll.document.TenantDocumentUploadService.DownloadUrlResult;
import com.wagepayroll.document.TenantDocumentUploadService.UploadSessionResult;
import com.wagepayroll.domain.document.TenantDocumentEntity;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/documents")
public class TenantDocumentsController {

	private final DocumentHubService documentHubService;
	private final TenantDocumentUploadService tenantDocumentUploadService;
	private final TenantDocumentShareService tenantDocumentShareService;
	private final TenantDocumentAttachmentService tenantDocumentAttachmentService;
	private final TenantDocumentLifecycleService tenantDocumentLifecycleService;

	public TenantDocumentsController(DocumentHubService documentHubService,
			TenantDocumentUploadService tenantDocumentUploadService, TenantDocumentShareService tenantDocumentShareService,
			TenantDocumentAttachmentService tenantDocumentAttachmentService,
			TenantDocumentLifecycleService tenantDocumentLifecycleService) {
		this.documentHubService = documentHubService;
		this.tenantDocumentUploadService = tenantDocumentUploadService;
		this.tenantDocumentShareService = tenantDocumentShareService;
		this.tenantDocumentAttachmentService = tenantDocumentAttachmentService;
		this.tenantDocumentLifecycleService = tenantDocumentLifecycleService;
	}

	@GetMapping("/by-entity")
	@RequiresPrivilege("DOCUMENT_VIEW")
	public ResponseEntity<ApiResponse<Map<String, List<DocumentHubItemDto>>>> listByEntity(
			@RequestParam("entityType") String entityType, @RequestParam("entityId") UUID entityId,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		List<DocumentHubItemDto> items = tenantDocumentAttachmentService.listByEntity(tenantId, entityType, entityId);
		return ResponseEntity.ok(ApiResponse.of(Map.of("items", items), RequestIdFilter.currentRequestId(request)));
	}

	@GetMapping
	@RequiresPrivilege("DOCUMENT_VIEW")
	public ResponseEntity<ApiResponse<Map<String, List<DocumentHubItemDto>>>> listHub(HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		List<DocumentHubItemDto> items = documentHubService.hub(tenantId, userId);
		return ResponseEntity.ok(ApiResponse.of(Map.of("items", items), RequestIdFilter.currentRequestId(request)));
	}

	@PostMapping("/upload-sessions")
	@RequiresPrivilege("DOCUMENT_EDIT")
	public ResponseEntity<ApiResponse<Map<String, Object>>> createUploadSession(@Valid @RequestBody DocumentUploadSessionRequestDto body,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		UploadSessionResult r = tenantDocumentUploadService.createUploadSession(tenantId, body.originalFilename(), body.contentType(),
				body.sizeBytes());
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("documentId", r.documentId().toString());
		data.put("storageKey", r.storageKey());
		data.put("uploadUrl", r.uploadUrl());
		data.put("uploadMethod", "PUT");
		data.put("expiresAt", r.expiresAt().toString());
		data.put("requiredHeaders", r.requiredHeaders());
		return ResponseEntity.ok(ApiResponse.of(data, RequestIdFilter.currentRequestId(request)));
	}

	@PostMapping("/complete")
	@RequiresPrivilege("DOCUMENT_EDIT")
	public ResponseEntity<ApiResponse<Map<String, Object>>> completeUpload(@Valid @RequestBody DocumentUploadCompleteRequestDto body,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		TenantDocumentEntity saved = tenantDocumentUploadService.completeUpload(tenantId, userId, body.documentId(), body.storageKey(),
				body.originalFilename(), body.contentType(), body.sizeBytes());
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("documentId", saved.getId().toString());
		data.put("storageKey", saved.getStorageKey());
		return ResponseEntity.ok(ApiResponse.of(data, RequestIdFilter.currentRequestId(request)));
	}

	@DeleteMapping("/{documentId}")
	@RequiresPrivilege("DOCUMENT_EDIT")
	public ResponseEntity<ApiResponse<Map<String, Boolean>>> softDeleteDocument(@PathVariable("documentId") UUID documentId,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		tenantDocumentLifecycleService.softDelete(tenantId, userId, documentId);
		return ResponseEntity.ok(ApiResponse.of(Map.of("deleted", true), RequestIdFilter.currentRequestId(request)));
	}

	@GetMapping("/{documentId}/download-url")
	@RequiresPrivilege("DOCUMENT_VIEW")
	public ResponseEntity<ApiResponse<Map<String, Object>>> downloadUrl(@PathVariable("documentId") UUID documentId,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		DownloadUrlResult r = tenantDocumentUploadService.presignDownload(tenantId, userId, documentId);
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("downloadUrl", r.downloadUrl());
		data.put("expiresAt", r.expiresAt().toString());
		return ResponseEntity.ok(ApiResponse.of(data, RequestIdFilter.currentRequestId(request)));
	}

	@GetMapping("/{documentId}/shares")
	@RequiresPrivilege("DOCUMENT_EDIT")
	public ResponseEntity<ApiResponse<Map<String, List<DocumentShareListItemDto>>>> listShares(@PathVariable("documentId") UUID documentId,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		List<DocumentShareListItemDto> items = tenantDocumentShareService.listShares(tenantId, userId, documentId);
		return ResponseEntity.ok(ApiResponse.of(Map.of("items", items), RequestIdFilter.currentRequestId(request)));
	}

	@PostMapping("/{documentId}/shares")
	@RequiresPrivilege("DOCUMENT_EDIT")
	public ResponseEntity<ApiResponse<Map<String, DocumentShareListItemDto>>> createShare(@PathVariable("documentId") UUID documentId,
			@RequestBody CreateDocumentShareRequestDto body, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		DocumentShareListItemDto created = tenantDocumentShareService.createShare(tenantId, userId, documentId, body.granteeUserId(),
				body.granteeRoleId());
		return ResponseEntity.ok(ApiResponse.of(Map.of("share", created), RequestIdFilter.currentRequestId(request)));
	}

	@DeleteMapping("/{documentId}/shares/{shareId}")
	@RequiresPrivilege("DOCUMENT_EDIT")
	public ResponseEntity<ApiResponse<Map<String, Boolean>>> deleteShare(@PathVariable("documentId") UUID documentId,
			@PathVariable("shareId") UUID shareId, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		tenantDocumentShareService.deleteShare(tenantId, userId, documentId, shareId);
		return ResponseEntity.ok(ApiResponse.of(Map.of("deleted", true), RequestIdFilter.currentRequestId(request)));
	}

	@GetMapping("/{documentId}/attachments")
	@RequiresPrivilege("DOCUMENT_VIEW")
	public ResponseEntity<ApiResponse<Map<String, List<DocumentAttachmentListItemDto>>>> listAttachments(
			@PathVariable("documentId") UUID documentId, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		List<DocumentAttachmentListItemDto> items = tenantDocumentAttachmentService.listAttachments(tenantId, userId, documentId);
		return ResponseEntity.ok(ApiResponse.of(Map.of("items", items), RequestIdFilter.currentRequestId(request)));
	}

	@PostMapping("/{documentId}/attachments")
	@RequiresPrivilege("DOCUMENT_EDIT")
	public ResponseEntity<ApiResponse<Map<String, DocumentAttachmentListItemDto>>> createAttachment(
			@PathVariable("documentId") UUID documentId, @Valid @RequestBody CreateDocumentAttachmentRequestDto body,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		DocumentAttachmentListItemDto created = tenantDocumentAttachmentService.createAttachment(tenantId, userId, documentId,
				body.entityType(), body.entityId());
		return ResponseEntity.ok(ApiResponse.of(Map.of("attachment", created), RequestIdFilter.currentRequestId(request)));
	}

	@DeleteMapping("/{documentId}/attachments/{attachmentId}")
	@RequiresPrivilege("DOCUMENT_EDIT")
	public ResponseEntity<ApiResponse<Map<String, Boolean>>> deleteAttachment(@PathVariable("documentId") UUID documentId,
			@PathVariable("attachmentId") UUID attachmentId, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		tenantDocumentAttachmentService.deleteAttachment(tenantId, userId, documentId, attachmentId);
		return ResponseEntity.ok(ApiResponse.of(Map.of("deleted", true), RequestIdFilter.currentRequestId(request)));
	}
}
