package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Upserts one row in the {@code tenant} table.
 *
 * <pre>{@code
 * <customChange class="com.wagepayroll.liquibase.task.DataUpsertTenant">
 *     <param name="id"     value="10000000-0000-0000-0000-000000000001"/>
 *     <param name="handle" value="demo"/>
 *     <param name="name"   value="Demo tenant"/>
 * </customChange>
 * }</pre>
 */
public class DataUpsertTenant extends CustomDataTaskChange {

	private String id;
	private String handle;
	private String name;

	@Override
	public void handleUpdate() throws Exception {
		try (PreparedStatement check = connection.prepareStatement(
				"SELECT COUNT(*) FROM tenant WHERE id = ?")) {
			setData(check, 1, id);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = connection.prepareStatement(
							"UPDATE tenant SET handle = ?, name = ?, updated_at = ? WHERE id = ?")) {
						setData(ps, 1, handle);
						setData(ps, 2, name);
						setData(ps, 3, ts);
						setData(ps, 4, id);
						ps.executeUpdate();
					}
					return;
				}
			}
		}
		try (PreparedStatement ps = connection.prepareStatement(
				"INSERT INTO tenant (id, handle, name, created_at, updated_at) VALUES (?,?,?,?,?)")) {
			setData(ps, 1, id);
			setData(ps, 2, handle);
			setData(ps, 3, name);
			setData(ps, 4, ts);
			setData(ps, 5, ts);
			ps.executeUpdate();
		}
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	public String getHandle() { return handle; }
	public void setHandle(String handle) { this.handle = handle; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
}
