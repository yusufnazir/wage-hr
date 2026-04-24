package com.wagepayroll.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.wagepayroll.domain.privilege.PrivilegeEntity;
import com.wagepayroll.domain.privilege.PrivilegeRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PrivilegeCatalogSyncIT {

	@Autowired
	private PrivilegeRepository privilegeRepository;

	@Test
	void everyDatabasePrivilegeCodeIsCataloguedInDefinedPrivilege() {
		Set<String> dbCodes = privilegeRepository.findAll().stream().map(PrivilegeEntity::getCode).collect(Collectors.toSet());
		for (String code : dbCodes) {
			assertTrue(DefinedPrivilege.forCode(code).isPresent(),
					"Add DefinedPrivilege for privilege.code=" + code + " (Liquibase ↔ catalog sync)");
		}
	}

	@Test
	void everyDefinedPrivilegeExistsInDatabase() {
		Set<String> dbCodes = privilegeRepository.findAll().stream().map(PrivilegeEntity::getCode).collect(Collectors.toSet());
		Set<String> enumCodes = Arrays.stream(DefinedPrivilege.values()).map(DefinedPrivilege::code).collect(Collectors.toSet());
		Set<String> missing = new HashSet<>(enumCodes);
		missing.removeAll(dbCodes);
		assertTrue(missing.isEmpty(), "DefinedPrivilege entries without DB rows: " + missing);
	}
}
