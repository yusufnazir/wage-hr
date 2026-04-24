package com.wagepayroll.domain.passwordreset;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, java.util.UUID> {

	Optional<PasswordResetTokenEntity> findByTokenSha256(String tokenSha256);
}
