package com.wagepayroll.security;

import java.util.Collections;

import com.wagepayroll.domain.user.UserAccountEntity;
import com.wagepayroll.domain.user.UserAccountRepository;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountUserDetailsService implements UserDetailsService {

	private final UserAccountRepository userAccountRepository;

	public AccountUserDetailsService(UserAccountRepository userAccountRepository) {
		this.userAccountRepository = userAccountRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		UserAccountEntity u = userAccountRepository.findByEmailIgnoreCase(email)
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));
		return User.builder()
				.username(u.getId().toString())
				.password(u.getPasswordHash())
				.authorities(Collections.emptyList())
				.build();
	}
}
