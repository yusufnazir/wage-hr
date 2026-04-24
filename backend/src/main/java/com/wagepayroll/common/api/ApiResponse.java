package com.wagepayroll.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, Meta meta) {

	public static <T> ApiResponse<T> of(T data, String requestId) {
		return new ApiResponse<>(data, new Meta(requestId));
	}

	public record Meta(String requestId) {
	}
}
