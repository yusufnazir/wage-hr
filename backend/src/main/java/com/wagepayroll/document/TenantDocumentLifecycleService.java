package com.wagepayroll.document;

import java.time.Instant;
import java.util.UUID;

import com.wagepayroll.config.MinioStorageProperties;
import com.wagepayroll.domain.document.TenantDocumentEntity;
import com.wagepayroll.domain.document.TenantDocumentRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantDocumentLifecycleService {

	private static final Logger log = LoggerFactory.getLogger(TenantDocumentLifecycleService.class);

	private final TenantDocumentMutationPolicy mutationPolicy;
	private final TenantDocumentRepository tenantDocumentRepository;
	private final MinioDocumentStorageGateway storageGateway;
	private final MinioStorageProperties storageProperties;

	public TenantDocumentLifecycleService(TenantDocumentMutationPolicy mutationPolicy,
			TenantDocumentRepository tenantDocumentRepository, MinioDocumentStorageGateway storageGateway,
			MinioStorageProperties storageProperties) {
		this.mutationPolicy = mutationPolicy;
		this.tenantDocumentRepository = tenantDocumentRepository;
		this.storageGateway = storageGateway;
		this.storageProperties = storageProperties;
	}

	@Transactional
	public void softDelete(UUID tenantId, UUID actorUserId, UUID documentId) {
		TenantDocumentEntity doc = mutationPolicy.requireUploaderDocument(tenantId, actorUserId, documentId);
		String storageKey = doc.getStorageKey();
		Instant now = Instant.now();
		doc.setDeletedAt(now);
		doc.setUpdatedAt(now);
		tenantDocumentRepository.save(doc);

		if (storageProperties.isDeleteObjectOnSoftDelete()) {
			MinioDocumentStorageGateway gateway = storageGateway;
			if (gateway.isOperational()) {
				try {
					gateway.deleteObject(storageKey);
				}
				catch (RuntimeException ex) {
					log.warn("S3 delete after soft-delete failed documentId={} key={}: {}", documentId, storageKey, ex.getMessage());
				}
			}
		}
	}
}
