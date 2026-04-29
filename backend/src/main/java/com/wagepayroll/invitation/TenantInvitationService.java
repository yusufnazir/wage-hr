package com.wagepayroll.invitation;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wagepayroll.api.dto.AcceptInvitationRequest;
import com.wagepayroll.api.dto.TenantInvitationListItemDto;
import com.wagepayroll.auth.Sha256Hex;
import com.wagepayroll.common.email.EmailAddress;
import com.wagepayroll.config.InvitationTokenExposure;
import com.wagepayroll.domain.invitation.TenantInvitationEntity;
import com.wagepayroll.domain.invitation.TenantInvitationRepository;
import com.wagepayroll.domain.membership.MembershipEntity;
import com.wagepayroll.domain.membership.MembershipRepository;
import com.wagepayroll.domain.role.RoleRepository;
import com.wagepayroll.domain.role.UserRoleEntity;
import com.wagepayroll.domain.role.UserRoleRepository;
import com.wagepayroll.domain.user.UserAccountEntity;
import com.wagepayroll.domain.user.UserAccountRepository;
import com.wagepayroll.mail.InvitationEmailRequest;
import com.wagepayroll.mail.MailSendPort;
import com.wagepayroll.notification.NotificationEmitCommand;
import com.wagepayroll.notification.NotificationService;
import com.wagepayroll.notification.NotificationType;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantInvitationService {

	public static final String STATUS_PENDING = "PENDING";
	public static final String STATUS_ACCEPTED = "ACCEPTED";

	private static final int MIN_PASSWORD = 8;

	private final TenantInvitationRepository invitations;
	private final UserAccountRepository users;
	private final MembershipRepository membershipRepository;
	private final UserRoleRepository userRoleRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final MailSendPort mailSendPort;
	private final NotificationService notificationService;
	private final InvitationTokenExposure invitationTokenExposure;

	public TenantInvitationService(TenantInvitationRepository invitations, UserAccountRepository users,
			MembershipRepository membershipRepository, UserRoleRepository userRoleRepository, RoleRepository roleRepository,
			PasswordEncoder passwordEncoder, MailSendPort mailSendPort, NotificationService notificationService,
			InvitationTokenExposure invitationTokenExposure) {
		this.invitations = invitations;
		this.users = users;
		this.membershipRepository = membershipRepository;
		this.userRoleRepository = userRoleRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
		this.mailSendPort = mailSendPort;
		this.notificationService = notificationService;
		this.invitationTokenExposure = invitationTokenExposure;
	}

	@Transactional
	public CreateInvitationResult create(UUID tenantId, String tenantHandle, UUID inviterUserId, String rawEmail,
			UUID roleId) {
		final String email;
		try {
			email = EmailAddress.normalizeAndValidate(rawEmail);
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL");
		}
		roleRepository.findByTenantIdAndId(tenantId, roleId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_ROLE"));

		Optional<TenantInvitationEntity> existingPending = invitations.findByTenantIdAndInvitedEmailIgnoreCaseAndStatus(
				tenantId, email, STATUS_PENDING);
		if (existingPending.isPresent()) {
			TenantInvitationEntity e = existingPending.get();
			return new CreateInvitationResult(e.getId(), e.getExpiresAt(), null, true);
		}

		users.findByEmailIgnoreCase(email).ifPresent(u -> {
			if (membershipRepository.findByTenantIdAndUserId(tenantId, u.getId()).isPresent()) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "ALREADY_MEMBER");
			}
		});

		byte[] raw = new byte[24];
		new SecureRandom().nextBytes(raw);
		String plainToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
		String hash = Sha256Hex.ofUtf8String(plainToken);
		Instant now = Instant.now();
		TenantInvitationEntity e = new TenantInvitationEntity();
		e.setId(UUID.randomUUID());
		e.setTenantId(tenantId);
		e.setInvitedEmail(email);
		e.setInviterUserId(inviterUserId);
		e.setRoleId(roleId);
		e.setTokenHash(hash);
		e.setStatus(STATUS_PENDING);
		e.setPendingDedupKey(pendingDedupKey(tenantId, email));
		e.setExpiresAt(now.plus(7, ChronoUnit.DAYS));
		e.setCreatedAt(now);
		e.setUpdatedAt(now);
		try {
			invitations.save(e);
		}
		catch (DataIntegrityViolationException ex) {
			return invitations.findByTenantIdAndInvitedEmailIgnoreCaseAndStatus(tenantId, email, STATUS_PENDING)
					.map(p -> new CreateInvitationResult(p.getId(), p.getExpiresAt(), null, true))
					.orElseThrow(() -> ex);
		}
		mailSendPort.sendInvitationEmail(new InvitationEmailRequest(tenantId, tenantHandle, email, plainToken));
		String devToken = invitationTokenExposure.effectiveExposePlainToken() ? plainToken : null;
		return new CreateInvitationResult(e.getId(), e.getExpiresAt(), devToken, false);
	}

	static String pendingDedupKey(UUID tenantId, String normalizedEmail) {
		return tenantId + ":" + normalizedEmail;
	}

	@Transactional
	public void accept(AcceptInvitationRequest body) {
		if (body == null || body.token() == null || body.token().isBlank() || body.password() == null
				|| body.password().length() < MIN_PASSWORD) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_INVITE_REQUEST");
		}
		String hash = Sha256Hex.ofUtf8String(body.token().trim());
		TenantInvitationEntity inv = invitations.findByTokenHash(hash)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "INVITE_NOT_FOUND"));
		if (!STATUS_PENDING.equals(inv.getStatus())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "INVITE_NOT_PENDING");
		}
		if (Instant.now().isAfter(inv.getExpiresAt())) {
			throw new ResponseStatusException(HttpStatus.GONE, "INVITE_EXPIRED");
		}
		UUID tenantId = inv.getTenantId();
		UUID invitationId = inv.getId();
		String email = inv.getInvitedEmail();
		UserAccountEntity user = users.findByEmailIgnoreCase(email).orElse(null);
		if (user == null) {
			user = new UserAccountEntity();
			user.setId(UUID.randomUUID());
			user.setEmail(email);
			user.setPasswordHash(passwordEncoder.encode(body.password()));
			user.setPlatformSuperadmin(false);
			user.setPreferredLocale("en");
			Instant now = Instant.now();
			user.setCreatedAt(now);
			user.setUpdatedAt(now);
			users.save(user);
		}
		else {
			if (!passwordEncoder.matches(body.password(), user.getPasswordHash())) {
				throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_PASSWORD");
			}
		}
		UUID userId = user.getId();
		if (membershipRepository.findByTenantIdAndUserId(tenantId, userId).isEmpty()) {
			Instant now = Instant.now();
			MembershipEntity m = new MembershipEntity();
			m.setId(UUID.randomUUID());
			m.setTenantId(tenantId);
			m.setUserId(userId);
			m.setCreatedAt(now);
			m.setUpdatedAt(now);
			m.setStatus("ACTIVE");
			membershipRepository.save(m);
		}
		if (userRoleRepository.findRoleIdsByUserAndTenant(userId, tenantId).stream().noneMatch(r -> r.equals(inv.getRoleId()))) {
			Instant now = Instant.now();
			UserRoleEntity ur = new UserRoleEntity();
			ur.setId(UUID.randomUUID());
			ur.setTenantId(tenantId);
			ur.setUserId(userId);
			ur.setRoleId(inv.getRoleId());
			ur.setCreatedAt(now);
			ur.setUpdatedAt(now);
			userRoleRepository.save(ur);
		}
		Instant now = Instant.now();
		inv.setStatus(STATUS_ACCEPTED);
		inv.setAcceptedUserId(userId);
		inv.setPendingDedupKey(null);
		inv.setUpdatedAt(now);
		invitations.save(inv);
		notificationService.emit(new NotificationEmitCommand(tenantId, userId, NotificationType.TENANT_JOINED,
				NotificationService.TEMPLATE_VERSION_M2_1, invitationId, false));
	}

	public List<TenantInvitationListItemDto> listPending(UUID tenantId) {
		return invitations.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, STATUS_PENDING).stream()
				.map(e -> new TenantInvitationListItemDto(e.getId(), e.getInvitedEmail(), e.getRoleId(), e.getStatus(),
						e.getExpiresAt(), e.getCreatedAt()))
				.toList();
	}

	public record CreateInvitationResult(UUID invitationId, Instant expiresAt, String devPlainToken, boolean idempotentReplay) {
	}
}
