package com.wagepayroll.domain.employeeactivation;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeAccountActivationTokenRepository extends JpaRepository<EmployeeAccountActivationTokenEntity, UUID> {

	Optional<EmployeeAccountActivationTokenEntity> findByTokenSha256(String tokenSha256);
}
