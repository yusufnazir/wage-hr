package com.wagepayroll.tenant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.wagepayroll.api.dto.TenantSummaryDto;
import com.wagepayroll.domain.membership.MembershipEntity;
import com.wagepayroll.domain.membership.MembershipRepository;
import com.wagepayroll.domain.role.RoleRepository;
import com.wagepayroll.domain.role.UserRoleRepository;
import com.wagepayroll.domain.tenant.TenantEntity;
import com.wagepayroll.domain.tenant.TenantRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantDirectoryService {

	private final MembershipRepository membershipRepository;
	private final TenantRepository tenantRepository;
	private final UserRoleRepository userRoleRepository;
	private final RoleRepository roleRepository;

	public TenantDirectoryService(MembershipRepository membershipRepository, TenantRepository tenantRepository,
			UserRoleRepository userRoleRepository, RoleRepository roleRepository) {
		this.membershipRepository = membershipRepository;
		this.tenantRepository = tenantRepository;
		this.userRoleRepository = userRoleRepository;
		this.roleRepository = roleRepository;
	}

	@Transactional(readOnly = true)
	public List<TenantSummaryDto> listTenantSummaries(UUID userId) {
		List<MembershipEntity> memberships = membershipRepository.findByUserIdOrderByTenantIdAsc(userId);
		List<TenantSummaryDto> rows = new ArrayList<>();
		for (MembershipEntity m : memberships) {
			TenantEntity t = tenantRepository.findById(m.getTenantId()).orElseThrow();
			List<UUID> roleIds = userRoleRepository.findRoleIdsByUserAndTenant(userId, m.getTenantId());
			List<String> roles = new ArrayList<>();
			for (UUID rid : roleIds) {
				roleRepository.findById(rid).ifPresent(role -> roles.add(role.getName()));
			}
			Collections.sort(roles);
			rows.add(new TenantSummaryDto(t.getId(), t.getHandle(), t.getName(), List.copyOf(roles)));
		}
		rows.sort(Comparator.comparing(TenantSummaryDto::handle));
		return rows;
	}
}
