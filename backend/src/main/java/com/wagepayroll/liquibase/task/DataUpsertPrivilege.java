package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Upserts one row in the {@code privilege} table.
 *
 * <pre>{@code
 * <customChange class="com.wagepayroll.liquibase.task.DataUpsertPrivilege">
 *     <param name="id"          value="20000000-0000-0000-0000-000000000001"/>
 *     <param name="code"        value="USER_VIEW"/>
 *     <param name="description" value="View users"/>
 * </customChange>
 * }</pre>
 */
public class DataUpsertPrivilege extends CustomDataTaskChange {

	private String id;
	private String code;
	private String description;

	@Override
	public void handleUpdate() throws Exception {
		try (PreparedStatement check = connection.prepareStatement(
				"SELECT COUNT(*) FROM privilege WHERE id = ?")) {
			setData(check, 1, id);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = connection.prepareStatement(
							"UPDATE privilege SET code = ?, description = ?, updated_at = ? WHERE id = ?")) {
						setData(ps, 1, code);
						setData(ps, 2, description);
						setData(ps, 3, ts);
						setData(ps, 4, id);
						ps.executeUpdate();
					}
					return;
				}
			}
		}
		try (PreparedStatement ps = connection.prepareStatement(
				"INSERT INTO privilege (id, code, description, created_at, updated_at) VALUES (?,?,?,?,?)")) {
			setData(ps, 1, id);
			setData(ps, 2, code);
			setData(ps, 3, description);
			setData(ps, 4, ts);
			setData(ps, 5, ts);
			ps.executeUpdate();
		}
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	public String getCode() { return code; }
	public void setCode(String code) { this.code = code; }

	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
}
