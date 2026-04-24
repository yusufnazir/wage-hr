package com.wagepayroll.security;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.common.api.RateLimitedException;
import com.wagepayroll.config.AppRateLimitProperties;

import org.springframework.stereotype.Service;

@Service
public class ForgotPasswordRateLimiter {

	private final AppRateLimitProperties props;
	private final Clock clock;
	private final Map<String, List<Instant>> attempts = new ConcurrentHashMap<>();

	public ForgotPasswordRateLimiter(AppRateLimitProperties props) {
		this.props = props;
		this.clock = Clock.systemUTC();
	}

	public void checkAllowed(HttpServletRequest request, String email) {
		String key = clientKey(request) + ":forgot:" + email.trim().toLowerCase();
		synchronized (attempts) {
			List<Instant> list = attempts.computeIfAbsent(key, k -> new ArrayList<>());
			Instant cutoff = clock.instant()
					.minusSeconds((long) props.getForgotPasswordWindowMinutes() * 60L);
			for (Iterator<Instant> it = list.iterator(); it.hasNext();) {
				if (it.next().isBefore(cutoff)) {
					it.remove();
				}
			}
			if (list.size() >= props.getForgotPasswordMaxAttempts()) {
				throw new RateLimitedException();
			}
		}
	}

	public void recordAttempt(HttpServletRequest request, String email) {
		String key = clientKey(request) + ":forgot:" + email.trim().toLowerCase();
		synchronized (attempts) {
			attempts.computeIfAbsent(key, k -> new ArrayList<>()).add(clock.instant());
		}
	}

	private static String clientKey(HttpServletRequest request) {
		String xff = request.getHeader("X-Forwarded-For");
		if (xff != null && !xff.isBlank()) {
			return xff.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}
