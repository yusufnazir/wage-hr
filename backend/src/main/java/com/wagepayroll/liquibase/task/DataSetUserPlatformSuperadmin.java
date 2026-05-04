package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;

/**
 * Sets {@code platform_superadmin = true} on the given user account.
 * Used once during scaffold seeding to promote the first admin user.
 *
 * <pre>{@code
 * <customChange class="com.wagepayroll.liquibase.task.DataSetUserPlatformSuperadmin">
 *     <param name="userId" value="30000000-0000-0000-0000-000000000001"/>
 * </customChange>
 * }</pre>
 */
public class DataSetUserPlatformSuperadmin extends CustomDataTaskChange {

	private String userId;

	@Override
	public void handleUpdate() throws Exception {
		try (PreparedStatement ps = connection.prepareStatement(
				"UPDATE user_account SET platform_superadmin = ?, updated_at = ? WHERE id = ?")) {
			ps.setBoolean(1, true);
			setData(ps, 2, ts);
			setData(ps, 3, userId);
			ps.executeUpdate();
		}
	}

	public String getUserId() { return userId; }
	public void setUserId(String userId) { this.userId = userId; }
}
