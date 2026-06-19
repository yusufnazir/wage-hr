package com.wagepayroll.wagecomponent;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.wagepayroll.api.dto.PlatformWageComponentCatalogRowDto;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.PlatformWageComponentRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformWageComponentCatalogService {

	private static final int MAX_PAGE_SIZE = 100;

	private final PlatformWageComponentRepository repository;

	public PlatformWageComponentCatalogService(PlatformWageComponentRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> list(int page, int size, String countryCode) {
		if (countryCode == null || countryCode.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COUNTRY_REQUIRED");
		}
		String cc = countryCode.trim().toUpperCase(Locale.ROOT);
		if (cc.length() != 2) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COUNTRY_INVALID");
		}
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int safePage = Math.max(page, 0);
		Page<PlatformWageComponentEntity> p = repository.findByCountryCodeOrderByCodeAsc(cc,
				PageRequest.of(safePage, safeSize));
		List<PlatformWageComponentCatalogRowDto> items = p.getContent().stream()
				.map(e -> new PlatformWageComponentCatalogRowDto(e.getId(), e.getCountryCode(), e.getCode(), e.getName()))
				.toList();
		Map<String, Object> out = new HashMap<>();
		out.put("items", items);
		out.put("totalElements", p.getTotalElements());
		out.put("page", p.getNumber());
		out.put("size", p.getSize());
		out.put("totalPages", p.getTotalPages());
		return out;
	}
}
