package com.wagepayroll.plans;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.wagepayroll.domain.plan.PlanFeatureEntity;
import com.wagepayroll.domain.plan.PlanFeatureRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PlanFeatureCatalogSyncIT {

	@Autowired
	private PlanFeatureRepository planFeatureRepository;

	@Test
	void everyDatabasePlanFeatureCodeIsInEnum() {
		Set<String> dbCodes = planFeatureRepository.findAll().stream().map(PlanFeatureEntity::getCode).collect(Collectors.toSet());
		for (String code : dbCodes) {
			assertTrue(isValidEnum(code), "Remove or map unknown plan_feature.code=" + code + " (DB ↔ PlanFeatureCode enum)");
		}
	}

	private static boolean isValidEnum(String code) {
		try {
			PlanFeatureCode.valueOf(code);
			return true;
		}
		catch (IllegalArgumentException e) {
			return false;
		}
	}

	@Test
	void everyPlanFeatureCodeExistsInDatabase() {
		Set<String> dbCodes = planFeatureRepository.findAll().stream().map(PlanFeatureEntity::getCode).collect(Collectors.toSet());
		Set<String> enumCodes = Arrays.stream(PlanFeatureCode.values()).map(PlanFeatureCode::code).collect(Collectors.toSet());
		Set<String> missing = new HashSet<>(enumCodes);
		missing.removeAll(dbCodes);
		assertTrue(missing.isEmpty(), "PlanFeatureCode entries without DB rows: " + missing);
	}
}
