package com.wagepayroll.tenant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.wagepayroll.api.dto.PlatformRoleTemplateDto;
import com.wagepayroll.api.dto.PlatformRoleTemplateCreateRequest;
import com.wagepayroll.api.dto.PlatformRoleTemplatePatchRequest;
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

	private static final Pattern TEMPLATE_CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,31}$");

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

	@Transactional(readOnly = true)
	public PlatformRoleTemplateDto getOne(UUID templateId) {
		RoleTemplateEntity e = roleTemplateRepository.findById(templateId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ROLE_TEMPLATE_NOT_FOUND"));
		return toDto(e);
	}

	@Transactional
	public PlatformRoleTemplateDto create(PlatformRoleTemplateCreateRequest body) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		String code = normalizeAndValidateCode(body.code());
		if (roleTemplateRepository.findByCode(code).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "ROLE_TEMPLATE_CODE_IN_USE");
		}
		String displayName = normalizeAndValidateDisplayName(body.displayName());

		var now = java.time.Instant.now();
		RoleTemplateEntity e = new RoleTemplateEntity();
		e.setId(UUID.randomUUID());
		e.setCode(code);
		e.setDisplayName(displayName);
		e.setCreatedAt(now);
		e.setUpdatedAt(now);
		roleTemplateRepository.save(e);

		List<String> codes = body.privilegeCodes() == null ? List.of() : body.privilegeCodes();
		replacePrivileges(e.getId(), codes);
		return toDto(e);
	}

	@Transactional
	public PlatformRoleTemplateDto patch(UUID templateId, PlatformRoleTemplatePatchRequest body) {
		if (body == null || (body.displayName() == null && body.privilegeCodes() == null)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EMPTY_PATCH");
		}
		RoleTemplateEntity e = roleTemplateRepository.findById(templateId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ROLE_TEMPLATE_NOT_FOUND"));
		boolean changed = false;
		if (body.displayName() != null) {
			String dn = normalizeAndValidateDisplayName(body.displayName());
			if (!Objects.equals(dn, e.getDisplayName())) {
				e.setDisplayName(dn);
				changed = true;
			}
		}
		if (body.privilegeCodes() != null) {
			replacePrivileges(e.getId(), body.privilegeCodes());
			changed = true;
		}
		if (changed) {
			e.setUpdatedAt(java.time.Instant.now());
			roleTemplateRepository.save(e);
		}
		return toDto(e);
	}

	private void replacePrivileges(UUID templateId, List<String> privilegeCodes) {
		List<String> normalized = normalizePrivilegeCodes(privilegeCodes);
		roleTemplatePrivilegeRepository.deleteByTemplateId(templateId);
		for (String code : normalized) {
			PrivilegeEntity p = privilegeRepository.findByCode(code).orElseThrow(
					() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_PRIVILEGE_CODE"));
			var row = new com.wagepayroll.domain.roletemplate.RoleTemplatePrivilegeEntity();
			row.setId(UUID.randomUUID());
			row.setRoleTemplateId(templateId);
			row.setPrivilegeId(p.getId());
			roleTemplatePrivilegeRepository.save(row);
		}
	}

	private PlatformRoleTemplateDto toDto(RoleTemplateEntity t) {
		// Reuse list() building logic for privilege codes.
		Map<UUID, PrivilegeEntity> privilegeById = new HashMap<>();
		for (PrivilegeEntity p : privilegeRepository.findAll()) {
			privilegeById.put(p.getId(), p);
		}
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
		return new PlatformRoleTemplateDto(t.getId(), t.getCode(), t.getDisplayName(), codeList);
	}

	private static String normalizeAndValidateCode(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ROLE_TEMPLATE_CODE_REQUIRED");
		}
		String code = raw.trim().toUpperCase();
		if (!TEMPLATE_CODE_PATTERN.matcher(code).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ROLE_TEMPLATE_CODE_INVALID");
		}
		return code;
	}

	private static String normalizeAndValidateDisplayName(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ROLE_TEMPLATE_DISPLAY_NAME_REQUIRED");
		}
		String dn = raw.trim();
		if (dn.length() < 1 || dn.length() > 128) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ROLE_TEMPLATE_DISPLAY_NAME_INVALID");
		}
		return dn;
	}

	private static List<String> normalizePrivilegeCodes(List<String> raw) {
		if (raw == null) {
			return List.of();
		}
		Set<String> out = new LinkedHashSet<>();
		for (String s : raw) {
			if (s == null || s.isBlank()) {
				continue;
			}
			out.add(s.trim().toUpperCase());
		}
		List<String> list = new ArrayList<>(out);
		list.sort(String::compareToIgnoreCase);
		return list;
	}
}

