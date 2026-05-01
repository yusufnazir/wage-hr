package com.wagepayroll.domain.mailtemplate;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MailTemplateLocaleRepository extends JpaRepository<MailTemplateLocaleEntity, UUID> {

	List<MailTemplateLocaleEntity> findByMailTemplateIdOrderByLocaleAsc(UUID mailTemplateId);

	@Modifying
	@Query("delete from MailTemplateLocaleEntity l where l.mailTemplateId = :templateId")
	void deleteByMailTemplateId(@Param("templateId") UUID templateId);
}
