package com.wagepayroll.api;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.wagepayroll.api.dto.PlatformComponentGroupCreateRequest;
import com.wagepayroll.api.dto.PlatformComponentGroupPutRequest;
import com.wagepayroll.api.dto.PlatformComponentGroupRowDto;
import com.wagepayroll.api.dto.PlatformComponentHeaderCreateRequest;
import com.wagepayroll.api.dto.PlatformComponentHeaderPutRequest;
import com.wagepayroll.api.dto.PlatformComponentHeaderRowDto;
import com.wagepayroll.api.dto.PlatformComponentItemCreateRequest;
import com.wagepayroll.api.dto.PlatformComponentItemPutRequest;
import com.wagepayroll.api.dto.PlatformComponentItemRowDto;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.componentgroup.PlatformComponentGroupTemplateAdminService;
import com.wagepayroll.security.PlatformOperatorService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/component-group-templates")
public class PlatformComponentGroupTemplatesController {

	private final PlatformOperatorService platformOperatorService;
	private final PlatformComponentGroupTemplateAdminService adminService;

	public PlatformComponentGroupTemplatesController(PlatformOperatorService platformOperatorService,
			PlatformComponentGroupTemplateAdminService adminService) {
		this.platformOperatorService = platformOperatorService;
		this.adminService = adminService;
	}

	@GetMapping
	public ApiResponse<Map<String, Object>> listGroups(
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			@RequestParam(name = "country", required = false) String country,
			@RequestParam(name = "active", required = false) Boolean active,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		requirePlatformSuperadmin();
		return ApiResponse.of(adminService.listGroups(page, size, country, active, locale),
				RequestIdFilter.currentRequestId(request));
	}

	@GetMapping("/{id}")
	public ApiResponse<Map<String, PlatformComponentGroupRowDto>> getGroup(@PathVariable("id") UUID id,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		requirePlatformSuperadmin();
		return ApiResponse.of(Map.of("group", adminService.getGroup(id, locale)),
				RequestIdFilter.currentRequestId(request));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<Map<String, PlatformComponentGroupRowDto>>> createGroup(
			@Valid @RequestBody PlatformComponentGroupCreateRequest body,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformComponentGroupRowDto row = adminService.createGroup(body, actor, RequestIdFilter.currentRequestId(request),
				locale);
		String rid = RequestIdFilter.currentRequestId(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(Map.of("group", row), rid));
	}

	@PutMapping("/{id}")
	public ApiResponse<Map<String, PlatformComponentGroupRowDto>> updateGroup(@PathVariable("id") UUID id,
			@Valid @RequestBody PlatformComponentGroupPutRequest body,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		return ApiResponse.of(Map.of("group", adminService.updateGroup(id, body, actor, RequestIdFilter.currentRequestId(request), locale)),
				RequestIdFilter.currentRequestId(request));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteGroup(@PathVariable("id") UUID id, HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		adminService.deleteGroup(id, actor, RequestIdFilter.currentRequestId(request));
	}

	@GetMapping("/{groupId}/headers")
	public ApiResponse<Map<String, Object>> listHeaders(@PathVariable("groupId") UUID groupId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		requirePlatformSuperadmin();
		return ApiResponse.of(adminService.listHeaders(groupId, page, size, locale),
				RequestIdFilter.currentRequestId(request));
	}

	@GetMapping("/{groupId}/headers/{headerId}")
	public ApiResponse<Map<String, PlatformComponentHeaderRowDto>> getHeader(@PathVariable("groupId") UUID groupId,
			@PathVariable("headerId") UUID headerId,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		requirePlatformSuperadmin();
		return ApiResponse.of(Map.of("header", adminService.getHeader(groupId, headerId, locale)),
				RequestIdFilter.currentRequestId(request));
	}

	@PostMapping("/{groupId}/headers")
	public ResponseEntity<ApiResponse<Map<String, PlatformComponentHeaderRowDto>>> createHeader(
			@PathVariable("groupId") UUID groupId, @Valid @RequestBody PlatformComponentHeaderCreateRequest body,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformComponentHeaderRowDto row = adminService.createHeader(groupId, body, actor,
				RequestIdFilter.currentRequestId(request), locale);
		String rid = RequestIdFilter.currentRequestId(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(Map.of("header", row), rid));
	}

	@PutMapping("/{groupId}/headers/{headerId}")
	public ApiResponse<Map<String, PlatformComponentHeaderRowDto>> updateHeader(@PathVariable("groupId") UUID groupId,
			@PathVariable("headerId") UUID headerId, @Valid @RequestBody PlatformComponentHeaderPutRequest body,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		return ApiResponse.of(
				Map.of("header", adminService.updateHeader(groupId, headerId, body, actor, RequestIdFilter.currentRequestId(request), locale)),
				RequestIdFilter.currentRequestId(request));
	}

	@DeleteMapping("/{groupId}/headers/{headerId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteHeader(@PathVariable("groupId") UUID groupId, @PathVariable("headerId") UUID headerId,
			HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		adminService.deleteHeader(groupId, headerId, actor, RequestIdFilter.currentRequestId(request));
	}

	@GetMapping("/{groupId}/headers/{headerId}/items")
	public ApiResponse<Map<String, Object>> listItems(@PathVariable("groupId") UUID groupId,
			@PathVariable("headerId") UUID headerId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		requirePlatformSuperadmin();
		return ApiResponse.of(adminService.listItems(groupId, headerId, page, size, locale),
				RequestIdFilter.currentRequestId(request));
	}

	@GetMapping("/{groupId}/headers/{headerId}/items/{itemId}")
	public ApiResponse<Map<String, PlatformComponentItemRowDto>> getItem(@PathVariable("groupId") UUID groupId,
			@PathVariable("headerId") UUID headerId, @PathVariable("itemId") UUID itemId,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		requirePlatformSuperadmin();
		return ApiResponse.of(Map.of("item", adminService.getItem(groupId, headerId, itemId, locale)),
				RequestIdFilter.currentRequestId(request));
	}

	@PostMapping("/{groupId}/headers/{headerId}/items")
	public ResponseEntity<ApiResponse<Map<String, PlatformComponentItemRowDto>>> createItem(
			@PathVariable("groupId") UUID groupId, @PathVariable("headerId") UUID headerId,
			@Valid @RequestBody PlatformComponentItemCreateRequest body,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		PlatformComponentItemRowDto row = adminService.createItem(groupId, headerId, body, actor,
				RequestIdFilter.currentRequestId(request), locale);
		String rid = RequestIdFilter.currentRequestId(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(Map.of("item", row), rid));
	}

	@PutMapping("/{groupId}/headers/{headerId}/items/{itemId}")
	public ApiResponse<Map<String, PlatformComponentItemRowDto>> updateItem(@PathVariable("groupId") UUID groupId,
			@PathVariable("headerId") UUID headerId, @PathVariable("itemId") UUID itemId,
			@Valid @RequestBody PlatformComponentItemPutRequest body,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		return ApiResponse.of(
				Map.of("item", adminService.updateItem(groupId, headerId, itemId, body, actor, RequestIdFilter.currentRequestId(request), locale)),
				RequestIdFilter.currentRequestId(request));
	}

	@DeleteMapping("/{groupId}/headers/{headerId}/items/{itemId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteItem(@PathVariable("groupId") UUID groupId, @PathVariable("headerId") UUID headerId,
			@PathVariable("itemId") UUID itemId, HttpServletRequest request) {
		UUID actor = requirePlatformSuperadmin();
		adminService.deleteItem(groupId, headerId, itemId, actor, RequestIdFilter.currentRequestId(request));
	}

	private UUID requirePlatformSuperadmin() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		platformOperatorService.requirePlatformSuperadmin(userId);
		return userId;
	}
}
