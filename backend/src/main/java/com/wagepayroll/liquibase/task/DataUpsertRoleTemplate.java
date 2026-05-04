package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Upserts one row in the {@code role_template} table.
 *
 * <pre>{@code
 * <customChange class="com.wagepayroll.liquibase.task.DataUpsertRoleTemplate">
 *     <param name="id"          value="60000000-0000-0000-0000-000000000001"/>
 *     <param name="code"        value="ADMIN"/>
 *     <param name="displayName" value="Admin"/>
 * </customChange>
 * }</pre>
 */
public class DataUpsertRoleTemplate extends CustomDataTaskChange {

	private String id;
	private String code;
	private String displayName;

	@Override
	public void handleUpdate() throws Exception {
		try (PreparedStatement check = connection.prepareStatement(
				"SELECT COUNT(*) FROM role_template WHERE id = ?")) {
			setData(check, 1, id);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = connection.prepareStatement(
							"UPDATE role_template SET code = ?, display_name = ?, updated_at = ? WHERE id = ?")) {
						setData(ps, 1, code);
						setData(ps, 2, displayName);
						setData(ps, 3, ts);
						setData(ps, 4, id);
						ps.executeUpdate();
					}
					return;
				}
			}
		}
		try (PreparedStatement ps = connection.prepareStatement(
				"INSERT INTO role_template (id, code, display_name, created_at, updated_at) VALUES (?,?,?,?,?)")) {
			setData(ps, 1, id);
			setData(ps, 2, code);
			setData(ps, 3, displayName);
			setData(ps, 4, ts);
			setData(ps, 5, ts);
			ps.executeUpdate();
		}
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	public String getCode() { return code; }
	public void setCode(String code) { this.code = code; }

	public String getDisplayName() { return displayName; }
	public void setDisplayName(String displayName) { this.displayName = displayName; }
}
