package com.wagepayroll.notification;

public enum NotificationRowStatus {

	PENDING,
	SENT,
	FAILED,
	READ;

	public String code() {
		return name();
	}

	public static NotificationRowStatus fromCode(String code) {
		return NotificationRowStatus.valueOf(code);
	}
}
