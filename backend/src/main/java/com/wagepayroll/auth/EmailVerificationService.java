package com.wagepayroll.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.domain.emailverification.EmailVerificationTokenEntity;
import com.wagepayroll.domain.emailverification.EmailVerificationTokenRepository;
import com.wagepayroll.domain.membership.MembershipRepository;
import com.wagepayroll.domain.tenant.TenantEntity;
import com.wagepayroll.domain.tenant.TenantRepository;
import com.wagepayroll.domain.user.UserAccountEntity;
import com.wagepayroll.domain.user.UserAccountRepository;
import com.wagepayroll.settings.PlatformBrandingService;
import com.wagepayroll.settings.PlatformUrlJoin;
import com.wagepayroll.config.AppAuthProperties;
import com.wagepayroll.security.ForgotPasswordRateLimiter;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmailVerificationService {

	private static final int RAW_TOKEN_BYTES = 32;

	private final UserAccountRepository users;
	private final EmailVerificationTokenRepository tokens;
	private final MembershipRepository memberships;
	private final TenantRepository tenants;
	private final EmailVerificationMailPort mailPort;
	private final ForgotPasswordRateLimiter forgotPasswordRateLimiter;
	private final PlatformBrandingService platformBrandingService;
	private final AppAuthProperties authProperties;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public EmailVerificationService(UserAccountRepository users, EmailVerificationTokenRepository tokens,
			MembershipRepository memberships, TenantRepository tenants, EmailVerificationMailPort mailPort,
			ForgotPasswordRateLimiter forgotPasswordRateLimiter, PlatformBrandingService platformBrandingService,
			AppAuthProperties authProperties) {
		this.users = users;
		this.tokens = tokens;
		this.memberships = memberships;
		this.tenants = tenants;
		this.mailPort = mailPort;
		this.forgotPasswordRateLimiter = forgotPasswordRateLimiter;
		this.platformBrandingService = platformBrandingService;
		this.authProperties = authProperties;
		this.clock = Clock.systemUTC();
	}

	@Transactional
	public void issueNewTokenAndSendMail(UserAccountEntity user) {
		issueNewTokenAndSendMail(user, resolveTenantHandleForUser(user.getId()));
	}

	@Transactional
	public void issueNewTokenAndSendMail(UserAccountEntity user, String tenantHandle) {
		Instant now = clock.instant();
		tokens.markUnusedConsumedForUser(user.getId(), now);
		byte[] raw = new byte[RAW_TOKEN_BYTES];
		random.nextBytes(raw);
		String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
		String sha = Sha256Hex.ofUtf8String(rawToken);
		EmailVerificationTokenEntity row = new EmailVerificationTokenEntity();
		row.setId(UUID.randomUUID());
		row.setUserAccount(user);
		row.setTokenSha256(sha);
		row.setExpiresAt(now.plus(authProperties.getEmailVerificationTtlHours(), ChronoUnit.HOURS));
		row.setCreatedAt(now);
		tokens.save(row);
		String base = platformBrandingService.publicBaseUrl();
		String verifyUrl = PlatformUrlJoin.joinPublicBaseAndPath(base, "/verify-email?token=" + rawToken);
		mailPort.sendEmailVerificationLink(user.getEmail(), verifyUrl, safe(user.getFirstName()), safe(tenantHandle),
				safe(user.getPreferredLocale()));
	}

	@Transactional
	public void verify(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VERIFICATION_TOKEN_INVALID");
		}
		String sha = Sha256Hex.ofUtf8String(rawToken.trim());
		EmailVerificationTokenEntity row = tokens.findByTokenSha256(sha)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "VERIFICATION_TOKEN_INVALID"));
		Instant now = clock.instant();
		UserAccountEntity user = users.findById(row.getUserAccount().getId()).orElseThrow();
		if (user.getEmailVerifiedAt() != null) {
			return;
		}
		if (row.getUsedAt() != null || row.getExpiresAt().isBefore(now)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VERIFICATION_TOKEN_INVALID");
		}
		user.setEmailVerifiedAt(now);
		user.setUpdatedAt(now);
		users.save(user);
		row.setUsedAt(now);
		tokens.save(row);
	}

	@Transactional
	public void resend(HttpServletRequest http, String rawEmail) {
		forgotPasswordRateLimiter.checkResendVerificationAllowed(http, rawEmail);
		forgotPasswordRateLimiter.recordResendVerificationAttempt(http, rawEmail);
		String normalized = rawEmail.trim().toLowerCase();
		var userOpt = users.findByEmailIgnoreCase(normalized);
		if (userOpt.isEmpty()) {
			return;
		}
		UserAccountEntity user = userOpt.get();
		if (user.getEmailVerifiedAt() != null) {
			return;
		}
		issueNewTokenAndSendMail(user);
	}

	private String resolveTenantHandleForUser(UUID userId) {
		Optional<TenantEntity> tenant = memberships.findByUserIdOrderByTenantIdAsc(userId).stream()
				.map(m -> tenants.findById(m.getTenantId())).filter(Optional::isPresent).map(Optional::get).findFirst();
		return tenant.map(TenantEntity::getHandle).map(this::safe).orElse("app");
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}
}
