package com.wagepayroll.common.api;

import java.net.URI;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProblemDetailControllerAdvice {

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ProblemDetail> missingServletRequestParameter(MissingServletRequestParameterException ex,
			HttpServletRequest request) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Missing required query parameter: " + ex.getParameterName());
		pd.setTitle("Bad Request");
		pd.setInstance(URI.create(request.getRequestURI()));
		pd.setProperty("code", "MISSING_PARAMETER");
		pd.setProperty("traceId", RequestIdFilter.currentRequestId(request));
		return ResponseEntity.badRequest().body(pd);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
		pd.setTitle("Bad Request");
		pd.setInstance(URI.create(request.getRequestURI()));
		pd.setProperty("code", "VALIDATION_ERROR");
		pd.setProperty("traceId", RequestIdFilter.currentRequestId(request));
		String first = ex.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
				.orElse("invalid");
		pd.setProperty("details", Map.of("fieldErrors", first));
		return ResponseEntity.badRequest().body(pd);
	}

	@ExceptionHandler({ BadCredentialsException.class, AuthenticationException.class })
	public ResponseEntity<ProblemDetail> auth(AuthenticationException ex, HttpServletRequest request) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication failed");
		pd.setTitle("Unauthorized");
		pd.setInstance(URI.create(request.getRequestURI()));
		pd.setProperty("code", "UNAUTHORIZED");
		pd.setProperty("traceId", RequestIdFilter.currentRequestId(request));
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pd);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ProblemDetail> denied(AccessDeniedException ex, HttpServletRequest request) {
		boolean unauthenticated = "Unauthenticated".equals(ex.getMessage());
		HttpStatus status = unauthenticated ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
		String detail = unauthenticated ? "Authentication required" : "Forbidden";
		String code = unauthenticated ? "UNAUTHORIZED" : "FORBIDDEN";
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
		pd.setTitle(unauthenticated ? "Unauthorized" : "Forbidden");
		pd.setInstance(URI.create(request.getRequestURI()));
		pd.setProperty("code", code);
		pd.setProperty("traceId", RequestIdFilter.currentRequestId(request));
		return ResponseEntity.status(status).body(pd);
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ProblemDetail> responseStatus(ResponseStatusException ex, HttpServletRequest request) {
		HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
		String detail = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
		pd.setTitle(status.getReasonPhrase());
		pd.setInstance(URI.create(request.getRequestURI()));
		pd.setProperty("code", ex.getReason() != null ? ex.getReason().replace(' ', '_').toUpperCase() : status.name());
		pd.setProperty("traceId", RequestIdFilter.currentRequestId(request));
		return ResponseEntity.status(status).body(pd);
	}

	@ExceptionHandler(RateLimitedException.class)
	public ResponseEntity<ProblemDetail> rateLimited(RateLimitedException ex, HttpServletRequest request) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Too many requests");
		pd.setTitle("Too Many Requests");
		pd.setInstance(URI.create(request.getRequestURI()));
		pd.setProperty("code", "RATE_LIMITED");
		pd.setProperty("traceId", RequestIdFilter.currentRequestId(request));
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(pd);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ProblemDetail> fallback(Exception ex, HttpServletRequest request) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
		pd.setTitle("Internal Server Error");
		pd.setInstance(URI.create(request.getRequestURI()));
		pd.setProperty("code", "INTERNAL_ERROR");
		pd.setProperty("traceId", RequestIdFilter.currentRequestId(request));
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(pd);
	}
}
