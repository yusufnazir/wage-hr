package com.wagepayroll.document;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.wagepayroll.config.MinioStorageProperties;
import com.wagepayroll.settings.MinioSettingsMergeService;
import com.wagepayroll.settings.MinioSettingsMergeService.MergedMinioSettings;
import com.wagepayroll.settings.StorageState;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * MinIO / S3-compatible presigned URLs (path-style) plus HEAD/DELETE helpers. Credentials resolve from
 * {@code platform_setting} first, then {@code app.storage.minio.*} (see {@code docs/modules/platform-settings.md}).
 */
@Component
public class MinioDocumentStorageGateway {

	private final MinioSettingsMergeService minioSettingsMergeService;
	private final MinioStorageProperties minioStorageProperties;

	private volatile StorageState storageState = StorageState.STORAGE_DISABLED;
	private volatile S3Client s3Client;
	private volatile S3Presigner presigner;
	private volatile String resolvedBucket = "";

	public MinioDocumentStorageGateway(MinioSettingsMergeService minioSettingsMergeService,
			MinioStorageProperties minioStorageProperties) {
		this.minioSettingsMergeService = minioSettingsMergeService;
		this.minioStorageProperties = minioStorageProperties;
	}

	@PostConstruct
	void initClients() {
		MergedMinioSettings m = minioSettingsMergeService.resolve();
		this.storageState = m.storageState();
		if (storageState != StorageState.STORAGE_READY) {
			return;
		}
		var creds = StaticCredentialsProvider.create(AwsBasicCredentials.create(m.accessKey(), m.secretKey()));
		var endpoint = URI.create(m.endpoint().trim());
		var region = Region.of(minioStorageProperties.getRegion());
		var s3cfg = S3Configuration.builder().pathStyleAccessEnabled(true).build();
		this.s3Client = S3Client.builder().endpointOverride(endpoint).region(region).credentialsProvider(creds).serviceConfiguration(s3cfg)
				.build();
		this.presigner = S3Presigner.builder().endpointOverride(endpoint).region(region).credentialsProvider(creds).serviceConfiguration(s3cfg)
				.build();
		this.resolvedBucket = m.bucket().trim();
	}

	public StorageState getStorageState() {
		return storageState;
	}

	public boolean isOperational() {
		return storageState == StorageState.STORAGE_READY && s3Client != null && presigner != null;
	}

	public String bucket() {
		return resolvedBucket;
	}

	public String presignPut(String objectKey, String contentType, Duration ttl) {
		requireOperational();
		PutObjectRequest put = PutObjectRequest.builder().bucket(resolvedBucket).key(objectKey).contentType(contentType).build();
		PutObjectPresignRequest presign = PutObjectPresignRequest.builder().signatureDuration(ttl).putObjectRequest(put).build();
		PresignedPutObjectRequest signed = presigner.presignPutObject(presign);
		return signed.url().toString();
	}

	public String presignGet(String objectKey, Duration ttl) {
		requireOperational();
		GetObjectRequest get = GetObjectRequest.builder().bucket(resolvedBucket).key(objectKey).build();
		GetObjectPresignRequest presign = GetObjectPresignRequest.builder().signatureDuration(ttl).getObjectRequest(get).build();
		PresignedGetObjectRequest signed = presigner.presignGetObject(presign);
		return signed.url().toString();
	}

	public void verifyUploadedObject(String objectKey, long expectedSizeBytes, String expectedContentType) {
		requireOperational();
		try {
			HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder().bucket(resolvedBucket).key(objectKey).build());
			long actual = head.contentLength() == null ? -1L : head.contentLength();
			if (actual != expectedSizeBytes) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UPLOAD_SIZE_MISMATCH");
			}
			if (expectedContentType != null && !expectedContentType.isBlank()) {
				String ct = head.contentType();
				if (ct != null && !ct.equalsIgnoreCase(expectedContentType.trim())) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UPLOAD_CONTENT_TYPE_MISMATCH");
				}
			}
		}
		catch (ResponseStatusException e) {
			throw e;
		}
		catch (S3Exception e) {
			if (e.statusCode() == 404) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UPLOAD_OBJECT_MISSING");
			}
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_VERIFY_FAILED");
		}
		catch (SdkClientException e) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_VERIFY_FAILED");
		}
	}

	public void deleteObject(String objectKey) {
		requireOperational();
		s3Client.deleteObject(DeleteObjectRequest.builder().bucket(resolvedBucket).key(objectKey).build());
	}

	public S3ObjectListPage listObjectsPage(String prefix, int maxKeys, String continuationToken) {
		requireOperational();
		int n = Math.min(1000, Math.max(1, maxKeys));
		ListObjectsV2Request.Builder req = ListObjectsV2Request.builder().bucket(resolvedBucket).prefix(prefix).maxKeys(n);
		if (continuationToken != null && !continuationToken.isBlank()) {
			req.continuationToken(continuationToken);
		}
		ListObjectsV2Response resp = s3Client.listObjectsV2(req.build());
		List<S3ObjectListingItem> items = resp.contents() == null || resp.contents().isEmpty() ? List.of()
				: resp.contents().stream()
						.map(s -> new S3ObjectListingItem(s.key(), s.lastModified() != null ? s.lastModified() : Instant.EPOCH))
						.toList();
		String next = Boolean.TRUE.equals(resp.isTruncated()) && resp.nextContinuationToken() != null ? resp.nextContinuationToken() : null;
		return new S3ObjectListPage(items, next);
	}

	public record S3ObjectListingItem(String key, Instant lastModified) {
	}

	public record S3ObjectListPage(List<S3ObjectListingItem> contents, String nextContinuationToken) {
	}

	private void requireOperational() {
		if (storageState != StorageState.STORAGE_READY || s3Client == null || presigner == null) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_NOT_CONFIGURED");
		}
	}

	@PreDestroy
	public void close() {
		if (s3Client != null) {
			s3Client.close();
		}
		if (presigner != null) {
			presigner.close();
		}
	}

	public Duration getUploadPresignTtl() {
		return minioStorageProperties.getUploadPresignTtl();
	}

	public Duration getDownloadPresignTtl() {
		return minioStorageProperties.getDownloadPresignTtl();
	}

	public long getMaxUploadBytes() {
		return minioStorageProperties.getMaxUploadBytes();
	}

	public boolean isVerifyObjectBeforeComplete() {
		return minioStorageProperties.isVerifyObjectBeforeComplete();
	}
}
