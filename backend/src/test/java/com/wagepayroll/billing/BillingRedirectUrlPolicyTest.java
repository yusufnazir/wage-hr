package com.wagepayroll.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class BillingRedirectUrlPolicyTest {

	@Test
	void httpsAlwaysAllowed() {
		BillingRedirectUrlPolicy p = policy(false);
		p.validateTenantBillingRedirectUrl("https://evil.example/pay");
	}

	@Test
	void httpRejectedWhenInsecureNotAllowed() {
		BillingRedirectUrlPolicy p = policy(false);
		assertThatThrownBy(() -> p.validateTenantBillingRedirectUrl("http://localhost:3000/cb"))
				.isInstanceOf(ResponseStatusException.class).extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void httpLocalhostAllowedWhenInsecureFlagOn() {
		BillingRedirectUrlPolicy p = policy(true);
		p.validateTenantBillingRedirectUrl("http://localhost:3007/app?billing=stripe_success");
	}

	@Test
	void httpLvhMeSubdomainAllowedWhenInsecureFlagOn() {
		BillingRedirectUrlPolicy p = policy(true);
		p.validateTenantBillingRedirectUrl("http://demo.lvh.me:3007/app?billing=stripe_cancel");
		p.validateTenantBillingRedirectUrl("http://lvh.me:3007/");
	}

	@Test
	void httpNonDevHostRejectedEvenWhenInsecureFlagOn() {
		BillingRedirectUrlPolicy p = policy(true);
		assertThatThrownBy(() -> p.validateTenantBillingRedirectUrl("http://attacker.example/cb"))
				.isInstanceOf(ResponseStatusException.class).extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void isInsecureHttpDevHostMatchesExpectedHosts() {
		assertThat(BillingRedirectUrlPolicy.isInsecureHttpDevHost("localhost")).isTrue();
		assertThat(BillingRedirectUrlPolicy.isInsecureHttpDevHost("127.0.0.1")).isTrue();
		assertThat(BillingRedirectUrlPolicy.isInsecureHttpDevHost("demo.lvh.me")).isTrue();
		assertThat(BillingRedirectUrlPolicy.isInsecureHttpDevHost("lvh.me")).isTrue();
		assertThat(BillingRedirectUrlPolicy.isInsecureHttpDevHost("evil.lvh.me.not")).isFalse();
		assertThat(BillingRedirectUrlPolicy.isInsecureHttpDevHost("example.com")).isFalse();
	}

	private static BillingRedirectUrlPolicy policy(boolean allowInsecure) {
		BillingRedirectUrlPolicy p = new BillingRedirectUrlPolicy();
		ReflectionTestUtils.setField(p, "allowInsecureRedirectUrls", allowInsecure);
		return p;
	}
}
