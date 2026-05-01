package com.wagepayroll.domain.roletemplate;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleTemplateRepository extends JpaRepository<RoleTemplateEntity, UUID> {
	Optional<RoleTemplateEntity> findByCode(String code);

	@Query("select t from RoleTemplateEntity t where lower(t.code) = lower(:code)")
	Optional<RoleTemplateEntity> findByCodeIgnoreCase(@Param("code") String code);
}

