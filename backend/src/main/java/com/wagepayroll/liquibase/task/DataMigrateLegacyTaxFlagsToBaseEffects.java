package com.wagepayroll.liquibase.task;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

/**
 * Backfills base-effect rows from legacy taxability flags and {@code net_effect}.
 */
public class DataMigrateLegacyTaxFlagsToBaseEffects implements CustomTaskChange {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final Map<String, UUID> BASE_IDS = Map.ofEntries(
			Map.entry("GROSS", UUID.fromString("52001000-0000-0000-0000-000000000001")),
			Map.entry("NET", UUID.fromString("52001000-0000-0000-0000-000000000002")),
			Map.entry("LOONBELASTING", UUID.fromString("52001000-0000-0000-0000-000000000003")),
			Map.entry("AOV", UUID.fromString("52001000-0000-0000-0000-000000000004")),
			Map.entry("AWW", UUID.fromString("52001000-0000-0000-0000-000000000005")),
			Map.entry("SZF", UUID.fromString("52001000-0000-0000-0000-000000000006")),
			Map.entry("PENSION", UUID.fromString("52001000-0000-0000-0000-000000000007")),
			Map.entry("VACATION", UUID.fromString("52001000-0000-0000-0000-000000000008")));

	@Override
	public void execute(Database database) throws CustomChangeException {
		JdbcConnection jdbc = (JdbcConnection) database.getConnection();
		Connection conn = jdbc.getUnderlyingConnection();
		Timestamp now = Timestamp.from(Instant.now());
		try {
			migratePlatformWageComponents(conn, now);
			migrateTemplates(conn, now);
			migrateTenantComponentsFromTemplates(conn, now);
			migrateTenantComponentsFromColumns(conn, now);
			conn.commit();
		}
		catch (Exception ex) {
			throw new CustomChangeException("Failed to migrate wage component base effects", ex);
		}
	}

	private void migratePlatformWageComponents(Connection conn, Timestamp now) throws Exception {
		String sql = """
				SELECT id, component_type, phase, net_effect, taxable_wage_tax, taxable_social_security,
				       taxable_pension, taxable_vacation_reserve
				FROM platform_wage_component
				""";
		try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				UUID componentId = UUID.fromString(rs.getString("id"));
				List<EffectRow> effects = deriveEffects(rs.getString("component_type"), rs.getString("phase"),
						rs.getString("net_effect"), rs.getBoolean("taxable_wage_tax"),
						rs.getBoolean("taxable_social_security"), rs.getBoolean("taxable_pension"),
						rs.getBoolean("taxable_vacation_reserve"));
				insertPlatformComponentEffects(conn, componentId, effects, now);
			}
		}
	}

	private void migrateTemplates(Connection conn, Timestamp now) throws Exception {
		String sql = "SELECT id, definition_defaults_json FROM platform_wage_component_template";
		try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				UUID templateId = UUID.fromString(rs.getString("id"));
				JsonNode json = MAPPER.readTree(rs.getString("definition_defaults_json"));
				String componentType = textOr(json, "componentType", "EARNING");
				String phase = textOr(json, "phase", "GROSS");
				String netEffect = textOr(json, "netEffect", "ADD_TO_NET");
				boolean taxableWageTax = boolOr(json, "taxableWageTax", false);
				boolean taxableSocial = boolOr(json, "taxableSocialSecurity", false);
				boolean taxablePension = boolOr(json, "taxablePension", false);
				boolean taxableVacation = boolOr(json, "taxableVacationReserve", false);
				List<EffectRow> effects = deriveEffects(componentType, phase, netEffect, taxableWageTax, taxableSocial,
						taxablePension, taxableVacation);
				insertTemplateEffects(conn, templateId, effects, now);
			}
		}
	}

	private void migrateTenantComponentsFromTemplates(Connection conn, Timestamp now) throws Exception {
		String sql = """
				SELECT twc.id, twc.tenant_id, twc.platform_template_id
				FROM tenant_wage_component twc
				WHERE twc.platform_template_id IS NOT NULL
				""";
		try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				UUID tenantComponentId = UUID.fromString(rs.getString("id"));
				UUID tenantId = UUID.fromString(rs.getString("tenant_id"));
				UUID templateId = UUID.fromString(rs.getString("platform_template_id"));
				copyTemplateEffectsToTenant(conn, tenantId, templateId, tenantComponentId, now);
			}
		}
	}

	private void migrateTenantComponentsFromColumns(Connection conn, Timestamp now) throws Exception {
		String sql = """
				SELECT id, tenant_id, component_type, phase, net_effect, taxable_wage_tax, taxable_social_security,
				       taxable_pension, taxable_vacation_reserve
				FROM tenant_wage_component
				""";
		try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				UUID componentId = UUID.fromString(rs.getString("id"));
				UUID tenantId = UUID.fromString(rs.getString("tenant_id"));
				if (tenantHasAnyEffect(conn, tenantId, componentId)) {
					continue;
				}
				List<EffectRow> effects = deriveEffects(rs.getString("component_type"), rs.getString("phase"),
						rs.getString("net_effect"), rs.getBoolean("taxable_wage_tax"),
						rs.getBoolean("taxable_social_security"), rs.getBoolean("taxable_pension"),
						rs.getBoolean("taxable_vacation_reserve"));
				insertTenantEffects(conn, tenantId, componentId, effects, now);
			}
		}
	}

	private static boolean tenantHasAnyEffect(Connection conn, UUID tenantId, UUID componentId) throws Exception {
		String sql = """
				SELECT 1 FROM tenant_wage_component_base_effect
				WHERE tenant_id = ? AND tenant_wage_component_id = ? LIMIT 1
				""";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, tenantId.toString());
			ps.setString(2, componentId.toString());
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private static void copyTemplateEffectsToTenant(Connection conn, UUID tenantId, UUID templateId, UUID tenantComponentId,
			Timestamp now) throws Exception {
		String sql = """
				SELECT platform_payroll_base_id, effect_direction, effect_calculation_type, effect_value, priority,
				       effective_from, effective_until, active
				FROM platform_wage_component_template_base_effect
				WHERE platform_wage_component_template_id = ? AND active = TRUE
				""";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, templateId.toString());
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					UUID baseId = UUID.fromString(rs.getString("platform_payroll_base_id"));
					if (tenantEffectExists(conn, tenantId, tenantComponentId, baseId)) {
						continue;
					}
					insertTenantEffect(conn, tenantId, tenantComponentId, baseId, rs.getString("effect_direction"),
							rs.getString("effect_calculation_type"), rs.getBigDecimal("effect_value"), rs.getInt("priority"),
							rs.getDate("effective_from"), rs.getDate("effective_until"), rs.getBoolean("active"), now);
				}
			}
		}
	}

	private static List<EffectRow> deriveEffects(String componentType, String phase, String netEffect,
			boolean taxableWageTax, boolean taxableSocial, boolean taxablePension, boolean taxableVacation) {
		List<EffectRow> rows = new ArrayList<>();
		rows.add(taxableWageTax ? EffectRow.increase("LOONBELASTING") : EffectRow.ignore("LOONBELASTING"));
		if (taxableSocial) {
			rows.add(EffectRow.increase("AOV"));
			rows.add(EffectRow.increase("AWW"));
			rows.add(EffectRow.increase("SZF"));
		}
		else {
			rows.add(EffectRow.ignore("AOV"));
			rows.add(EffectRow.ignore("AWW"));
			rows.add(EffectRow.ignore("SZF"));
		}
		rows.add(taxablePension ? EffectRow.increase("PENSION") : EffectRow.ignore("PENSION"));
		rows.add(taxableVacation ? EffectRow.increase("VACATION") : EffectRow.ignore("VACATION"));
		rows.add(switch (netEffect != null ? netEffect : "ADD_TO_NET") {
			case "SUBTRACT_FROM_NET" -> EffectRow.decrease("NET");
			case "NO_EFFECT" -> EffectRow.ignore("NET");
			default -> EffectRow.increase("NET");
		});
		if ("EARNING".equalsIgnoreCase(componentType) && "GROSS".equalsIgnoreCase(phase)) {
			rows.add(EffectRow.increase("GROSS"));
		}
		return rows;
	}

	private static void insertPlatformComponentEffects(Connection conn, UUID componentId, List<EffectRow> effects,
			Timestamp now) throws Exception {
		for (EffectRow row : effects) {
			UUID baseId = BASE_IDS.get(row.baseCode);
			if (baseId == null || platformComponentEffectExists(conn, componentId, baseId)) {
				continue;
			}
			String sql = """
					INSERT INTO platform_wage_component_base_effect
					(id, platform_wage_component_id, platform_payroll_base_id, effect_direction, effect_calculation_type,
					 effect_value, priority, active, created_at, updated_at)
					VALUES (?, ?, ?, ?, 'FULL', ?, 0, TRUE, ?, ?)
					""";
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, UUID.randomUUID().toString());
				ps.setString(2, componentId.toString());
				ps.setString(3, baseId.toString());
				ps.setString(4, row.direction);
				ps.setBigDecimal(5, row.value);
				ps.setTimestamp(6, now);
				ps.setTimestamp(7, now);
				ps.executeUpdate();
			}
		}
	}

	private static void insertTemplateEffects(Connection conn, UUID templateId, List<EffectRow> effects, Timestamp now)
			throws Exception {
		for (EffectRow row : effects) {
			UUID baseId = BASE_IDS.get(row.baseCode);
			if (baseId == null || templateEffectExists(conn, templateId, baseId)) {
				continue;
			}
			String sql = """
					INSERT INTO platform_wage_component_template_base_effect
					(id, platform_wage_component_template_id, platform_payroll_base_id, effect_direction,
					 effect_calculation_type, effect_value, priority, active, created_at, updated_at)
					VALUES (?, ?, ?, ?, 'FULL', ?, 0, TRUE, ?, ?)
					""";
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, UUID.randomUUID().toString());
				ps.setString(2, templateId.toString());
				ps.setString(3, baseId.toString());
				ps.setString(4, row.direction);
				ps.setBigDecimal(5, row.value);
				ps.setTimestamp(6, now);
				ps.setTimestamp(7, now);
				ps.executeUpdate();
			}
		}
	}

	private static void insertTenantEffects(Connection conn, UUID tenantId, UUID componentId, List<EffectRow> effects,
			Timestamp now) throws Exception {
		for (EffectRow row : effects) {
			UUID baseId = BASE_IDS.get(row.baseCode);
			if (baseId == null || tenantEffectExists(conn, tenantId, componentId, baseId)) {
				continue;
			}
			insertTenantEffect(conn, tenantId, componentId, baseId, row.direction, "FULL", row.value, 0, null, null, true,
					now);
		}
	}

	private static void insertTenantEffect(Connection conn, UUID tenantId, UUID componentId, UUID baseId,
			String direction, String calcType, BigDecimal value, int priority, java.sql.Date effectiveFrom,
			java.sql.Date effectiveUntil, boolean active, Timestamp now) throws Exception {
		String sql = """
				INSERT INTO tenant_wage_component_base_effect
				(id, tenant_id, tenant_wage_component_id, platform_payroll_base_id, effect_direction,
				 effect_calculation_type, effect_value, priority, effective_from, effective_until, active, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, UUID.randomUUID().toString());
			ps.setString(2, tenantId.toString());
			ps.setString(3, componentId.toString());
			ps.setString(4, baseId.toString());
			ps.setString(5, direction);
			ps.setString(6, calcType);
			ps.setBigDecimal(7, value);
			ps.setInt(8, priority);
			ps.setDate(9, effectiveFrom);
			ps.setDate(10, effectiveUntil);
			ps.setBoolean(11, active);
			ps.setTimestamp(12, now);
			ps.setTimestamp(13, now);
			ps.executeUpdate();
		}
	}

	private static boolean platformComponentEffectExists(Connection conn, UUID componentId, UUID baseId)
			throws Exception {
		return effectExists(conn,
				"SELECT 1 FROM platform_wage_component_base_effect WHERE platform_wage_component_id = ? AND platform_payroll_base_id = ? LIMIT 1",
				componentId, baseId);
	}

	private static boolean templateEffectExists(Connection conn, UUID templateId, UUID baseId) throws Exception {
		return effectExists(conn,
				"SELECT 1 FROM platform_wage_component_template_base_effect WHERE platform_wage_component_template_id = ? AND platform_payroll_base_id = ? LIMIT 1",
				templateId, baseId);
	}

	private static boolean tenantEffectExists(Connection conn, UUID tenantId, UUID componentId, UUID baseId)
			throws Exception {
		String sql = """
				SELECT 1 FROM tenant_wage_component_base_effect
				WHERE tenant_id = ? AND tenant_wage_component_id = ? AND platform_payroll_base_id = ? LIMIT 1
				""";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, tenantId.toString());
			ps.setString(2, componentId.toString());
			ps.setString(3, baseId.toString());
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private static boolean effectExists(Connection conn, String sql, UUID parentId, UUID baseId) throws Exception {
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, parentId.toString());
			ps.setString(2, baseId.toString());
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private static String textOr(JsonNode json, String field, String defaultValue) {
		JsonNode n = json.get(field);
		return n != null && !n.isNull() ? n.asText() : defaultValue;
	}

	private static boolean boolOr(JsonNode json, String field, boolean defaultValue) {
		JsonNode n = json.get(field);
		return n != null && !n.isNull() ? n.asBoolean() : defaultValue;
	}

	private record EffectRow(String baseCode, String direction, BigDecimal value) {
		static EffectRow increase(String base) {
			return new EffectRow(base, "INCREASE", new BigDecimal("100"));
		}

		static EffectRow decrease(String base) {
			return new EffectRow(base, "DECREASE", new BigDecimal("100"));
		}

		static EffectRow ignore(String base) {
			return new EffectRow(base, "IGNORE", BigDecimal.ZERO);
		}
	}

	@Override
	public String getConfirmationMessage() {
		return "Migrated legacy tax flags to wage component base effects";
	}

	@Override
	public void setUp() throws SetupException {
		// no-op
	}

	@Override
	public void setFileOpener(ResourceAccessor resourceAccessor) {
		// no-op
	}

	@Override
	public ValidationErrors validate(Database database) {
		return new ValidationErrors();
	}
}
