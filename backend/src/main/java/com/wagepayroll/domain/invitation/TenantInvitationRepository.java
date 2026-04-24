package com.wagepayroll.domain.invitation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantInvitationRepository extends JpaRepository<TenantInvitationEntity, UUID> {

	Optional<TenantInvitationEntity> findByTokenHash(String tokenHash);

	List<TenantInvitationEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

	Optional<TenantInvitationEntity> findByTenantIdAndInvitedEmailIgnoreCaseAndStatus(UUID tenantId, String invitedEmail,
			String status);
}
