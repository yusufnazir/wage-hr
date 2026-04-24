package com.wagepayroll.api;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class CsrfController {

	@GetMapping("/csrf")
	public ApiResponse<Map<String, String>> csrf(CsrfToken token, HttpServletRequest request) {
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(
				Map.of("token", token.getToken(), "headerName", token.getHeaderName(), "parameterName", token.getParameterName()),
				rid);
	}
}
