package com.wagepayroll.domain.privilege;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivilegeRepository extends JpaRepository<PrivilegeEntity, UUID> {

	Optional<PrivilegeEntity> findByCode(String code);

	boolean existsByCode(String code);

	List<PrivilegeEntity> findAllByOrderByCodeAsc();
}
