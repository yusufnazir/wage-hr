package com.wagepayroll.auth;

import java.time.Instant;
import java.util.UUID;

import com.wagepayroll.domain.user.UserAccountEntity;
import com.wagepayroll.domain.user.UserAccountRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegistrationService {

	private final UserAccountRepository users;
	private final PasswordEncoder passwordEncoder;

	public RegistrationService(UserAccountRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public void register(String email, String rawPassword) {
		String normalized = email.trim().toLowerCase();
		if (users.findByEmailIgnoreCase(normalized).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED");
		}
		Instant now = Instant.now();
		UserAccountEntity u = new UserAccountEntity();
		u.setId(UUID.randomUUID());
		u.setEmail(normalized);
		u.setPasswordHash(passwordEncoder.encode(rawPassword));
		u.setPlatformSuperadmin(false);
		u.setPreferredLocale("en");
		u.setCreatedAt(now);
		u.setUpdatedAt(now);
		users.save(u);
	}
}
