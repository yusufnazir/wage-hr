package com.wagepayroll.domain.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, UUID> {

	Optional<UserAccountEntity> findByEmailIgnoreCase(String email);
}
