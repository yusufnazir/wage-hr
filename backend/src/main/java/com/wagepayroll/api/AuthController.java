package com.wagepayroll.api;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.api.dto.ForgotPasswordRequest;
import com.wagepayroll.api.dto.LoginRequest;
import com.wagepayroll.api.dto.RegisterRequest;
import com.wagepayroll.api.dto.ResendVerificationRequest;
import com.wagepayroll.api.dto.ResetPasswordRequest;
import com.wagepayroll.api.dto.VerifyEmailRequest;
import com.wagepayroll.auth.EmailVerificationService;
import com.wagepayroll.auth.PasswordResetService;
import com.wagepayroll.auth.RegistrationService;
import com.wagepayroll.domain.user.UserAccountEntity;
import com.wagepayroll.domain.user.UserAccountRepository;
import com.wagepayroll.security.AccountUserDetailsService;
import com.wagepayroll.security.LoginAttemptService;
import com.wagepayroll.security.RedirectUrlValidator;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@Validated
public class AuthController {

	private final SecurityContextRepository securityContextRepository;
	private final LoginAttemptService loginAttemptService;
	private final RedirectUrlValidator redirectUrlValidator;
	private final RegistrationService registrationService;
	private final PasswordResetService passwordResetService;
	private final EmailVerificationService emailVerificationService;
	private final UserAccountRepository userAccountRepository;
	private final PasswordEncoder passwordEncoder;
	private final AccountUserDetailsService accountUserDetailsService;

	public AuthController(SecurityContextRepository securityContextRepository, LoginAttemptService loginAttemptService,
			RedirectUrlValidator redirectUrlValidator, RegistrationService registrationService,
			PasswordResetService passwordResetService, EmailVerificationService emailVerificationService,
			UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder,
			AccountUserDetailsService accountUserDetailsService) {
		this.securityContextRepository = securityContextRepository;
		this.loginAttemptService = loginAttemptService;
		this.redirectUrlValidator = redirectUrlValidator;
		this.registrationService = registrationService;
		this.passwordResetService = passwordResetService;
		this.emailVerificationService = emailVerificationService;
		this.userAccountRepository = userAccountRepository;
		this.passwordEncoder = passwordEncoder;
		this.accountUserDetailsService = accountUserDetailsService;
	}

	@PostMapping("/register")
	public ResponseEntity<ApiResponse<Map<String, String>>> register(@Valid @RequestBody RegisterRequest req,
			HttpServletRequest request) {
		String handle = registrationService.register(req);
		String rid = RequestIdFilter.currentRequestId(request);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.of(Map.of("status", "pending_verification", "tenantHandle", handle), rid));
	}

	@PostMapping("/verify-email")
	public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
		emailVerificationService.verify(req.token());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/resend-verification")
	public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest req,
			HttpServletRequest request) {
		emailVerificationService.resend(request, req.email());
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/forgot-password")
	public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req,
			HttpServletRequest request) {
		passwordResetService.requestReset(request, req.email());
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/reset-password")
	public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
		passwordResetService.resetPassword(req.token(), req.newPassword());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<Map<String, String>>> login(@Valid @RequestBody LoginRequest req,
			HttpServletRequest request, HttpServletResponse response) {
		try {
			loginAttemptService.checkAllowed(request, req.email());
			String email = req.email().trim().toLowerCase();
			UserAccountEntity user = userAccountRepository.findByEmailIgnoreCase(email).orElse(null);
			if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
				loginAttemptService.recordFailure(request, req.email());
				throw new BadCredentialsException("Bad credentials");
			}
			if (user.getEmailVerifiedAt() == null) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED");
			}
			UserDetails ud = accountUserDetailsService.userDetailsForSession(user);
			Authentication auth = new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(auth);
			SecurityContextHolder.setContext(context);
			securityContextRepository.saveContext(context, request, response);
			loginAttemptService.clearFailures(request, req.email());
			String rid = RequestIdFilter.currentRequestId(request);
			return ResponseEntity.ok(ApiResponse.of(Map.of("status", "authenticated"), rid));
		}
		catch (BadCredentialsException e) {
			loginAttemptService.recordFailure(request, req.email());
			throw e;
		}
	}

	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Map<String, String>>> logout(HttpServletRequest request, HttpServletResponse response) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null) {
			new SecurityContextLogoutHandler().logout(request, response, auth);
		}
		String rid = RequestIdFilter.currentRequestId(request);
		return ResponseEntity.ok(ApiResponse.of(Map.of("status", "logged_out"), rid));
	}

	/**
	 * Safe redirect validation for {@code returnTo} (relative path or same-site host under BASE_DOMAIN).
	 */
	@GetMapping("/redirect-check")
	public ResponseEntity<Void> redirectCheck(@RequestParam("returnTo") String returnTo) {
		if (!redirectUrlValidator.isAllowedForLocalDev(returnTo)) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.noContent().build();
	}
}
