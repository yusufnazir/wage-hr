package com.wagepayroll.domain.componentgroup;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformComponentGroupTemplateRepository extends JpaRepository<PlatformComponentGroupTemplateEntity, UUID> {

	@Query("""
			select g from PlatformComponentGroupTemplateEntity g
			join g.country c
			where (:iso2 is null or upper(c.isoAlpha2) = upper(:iso2))
			and (:active is null or g.active = :active)
			order by g.sortOrder asc, g.id asc
			""")
	Page<PlatformComponentGroupTemplateEntity> search(@Param("iso2") String iso2, @Param("active") Boolean active,
			Pageable pageable);
}
