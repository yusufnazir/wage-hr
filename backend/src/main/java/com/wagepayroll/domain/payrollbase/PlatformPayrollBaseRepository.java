package com.wagepayroll.domain.payrollbase;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PlatformPayrollBaseRepository
		extends JpaRepository<PlatformPayrollBaseEntity, UUID>, JpaSpecificationExecutor<PlatformPayrollBaseEntity> {

	boolean existsByCode(String code);

	Optional<PlatformPayrollBaseEntity> findByCode(String code);

	List<PlatformPayrollBaseEntity> findByActiveIsTrueOrderByCodeAsc();
}
