package com.wagepayroll.api;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import com.wagepayroll.common.api.ApiResponse;
import com.wagepayroll.common.api.RequestIdFilter;
import com.wagepayroll.security.RequiresPrivilege;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {

	@GetMapping("/user-view")
	@RequiresPrivilege("USER_VIEW")
	public ApiResponse<Map<String, String>> userView(HttpServletRequest request) {
		String rid = RequestIdFilter.currentRequestId(request);
		return ApiResponse.of(Map.of("message", "USER_VIEW granted"), rid);
	}
}
