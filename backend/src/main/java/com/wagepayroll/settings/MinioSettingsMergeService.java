package com.wagepayroll.settings;

import com.wagepayroll.config.MinioStorageProperties;
import com.wagepayroll.domain.setting.PlatformSettingRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MinioSettingsMergeService {

	private static final String K_ENDPOINT = "storage.minio.endpoint";
	private static final String K_ACCESS = "storage.minio.access_key";
	private static final String K_SECRET = "storage.minio.secret_key";
	private static final String K_BUCKET = "storage.minio.bucket";

	private final PlatformSettingRepository platformSettingRepository;
	private final MinioStorageProperties minioStorageProperties;

	public MinioSettingsMergeService(PlatformSettingRepository platformSettingRepository,
			MinioStorageProperties minioStorageProperties) {
		this.platformSettingRepository = platformSettingRepository;
		this.minioStorageProperties = minioStorageProperties;
	}

	@Transactional(readOnly = true)
	public MergedMinioSettings resolve() {
		String ep = coalesceTextSetting(K_ENDPOINT, minioStorageProperties.getEndpoint());
		String ak = coalesceTextSetting(K_ACCESS, minioStorageProperties.getAccessKey());
		String sk = coalesceTextSetting(K_SECRET, minioStorageProperties.getSecretKey());
		String bk = coalesceTextSetting(K_BUCKET, minioStorageProperties.getBucket());
		return new MergedMinioSettings(ep, ak, sk, bk);
	}

	/** Snapshot of merge outcome; not persisted. */
	@Transactional(readOnly = true)
	public StorageState resolveStorageState() {
		return resolve().storageState();
	}

	public boolean isObjectStorageConfigured() {
		return resolve().storageState() == StorageState.STORAGE_READY;
	}

	private String coalesceTextSetting(String key, String propertyFallback) {
		return platformSettingRepository.findByKey(key).map(e -> e.getValueText()).filter(StringUtils::hasText).map(String::trim)
				.orElseGet(() -> StringUtils.hasText(propertyFallback) ? propertyFallback.trim() : "");
	}

	public record MergedMinioSettings(String endpoint, String accessKey, String secretKey, String bucket) {

		public StorageState storageState() {
			int n = 0;
			if (StringUtils.hasText(endpoint)) {
				n++;
			}
			if (StringUtils.hasText(accessKey)) {
				n++;
			}
			if (StringUtils.hasText(secretKey)) {
				n++;
			}
			if (StringUtils.hasText(bucket)) {
				n++;
			}
			if (n == 4) {
				return StorageState.STORAGE_READY;
			}
			if (n == 0) {
				return StorageState.STORAGE_DISABLED;
			}
			return StorageState.STORAGE_PARTIAL;
		}

		public boolean isFullyConfigured() {
			return storageState() == StorageState.STORAGE_READY;
		}
	}
}
