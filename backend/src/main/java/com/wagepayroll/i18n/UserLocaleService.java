package com.wagepayroll.i18n;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.wagepayroll.domain.user.UserAccountEntity;
import com.wagepayroll.domain.user.UserAccountRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserLocaleService {

	/** Supported UI locale tags (normalized lowercase, hyphen). */
	private static final Set<String> SUPPORTED = Set.of("en", "nl");

	private final UserAccountRepository userAccountRepository;

	public UserLocaleService(UserAccountRepository userAccountRepository) {
		this.userAccountRepository = userAccountRepository;
	}

	@Transactional
	public String setPreferredLocale(UUID userId, String rawTag) {
		if (rawTag == null || rawTag.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_LOCALE");
		}
		String normalized = normalize(rawTag);
		if (!SUPPORTED.contains(normalized)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_LOCALE");
		}
		UserAccountEntity u = userAccountRepository.findById(userId).orElseThrow();
		u.setPreferredLocale(normalized);
		u.setUpdatedAt(Instant.now());
		return normalized;
	}

	static String normalize(String rawTag) {
		return rawTag.trim().toLowerCase(Locale.ROOT).replace('_', '-');
	}
}
