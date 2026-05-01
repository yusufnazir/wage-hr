package com.wagepayroll.domain.mailtemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MailTemplateRepository extends JpaRepository<MailTemplateEntity, UUID> {

	Optional<MailTemplateEntity> findByCodeAndActiveIsTrue(String code);

	List<MailTemplateEntity> findAllByOrderByCodeAsc();
}
