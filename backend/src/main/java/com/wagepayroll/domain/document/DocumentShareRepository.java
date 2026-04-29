package com.wagepayroll.domain.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentShareRepository extends JpaRepository<DocumentShareEntity, UUID> {

	List<DocumentShareEntity> findByTenantIdAndDocumentIdOrderByCreatedAtAsc(UUID tenantId, UUID documentId);

	Optional<DocumentShareEntity> findByIdAndTenantIdAndDocumentId(UUID id, UUID tenantId, UUID documentId);

	boolean existsByTenantIdAndDocumentIdAndGranteeRoleId(UUID tenantId, UUID documentId, UUID granteeRoleId);

	@Query("select distinct s.documentId from DocumentShareEntity s where s.tenantId = :tenantId and s.granteeUserId = :userId")
	List<UUID> findDocumentIdsSharedToUser(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

	@Query("select distinct s.documentId from DocumentShareEntity s where s.tenantId = :tenantId and s.granteeRoleId in :roleIds")
	List<UUID> findDocumentIdsSharedToRoles(@Param("tenantId") UUID tenantId, @Param("roleIds") List<UUID> roleIds);

	boolean existsByTenantIdAndDocumentIdAndGranteeUserId(UUID tenantId, UUID documentId, UUID granteeUserId);

	@Query("select case when count(s) > 0 then true else false end from DocumentShareEntity s where s.tenantId = :tenantId and s.documentId = :documentId and s.granteeRoleId in :roleIds")
	boolean existsByTenantIdAndDocumentIdAndGranteeRoleIdIn(@Param("tenantId") UUID tenantId, @Param("documentId") UUID documentId,
			@Param("roleIds") List<UUID> roleIds);
}
