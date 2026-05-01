package com.wagepayroll.domain.roletemplate;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleTemplatePrivilegeRepository extends JpaRepository<RoleTemplatePrivilegeEntity, UUID> {

	@Query("select rtp.privilegeId from RoleTemplatePrivilegeEntity rtp where rtp.roleTemplateId = :templateId")
	List<UUID> findPrivilegeIdsByTemplateId(@Param("templateId") UUID templateId);

	@Modifying
	@Query("delete from RoleTemplatePrivilegeEntity rtp where rtp.roleTemplateId = :templateId")
	void deleteByTemplateId(@Param("templateId") UUID templateId);
}

