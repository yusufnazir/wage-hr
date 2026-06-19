package com.wagepayroll.wagecomponent;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.wagepayroll.api.dto.PlatformWageComponentTemplateDependencyPutItem;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentTemplateEntity;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentTemplateRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlatformWageComponentTemplateDependencyServiceTest {

	@Autowired
	private PlatformWageComponentTemplateDependencyService dependencyService;

	@Autowired
	private PlatformWageComponentTemplateRepository templateRepository;

	@Test
	void rejectsCycleOnSave() {
		List<PlatformWageComponentTemplateEntity> sr = templateRepository
				.findByCountryCodeAndActiveIsTrueOrderByTemplateCodeAsc("SR");
		PlatformWageComponentTemplateEntity t1001 = sr.stream().filter(t -> "1001".equals(t.getTemplateCode())).findFirst()
				.orElseThrow();
		PlatformWageComponentTemplateEntity t1002 = sr.stream().filter(t -> "1002".equals(t.getTemplateCode())).findFirst()
				.orElseThrow();
		dependencyService.replaceForTemplate(t1001.getId(),
				List.of(new PlatformWageComponentTemplateDependencyPutItem(t1002.getId())));
		assertThatThrownBy(() -> dependencyService.replaceForTemplate(t1002.getId(),
				List.of(new PlatformWageComponentTemplateDependencyPutItem(t1001.getId()))))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("DEPENDENCY_CYCLE");
	}
}
