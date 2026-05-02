package com.wagepayroll.api;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.country.PlatformCountryService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/countries")
public class CountriesController {

	private final PlatformCountryService platformCountryService;

	public CountriesController(PlatformCountryService platformCountryService) {
		this.platformCountryService = platformCountryService;
	}

	@GetMapping
	public ApiResponse<Map<String, Object>> list(
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "50") int size,
			@RequestParam(name = "search", required = false) String search,
			@RequestParam(name = "locale", required = false, defaultValue = "en") String locale,
			HttpServletRequest request) {
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(platformCountryService.listActive(page, size, search, locale), rid);
	}
}
