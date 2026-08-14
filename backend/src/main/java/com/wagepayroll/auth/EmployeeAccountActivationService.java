package com.wagepayroll.auth;

import java.time.Instant;

import com.wagepayroll.api.dto.EmployeeAccountActivateRequest;
import com.wagepayroll.domain.employeeactivation.EmployeeAccountActivationTokenEntity;
import com.wagepayroll.domain.employeeactivation.EmployeeAccountActivationTokenRepository;
import com.wagepayroll.domain.user.UserAccountEntity;
import com.wagepayroll.domain.user.UserAccountRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmployeeAccountActivationService {

	private static final int MIN_PASSWORD = 8;

	private final EmployeeAccountActivationTokenRepository activationTokenRepository;
	private final UserAccountRepository userAccountRepository;
	private final PasswordEncoder passwordEncoder;

	public EmployeeAccountActivationService(EmployeeAccountActivationTokenRepository activationTokenRepository,
			UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
		this.activationTokenRepository = activationTokenRepository;
		this.userAccountRepository = userAccountRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public void activate(EmployeeAccountActivateRequest body) {
		if (body == null || body.token() == null || body.token().isBlank() || body.password() == null
				|| body.password().length() < MIN_PASSWORD) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_ACTIVATION_REQUEST");
		}
		String hash = Sha256Hex.ofUtf8String(body.token().trim());
		EmployeeAccountActivationTokenEntity row = activationTokenRepository.findByTokenSha256(hash)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN"));
		Instant now = Instant.now();
		if (row.getUsedAt() != null || row.getExpiresAt().isBefore(now)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN");
		}
		UserAccountEntity user = userAccountRepository.findById(row.getUserAccountId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN"));
		user.setPasswordHash(passwordEncoder.encode(body.password()));
		user.setUpdatedAt(now);
		if (user.getEmailVerifiedAt() == null) {
			user.setEmailVerifiedAt(now);
		}
		userAccountRepository.save(user);
		row.setUsedAt(now);
		activationTokenRepository.save(row);
	}
}
