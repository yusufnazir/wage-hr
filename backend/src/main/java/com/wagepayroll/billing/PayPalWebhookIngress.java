package com.wagepayroll.billing;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.util.StringUtils;

/**
 * PayPal webhook HTTP envelope: raw JSON plus transmission headers used for idempotency and (when enabled)
 * {@code verify-webhook-signature}.
 */
public record PayPalWebhookIngress(String rawBody, String transmissionId, String transmissionTime, String transmissionSig,
		String certUrl, String authAlgo) {

	public static PayPalWebhookIngress from(HttpServletRequest request, String rawBody) {
		return new PayPalWebhookIngress(rawBody, header(request, "PayPal-Transmission-Id"),
				header(request, "PayPal-Transmission-Time"), header(request, "PayPal-Transmission-Sig"),
				header(request, "PayPal-Cert-Url"), header(request, "PayPal-Auth-Algo"));
	}

	private static String header(HttpServletRequest request, String name) {
		String v = request.getHeader(name);
		return StringUtils.hasText(v) ? v.trim() : null;
	}
}
