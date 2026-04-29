package com.wagepayroll.domain.roletemplate;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleTemplateRepository extends JpaRepository<RoleTemplateEntity, UUID> {
	Optional<RoleTemplateEntity> findByCode(String code);
}

