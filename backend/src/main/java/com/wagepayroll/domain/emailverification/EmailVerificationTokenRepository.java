package com.wagepayroll.domain.emailverification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationTokenEntity, UUID> {

	Optional<EmailVerificationTokenEntity> findByTokenSha256(String tokenSha256);

	List<EmailVerificationTokenEntity> findByUserAccount_Id(UUID userAccountId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update EmailVerificationTokenEntity t set t.usedAt = :now where t.userAccount.id = :userId and t.usedAt is null")
	int markUnusedConsumedForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
