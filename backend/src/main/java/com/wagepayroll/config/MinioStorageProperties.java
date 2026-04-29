package com.wagepayroll.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage.minio")
public class MinioStorageProperties {

	/**
	 * S3 API endpoint (e.g. {@code http://127.0.0.1:9000}). When blank, presigned upload/download routes return 503.
	 */
	private String endpoint = "";

	private String accessKey = "";

	private String secretKey = "";

	private String bucket = "";

	private String region = "us-east-1";

	/** Presigned PUT lifetime (default 15 minutes). */
	private Duration uploadPresignTtl = Duration.ofMinutes(15);

	/** Presigned GET lifetime (default 5 minutes). */
	private Duration downloadPresignTtl = Duration.ofMinutes(5);

	/** Max declared upload size per object (default 50 MiB). */
	private long maxUploadBytes = 52_428_800L;

	/**
	 * When {@code true}, {@code POST .../tenant/documents/complete} performs a HEAD on the object and checks size (and
	 * content type when S3 returns one). Requires reachable MinIO/S3. Default {@code false} so CI/tests without a real
	 * bucket still pass; enable in environments where you want this guard.
	 */
	private boolean verifyObjectBeforeComplete = false;

	/**
	 * When {@code true} (default), soft-delete attempts a best-effort S3 delete of the object key. Failures are logged;
	 * DB soft-delete still commits.
	 */
	private boolean deleteObjectOnSoftDelete = true;

	/** Scheduled orphan scan + soft-deleted S3 retry (default off). */
	private OrphanCleanup orphanCleanup = new OrphanCleanup();

	public boolean isObjectStorageConfigured() {
		return endpoint != null && !endpoint.isBlank() && accessKey != null && !accessKey.isBlank() && secretKey != null
				&& !secretKey.isBlank() && bucket != null && !bucket.isBlank();
	}

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getAccessKey() {
		return accessKey;
	}

	public void setAccessKey(String accessKey) {
		this.accessKey = accessKey;
	}

	public String getSecretKey() {
		return secretKey;
	}

	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	public String getBucket() {
		return bucket;
	}

	public void setBucket(String bucket) {
		this.bucket = bucket;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public Duration getUploadPresignTtl() {
		return uploadPresignTtl;
	}

	public void setUploadPresignTtl(Duration uploadPresignTtl) {
		this.uploadPresignTtl = uploadPresignTtl;
	}

	public Duration getDownloadPresignTtl() {
		return downloadPresignTtl;
	}

	public void setDownloadPresignTtl(Duration downloadPresignTtl) {
		this.downloadPresignTtl = downloadPresignTtl;
	}

	public long getMaxUploadBytes() {
		return maxUploadBytes;
	}

	public void setMaxUploadBytes(long maxUploadBytes) {
		this.maxUploadBytes = maxUploadBytes;
	}

	public boolean isVerifyObjectBeforeComplete() {
		return verifyObjectBeforeComplete;
	}

	public void setVerifyObjectBeforeComplete(boolean verifyObjectBeforeComplete) {
		this.verifyObjectBeforeComplete = verifyObjectBeforeComplete;
	}

	public boolean isDeleteObjectOnSoftDelete() {
		return deleteObjectOnSoftDelete;
	}

	public void setDeleteObjectOnSoftDelete(boolean deleteObjectOnSoftDelete) {
		this.deleteObjectOnSoftDelete = deleteObjectOnSoftDelete;
	}

	public OrphanCleanup getOrphanCleanup() {
		return orphanCleanup;
	}

	public void setOrphanCleanup(OrphanCleanup orphanCleanup) {
		this.orphanCleanup = orphanCleanup;
	}

	public static class OrphanCleanup {

		/**
		 * When {@code true}, runs a scheduled job that (1) lists {@code tenants/} keys and deletes S3 objects with no
		 * matching {@code tenant_document} row if older than {@link #minObjectAge}, and (2) retries S3 delete for
		 * soft-deleted rows older than that age.
		 */
		private boolean enabled = false;

		/** UTC cron for the orphan cleanup job. */
		private String cron = "0 30 4 * * *";

		/**
		 * Ignore S3 objects (and soft-deleted rows) newer than this age so in-flight uploads are not mistaken for
		 * orphans.
		 */
		private Duration minObjectAge = Duration.ofHours(24);

		/** Max S3 keys to inspect per run (pagination stops earlier if reached). */
		private int maxKeysPerRun = 500;

		/** Max soft-deleted document rows to attempt S3 delete retry per run. */
		private int softDeletedRetryMax = 200;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getCron() {
			return cron;
		}

		public void setCron(String cron) {
			this.cron = cron;
		}

		public Duration getMinObjectAge() {
			return minObjectAge;
		}

		public void setMinObjectAge(Duration minObjectAge) {
			this.minObjectAge = minObjectAge;
		}

		public int getMaxKeysPerRun() {
			return maxKeysPerRun;
		}

		public void setMaxKeysPerRun(int maxKeysPerRun) {
			this.maxKeysPerRun = maxKeysPerRun;
		}

		public int getSoftDeletedRetryMax() {
			return softDeletedRetryMax;
		}

		public void setSoftDeletedRetryMax(int softDeletedRetryMax) {
			this.softDeletedRetryMax = softDeletedRetryMax;
		}
	}
}
