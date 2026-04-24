package com.wagepayroll.security;

import java.util.UUID;

import com.wagepayroll.domain.user.UserAccountRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class PlatformOperatorService {

	private final UserAccountRepository userAccountRepository;

	public PlatformOperatorService(UserAccountRepository userAccountRepository) {
		this.userAccountRepository = userAccountRepository;
	}

	public void requirePlatformSuperadmin(UUID userId) {
		if (!userAccountRepository.findById(userId).orElseThrow().isPlatformSuperadmin()) {
			throw new AccessDeniedException("Platform operator only");
		}
	}
}
