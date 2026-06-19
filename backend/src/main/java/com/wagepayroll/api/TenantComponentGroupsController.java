package com.wagepayroll.api;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.wagepayroll.api.dto.TenantComponentGroupCreateRequest;
import com.wagepayroll.api.dto.TenantComponentGroupPutRequest;
import com.wagepayroll.api.dto.TenantComponentGroupRowDto;
import com.wagepayroll.api.dto.TenantComponentHeaderCreateRequest;
import com.wagepayroll.api.dto.TenantComponentHeaderPutRequest;
import com.wagepayroll.api.dto.TenantComponentHeaderRowDto;
import com.wagepayroll.api.dto.TenantComponentItemCreateRequest;
import com.wagepayroll.api.dto.TenantComponentItemPutRequest;
import com.wagepayroll.api.dto.TenantComponentItemRowDto;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.componentgroup.TenantComponentGroupService;
import com.wagepayroll.security.RequiresPrivilege;
import com.wagepayroll.tenant.TenantContext;

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
@RequestMapping("/api/v1/component-groups")
public class TenantComponentGroupsController {

	private final TenantComponentGroupService tenantComponentGroupService;

	public TenantComponentGroupsController(TenantComponentGroupService tenantComponentGroupService) {
		this.tenantComponentGroupService = tenantComponentGroupService;
	}

	@GetMapping
	@RequiresPrivilege("WAGE_COMPONENT_VIEW")
	public ApiResponse<Map<String, Object>> listGroups(@RequestParam("companyId") UUID companyId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			@RequestParam(name = "active", required = false) Boolean active,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		return ApiResponse.of(tenantComponentGroupService.listGroups(tenantId, companyId, page, size, active, locale),
				RequestIdFilter.currentRequestId(request));
	}

	@GetMapping("/{id}")
	@RequiresPrivilege("WAGE_COMPONENT_VIEW")
	public ApiResponse<Map<String, TenantComponentGroupRowDto>> getGroup(@PathVariable("id") UUID id,
			@RequestParam("companyId") UUID companyId,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		return ApiResponse.of(Map.of("group", tenantComponentGroupService.getGroup(tenantId, companyId, id, locale)),
				RequestIdFilter.currentRequestId(request));
	}

	@PostMapping
	@RequiresPrivilege("WAGE_COMPONENT_MANAGE")
	public ResponseEntity<ApiResponse<Map<String, TenantComponentGroupRowDto>>> createGroup(
			@Valid @RequestBody TenantComponentGroupCreateRequest body,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = currentUserId();
		TenantComponentGroupRowDto row = tenantComponentGroupService.createGroup(tenantId, body, actor,
				RequestIdFilter.currentRequestId(request), locale);
		String rid = RequestIdFilter.currentRequestId(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(Map.of("group", row), rid));
	}

	@PutMapping("/{id}")
	@RequiresPrivilege("WAGE_COMPONENT_MANAGE")
	public ApiResponse<Map<String, TenantComponentGroupRowDto>> updateGroup(@PathVariable("id") UUID id,
			@RequestParam("companyId") UUID companyId, @Valid @RequestBody TenantComponentGroupPutRequest body,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = currentUserId();
		return ApiResponse.of(
				Map.of("group", tenantComponentGroupService.updateGroup(tenantId, companyId, id, body, actor,
						RequestIdFilter.currentRequestId(request), locale)),
				RequestIdFilter.currentRequestId(request));
	}

	@DeleteMapping("/{id}")
	@RequiresPrivilege("WAGE_COMPONENT_MANAGE")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteGroup(@PathVariable("id") UUID id, @RequestParam("companyId") UUID companyId,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = currentUserId();
		tenantComponentGroupService.deleteGroup(tenantId, companyId, id, actor, RequestIdFilter.currentRequestId(request));
	}

	@GetMapping("/{groupId}/headers")
	@RequiresPrivilege("WAGE_COMPONENT_VIEW")
	public ApiResponse<Map<String, Object>> listHeaders(@PathVariable("groupId") UUID groupId,
			@RequestParam("companyId") UUID companyId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		return ApiResponse.of(tenantComponentGroupService.listHeaders(tenantId, companyId, groupId, page, size, locale),
				RequestIdFilter.currentRequestId(request));
	}

	@GetMapping("/{groupId}/headers/{headerId}")
	@RequiresPrivilege("WAGE_COMPONENT_VIEW")
	public ApiResponse<Map<String, TenantComponentHeaderRowDto>> getHeader(@PathVariable("groupId") UUID groupId,
			@PathVariable("headerId") UUID headerId, @RequestParam("companyId") UUID companyId,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		return ApiResponse.of(
				Map.of("header", tenantComponentGroupService.getHeader(tenantId, companyId, groupId, headerId, locale)),
				RequestIdFilter.currentRequestId(request));
	}

	@PostMapping("/{groupId}/headers")
	@RequiresPrivilege("WAGE_COMPONENT_MANAGE")
	public ResponseEntity<ApiResponse<Map<String, TenantComponentHeaderRowDto>>> createHeader(
			@PathVariable("groupId") UUID groupId, @RequestParam("companyId") UUID companyId,
			@Valid @RequestBody TenantComponentHeaderCreateRequest body,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = currentUserId();
		TenantComponentHeaderRowDto row = tenantComponentGroupService.createHeader(tenantId, companyId, groupId, body, actor,
				RequestIdFilter.currentRequestId(request), locale);
		String rid = RequestIdFilter.currentRequestId(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(Map.of("header", row), rid));
	}

	@PutMapping("/{groupId}/headers/{headerId}")
	@RequiresPrivilege("WAGE_COMPONENT_MANAGE")
	public ApiResponse<Map<String, TenantComponentHeaderRowDto>> updateHeader(@PathVariable("groupId") UUID groupId,
			@PathVariable("headerId") UUID headerId, @RequestParam("companyId") UUID companyId,
			@Valid @RequestBody TenantComponentHeaderPutRequest body,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = currentUserId();
		return ApiResponse.of(
				Map.of("header", tenantComponentGroupService.updateHeader(tenantId, companyId, groupId, headerId, body, actor,
						RequestIdFilter.currentRequestId(request), locale)),
				RequestIdFilter.currentRequestId(request));
	}

	@DeleteMapping("/{groupId}/headers/{headerId}")
	@RequiresPrivilege("WAGE_COMPONENT_MANAGE")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteHeader(@PathVariable("groupId") UUID groupId, @PathVariable("headerId") UUID headerId,
			@RequestParam("companyId") UUID companyId, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = currentUserId();
		tenantComponentGroupService.deleteHeader(tenantId, companyId, groupId, headerId, actor,
				RequestIdFilter.currentRequestId(request));
	}

	@GetMapping("/{groupId}/headers/{headerId}/items")
	@RequiresPrivilege("WAGE_COMPONENT_VIEW")
	public ApiResponse<Map<String, Object>> listItems(@PathVariable("groupId") UUID groupId,
			@PathVariable("headerId") UUID headerId, @RequestParam("companyId") UUID companyId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		return ApiResponse.of(
				tenantComponentGroupService.listItems(tenantId, companyId, groupId, headerId, page, size, locale),
				RequestIdFilter.currentRequestId(request));
	}

	@GetMapping("/{groupId}/headers/{headerId}/items/{itemId}")
	@RequiresPrivilege("WAGE_COMPONENT_VIEW")
	public ApiResponse<Map<String, TenantComponentItemRowDto>> getItem(@PathVariable("groupId") UUID groupId,
			@PathVariable("headerId") UUID headerId, @PathVariable("itemId") UUID itemId,
			@RequestParam("companyId") UUID companyId,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		return ApiResponse.of(
				Map.of("item", tenantComponentGroupService.getItem(tenantId, companyId, groupId, headerId, itemId, locale)),
				RequestIdFilter.currentRequestId(request));
	}

	@PostMapping("/{groupId}/headers/{headerId}/items")
	@RequiresPrivilege("WAGE_COMPONENT_MANAGE")
	public ResponseEntity<ApiResponse<Map<String, TenantComponentItemRowDto>>> createItem(
			@PathVariable("groupId") UUID groupId, @PathVariable("headerId") UUID headerId,
			@RequestParam("companyId") UUID companyId, @Valid @RequestBody TenantComponentItemCreateRequest body,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = currentUserId();
		TenantComponentItemRowDto row = tenantComponentGroupService.createItem(tenantId, companyId, groupId, headerId, body,
				actor, RequestIdFilter.currentRequestId(request), locale);
		String rid = RequestIdFilter.currentRequestId(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(Map.of("item", row), rid));
	}

	@PutMapping("/{groupId}/headers/{headerId}/items/{itemId}")
	@RequiresPrivilege("WAGE_COMPONENT_MANAGE")
	public ApiResponse<Map<String, TenantComponentItemRowDto>> updateItem(@PathVariable("groupId") UUID groupId,
			@PathVariable("headerId") UUID headerId, @PathVariable("itemId") UUID itemId,
			@RequestParam("companyId") UUID companyId, @Valid @RequestBody TenantComponentItemPutRequest body,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = currentUserId();
		return ApiResponse.of(
				Map.of("item", tenantComponentGroupService.updateItem(tenantId, companyId, groupId, headerId, itemId, body,
						actor, RequestIdFilter.currentRequestId(request), locale)),
				RequestIdFilter.currentRequestId(request));
	}

	@DeleteMapping("/{groupId}/headers/{headerId}/items/{itemId}")
	@RequiresPrivilege("WAGE_COMPONENT_MANAGE")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteItem(@PathVariable("groupId") UUID groupId, @PathVariable("headerId") UUID headerId,
			@PathVariable("itemId") UUID itemId, @RequestParam("companyId") UUID companyId, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		UUID actor = currentUserId();
		tenantComponentGroupService.deleteItem(tenantId, companyId, groupId, headerId, itemId, actor,
				RequestIdFilter.currentRequestId(request));
	}

	private static UUID currentUserId() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return UUID.fromString(auth.getName());
	}
}
