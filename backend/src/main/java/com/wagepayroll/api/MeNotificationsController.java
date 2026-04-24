package com.wagepayroll.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.api.dto.NotificationListItemDto;
import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.domain.notification.NotificationEntity;
import com.wagepayroll.domain.notification.NotificationRepository;
import com.wagepayroll.notification.NotificationService;
import com.wagepayroll.tenant.TenantContext;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/notifications")
public class MeNotificationsController {

	private static final int DEFAULT_LIMIT = 50;
	private static final int MAX_LIMIT = 100;

	private final NotificationRepository notificationRepository;
	private final NotificationService notificationService;

	public MeNotificationsController(NotificationRepository notificationRepository, NotificationService notificationService) {
		this.notificationRepository = notificationRepository;
		this.notificationService = notificationService;
	}

	@GetMapping
	public ApiResponse<Map<String, Object>> list(@RequestParam(name = "limit", required = false) Integer limitParam,
			@RequestParam(name = "offset", required = false) Integer offsetParam, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		int limit = limitParam == null ? DEFAULT_LIMIT : limitParam;
		int offset = offsetParam == null ? 0 : offsetParam;
		limit = Math.clamp(limit, 1, MAX_LIMIT);
		offset = Math.max(0, offset);
		long total = notificationRepository.countByTenantIdAndRecipientUserId(tenantId, userId);
		List<NotificationListItemDto> items = notificationRepository
				.findSliceDesc(tenantId.toString(), userId.toString(), limit, offset).stream()
				.map(MeNotificationsController::toDto)
				.collect(Collectors.toList());
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("items", items);
		data.put("total", total);
		data.put("limit", limit);
		data.put("offset", offset);
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(data, rid);
	}

	@PatchMapping("/{id}/read")
	public ResponseEntity<Void> markRead(@PathVariable("id") UUID id, HttpServletRequest request) {
		UUID tenantId = TenantContext.requireTenantId();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		UUID userId = UUID.fromString(auth.getName());
		notificationService.markRead(tenantId, userId, id);
		return ResponseEntity.noContent().build();
	}

	private static NotificationListItemDto toDto(NotificationEntity e) {
		return new NotificationListItemDto(e.getId(), e.getTenantId(), e.getNotificationType(), e.getTemplateVersion(),
				e.getCorrelationId(), e.getExternalMessageId(), e.getStatus(), e.getReadAt(), e.getCreatedAt());
	}
}
