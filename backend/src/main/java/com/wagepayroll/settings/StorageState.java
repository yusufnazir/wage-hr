package com.wagepayroll.settings;

/**
 * Derived from merged MinIO settings only (never persisted). See {@link MinioSettingsMergeService} and
 * {@code docs/modules/platform-settings.md}.
 */
public enum StorageState {

	/** No MinIO fields are set after DB → property merge. */
	STORAGE_DISABLED,

	/** Some but not all of endpoint, access key, secret, bucket are set after merge. */
	STORAGE_PARTIAL,

	/** All four fields are non-blank after merge; S3 client may be initialized. */
	STORAGE_READY
}
