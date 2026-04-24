package com.wagepayroll.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.config.AppPublicProperties;
import com.wagepayroll.domain.passwordreset.PasswordResetTokenEntity;
import com.wagepayroll.domain.passwordreset.PasswordResetTokenRepository;
import com.wagepayroll.domain.user.UserAccountEntity;
import com.wagepayroll.domain.user.UserAccountRepository;
import com.wagepayroll.security.ForgotPasswordRateLimiter;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordResetService {

	private static final int RAW_TOKEN_BYTES = 32;

	private final UserAccountRepository users;
	private final PasswordResetTokenRepository tokens;
	private final PasswordEncoder passwordEncoder;
	private final PasswordResetMailPort mailPort;
	private final ForgotPasswordRateLimiter forgotLimiter;
	private final AppPublicProperties publicProps;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public PasswordResetService(UserAccountRepository users, PasswordResetTokenRepository tokens,
			PasswordEncoder passwordEncoder, PasswordResetMailPort mailPort,
			ForgotPasswordRateLimiter forgotLimiter, AppPublicProperties publicProps) {
		this.users = users;
		this.tokens = tokens;
		this.passwordEncoder = passwordEncoder;
		this.mailPort = mailPort;
		this.forgotLimiter = forgotLimiter;
		this.publicProps = publicProps;
		this.clock = Clock.systemUTC();
	}

	@Transactional
	public void requestReset(HttpServletRequest http, String email) {
		forgotLimiter.checkAllowed(http, email);
		forgotLimiter.recordAttempt(http, email);
		String normalized = email.trim().toLowerCase();
		var userOpt = users.findByEmailIgnoreCase(normalized);
		if (userOpt.isEmpty()) {
			return;
		}
		UserAccountEntity user = userOpt.get();
		byte[] raw = new byte[RAW_TOKEN_BYTES];
		random.nextBytes(raw);
		String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
		String sha = Sha256Hex.ofUtf8String(rawToken);
		Instant now = clock.instant();
		PasswordResetTokenEntity row = new PasswordResetTokenEntity();
		row.setId(UUID.randomUUID());
		row.setUserAccount(user);
		row.setTokenSha256(sha);
		row.setExpiresAt(now.plus(1, ChronoUnit.HOURS));
		row.setCreatedAt(now);
		tokens.save(row);
		String origin = publicProps.getFrontendOrigin().replaceAll("/$", "");
		String resetUrl = origin + "/reset-password?token=" + rawToken;
		mailPort.sendPasswordResetLink(user.getEmail(), resetUrl);
	}

	@Transactional
	public void resetPassword(String rawToken, String newPassword) {
		if (rawToken == null || rawToken.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN");
		}
		String sha = Sha256Hex.ofUtf8String(rawToken.trim());
		PasswordResetTokenEntity row = tokens.findByTokenSha256(sha)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN"));
		Instant now = clock.instant();
		if (row.getUsedAt() != null || row.getExpiresAt().isBefore(now)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN");
		}
		UserAccountEntity user = row.getUserAccount();
		user.setPasswordHash(passwordEncoder.encode(newPassword));
		user.setUpdatedAt(now);
		row.setUsedAt(now);
		users.save(user);
	}
}
