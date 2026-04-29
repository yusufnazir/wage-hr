package com.wagepayroll.tenant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.wagepayroll.api.dto.PlatformRoleTemplateDto;
import com.wagepayroll.domain.privilege.PrivilegeEntity;
import com.wagepayroll.domain.privilege.PrivilegeRepository;
import com.wagepayroll.domain.roletemplate.RoleTemplateEntity;
import com.wagepayroll.domain.roletemplate.RoleTemplatePrivilegeRepository;
import com.wagepayroll.domain.roletemplate.RoleTemplateRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformRoleTemplateService {

	private final RoleTemplateRepository roleTemplateRepository;
	private final RoleTemplatePrivilegeRepository roleTemplatePrivilegeRepository;
	private final PrivilegeRepository privilegeRepository;

	public PlatformRoleTemplateService(RoleTemplateRepository roleTemplateRepository,
			RoleTemplatePrivilegeRepository roleTemplatePrivilegeRepository, PrivilegeRepository privilegeRepository) {
		this.roleTemplateRepository = roleTemplateRepository;
		this.roleTemplatePrivilegeRepository = roleTemplatePrivilegeRepository;
		this.privilegeRepository = privilegeRepository;
	}

	@Transactional(readOnly = true)
	public List<PlatformRoleTemplateDto> list() {
		List<RoleTemplateEntity> templates = roleTemplateRepository.findAll();
		templates.sort((a, b) -> a.getCode().compareToIgnoreCase(b.getCode()));

		Map<UUID, PrivilegeEntity> privilegeById = new HashMap<>();
		for (PrivilegeEntity p : privilegeRepository.findAll()) {
			privilegeById.put(p.getId(), p);
		}

		List<PlatformRoleTemplateDto> out = new ArrayList<>();
		for (RoleTemplateEntity t : templates) {
			List<UUID> privilegeIds = roleTemplatePrivilegeRepository.findPrivilegeIdsByTemplateId(t.getId());
			Set<String> codes = new LinkedHashSet<>();
			for (UUID pid : privilegeIds) {
				PrivilegeEntity p = privilegeById.get(pid);
				if (p != null) {
					codes.add(p.getCode());
				}
			}
			List<String> codeList = new ArrayList<>(codes);
			codeList.sort(String::compareToIgnoreCase);
			out.add(new PlatformRoleTemplateDto(t.getId(), t.getCode(), t.getDisplayName(), codeList));
		}
		return out;
	}

	@Transactional(readOnly = true)
	public RoleTemplateEntity requireTemplateByCode(String code) {
		return roleTemplateRepository.findByCode(code)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ROLE_TEMPLATE_MISSING"));
	}
}

