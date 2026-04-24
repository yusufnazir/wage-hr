package com.wagepayroll.api;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.api.dto.ForgotPasswordRequest;
import com.wagepayroll.api.dto.LoginRequest;
import com.wagepayroll.api.dto.RegisterRequest;
import com.wagepayroll.api.dto.ResetPasswordRequest;
import com.wagepayroll.auth.PasswordResetService;
import com.wagepayroll.auth.RegistrationService;
import com.wagepayroll.security.LoginAttemptService;
import com.wagepayroll.security.RedirectUrlValidator;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@Validated
public class AuthController {

	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository;
	private final LoginAttemptService loginAttemptService;
	private final RedirectUrlValidator redirectUrlValidator;
	private final RegistrationService registrationService;
	private final PasswordResetService passwordResetService;

	public AuthController(AuthenticationManager authenticationManager,
			SecurityContextRepository securityContextRepository, LoginAttemptService loginAttemptService,
			RedirectUrlValidator redirectUrlValidator, RegistrationService registrationService,
			PasswordResetService passwordResetService) {
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		this.loginAttemptService = loginAttemptService;
		this.redirectUrlValidator = redirectUrlValidator;
		this.registrationService = registrationService;
		this.passwordResetService = passwordResetService;
	}

	@PostMapping("/register")
	public ResponseEntity<ApiResponse<Map<String, String>>> register(@Valid @RequestBody RegisterRequest req,
			HttpServletRequest request) {
		registrationService.register(req.email(), req.password());
		String rid = RequestIdFilter.currentRequestId(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(Map.of("status", "registered"), rid));
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
			Authentication auth = authenticationManager
					.authenticate(new UsernamePasswordAuthenticationToken(req.email(), req.password()));
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
