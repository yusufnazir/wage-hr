package com.wagepayroll.tenant;

import java.time.Instant;
import java.util.UUID;

import com.wagepayroll.domain.membership.MembershipRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipActivityTouchService {

	private static final int DEBOUNCE_SECONDS = 60;

	private final MembershipRepository membershipRepository;

	public MembershipActivityTouchService(MembershipRepository membershipRepository) {
		this.membershipRepository = membershipRepository;
	}

	@Transactional
	public void touchLastSeen(UUID tenantId, UUID userId) {
		Instant now = Instant.now();
		membershipRepository.touchLastActiveIfStale(tenantId, userId, now, now.minusSeconds(DEBOUNCE_SECONDS));
	}
}
