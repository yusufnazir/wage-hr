package com.wagepayroll.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.cors(Customizer.withDefaults());
		http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
		http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
				.ignoringRequestMatchers("/api/v1/auth/invitations/accept", "/api/v1/billing/webhooks/**"));
		http.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health", "/error").permitAll()
				.requestMatchers("/api/v1/billing/webhooks/**").permitAll()
				.requestMatchers("/api/v1/auth/login", "/api/v1/auth/csrf", "/api/v1/auth/redirect-check",
						"/api/v1/auth/logout", "/api/v1/auth/register", "/api/v1/auth/verify-email",
						"/api/v1/auth/resend-verification", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password")
						.permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/platform/public-surface").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/invitations/accept").permitAll()
				.requestMatchers("/api/**").authenticated()
				.anyRequest().denyAll());
		return http.build();
	}
}
