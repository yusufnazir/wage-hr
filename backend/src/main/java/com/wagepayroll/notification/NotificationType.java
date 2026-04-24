package com.wagepayroll.notification;

/**
 * Stored on {@code notification.notification_type}; codes only (no descriptive DB text).
 *
 * @see com.wagepayroll.domain.notification.NotificationEntity
 */
public enum NotificationType {

	/**
	 * User completed an invitation and gained membership; {@code correlation_id} is {@code tenant_invitation.id}.
	 */
	TENANT_JOINED;

	public String code() {
		return name();
	}

	public static NotificationType fromCode(String code) {
		return NotificationType.valueOf(code);
	}
}
