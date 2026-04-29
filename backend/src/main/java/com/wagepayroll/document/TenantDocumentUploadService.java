package com.wagepayroll.document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.wagepayroll.config.MinioStorageProperties;
import com.wagepayroll.domain.document.TenantDocumentEntity;
import com.wagepayroll.domain.document.TenantDocumentRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantDocumentUploadService {

	private final MinioDocumentStorageGateway storageGateway;
	private final MinioStorageProperties storageProperties;
	private final TenantDocumentRepository tenantDocumentRepository;
	private final DocumentAccessService documentAccessService;

	public TenantDocumentUploadService(MinioDocumentStorageGateway storageGateway, MinioStorageProperties storageProperties,
			TenantDocumentRepository tenantDocumentRepository, DocumentAccessService documentAccessService) {
		this.storageGateway = storageGateway;
		this.storageProperties = storageProperties;
		this.tenantDocumentRepository = tenantDocumentRepository;
		this.documentAccessService = documentAccessService;
	}

	public UploadSessionResult createUploadSession(UUID tenantId, String originalFilename, String contentType, long sizeBytes) {
		MinioDocumentStorageGateway gateway = requireGateway();
		validateUploadMetadata(originalFilename, contentType, sizeBytes);
		UUID documentId = UUID.randomUUID();
		String objectKey = DocumentStoragePaths.buildObjectKey(tenantId, documentId, originalFilename);
		Instant expiresAt = Instant.now().plus(gateway.getUploadPresignTtl());
		String uploadUrl = gateway.presignPut(objectKey, contentType, gateway.getUploadPresignTtl());
		return new UploadSessionResult(documentId, objectKey, uploadUrl, expiresAt, Map.of("Content-Type", contentType));
	}

	@Transactional
	public TenantDocumentEntity completeUpload(UUID tenantId, UUID userId, UUID documentId, String storageKey,
			String originalFilename, String contentType, long sizeBytes) {
		MinioDocumentStorageGateway gateway = requireGateway();
		validateUploadMetadata(originalFilename, contentType, sizeBytes);
		String expectedKey = DocumentStoragePaths.buildObjectKey(tenantId, documentId, originalFilename);
		if (!expectedKey.equals(storageKey)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "STORAGE_KEY_MISMATCH");
		}
		if (tenantDocumentRepository.existsById(documentId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "DOCUMENT_ALREADY_EXISTS");
		}
		if (gateway.isVerifyObjectBeforeComplete()) {
			gateway.verifyUploadedObject(storageKey, sizeBytes, contentType.trim());
		}
		Instant now = Instant.now();
		TenantDocumentEntity e = new TenantDocumentEntity();
		e.setId(documentId);
		e.setTenantId(tenantId);
		e.setStorageKey(storageKey);
		e.setOriginalFilename(originalFilename.trim());
		e.setContentType(contentType.trim());
		e.setSizeBytes(sizeBytes);
		e.setUploadedByUserId(userId);
		e.setCreatedAt(now);
		e.setUpdatedAt(now);
		return tenantDocumentRepository.save(e);
	}

	public DownloadUrlResult presignDownload(UUID tenantId, UUID userId, UUID documentId) {
		MinioDocumentStorageGateway gateway = requireGateway();
		TenantDocumentEntity doc = documentAccessService.findReadableDocument(tenantId, userId, documentId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND"));
		Instant expiresAt = Instant.now().plus(gateway.getDownloadPresignTtl());
		String url = gateway.presignGet(doc.getStorageKey(), gateway.getDownloadPresignTtl());
		return new DownloadUrlResult(url, expiresAt);
	}

	private void validateUploadMetadata(String originalFilename, String contentType, long sizeBytes) {
		if (originalFilename == null || originalFilename.isBlank() || originalFilename.length() > 255) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ORIGINAL_FILENAME");
		}
		if (contentType == null || contentType.isBlank() || contentType.length() > 128) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_CONTENT_TYPE");
		}
		if (sizeBytes < 0 || sizeBytes > storageProperties.getMaxUploadBytes()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_SIZE_BYTES");
		}
		try {
			DocumentStoragePaths.sanitizeFilenameSegment(originalFilename);
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ORIGINAL_FILENAME");
		}
	}

	private MinioDocumentStorageGateway requireGateway() {
		if (!storageGateway.isOperational()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_NOT_CONFIGURED");
		}
		return storageGateway;
	}

	public record UploadSessionResult(UUID documentId, String storageKey, String uploadUrl, Instant expiresAt,
			Map<String, String> requiredHeaders) {
	}

	public record DownloadUrlResult(String downloadUrl, Instant expiresAt) {
	}
}
