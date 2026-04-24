package com.wagepayroll.config;

import java.util.Arrays;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties({ AppHostProperties.class, AppCorsProperties.class, AppSecurityHeadersProperties.class,
		AppRateLimitProperties.class, ForwardingProperties.class, AppPublicProperties.class })
public class WebConfiguration implements WebMvcConfigurer {

	@Bean
	CorsConfigurationSource corsConfigurationSource(AppCorsProperties cors) {
		CorsConfiguration c = new CorsConfiguration();
		c.setAllowCredentials(true);
		for (String p : Arrays.stream(cors.getAllowedOriginPatterns().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()) {
			c.addAllowedOriginPattern(p);
		}
		c.addAllowedHeader(CorsConfiguration.ALL);
		c.addAllowedMethod(CorsConfiguration.ALL);
		c.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", c);
		return source;
	}
}
