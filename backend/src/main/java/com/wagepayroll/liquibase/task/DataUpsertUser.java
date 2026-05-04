package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Upserts one row in the {@code user_account} table.
 * Pass a pre-computed BCrypt hash as {@code passwordHash}.
 * {@code email_verified_at} is always set so seed accounts are ready to log in.
 *
 * <pre>{@code
 * <customChange class="com.wagepayroll.liquibase.task.DataUpsertUser">
 *     <param name="id"           value="30000000-0000-0000-0000-000000000001"/>
 *     <param name="email"        value="admin@demo.lvh.me"/>
 *     <param name="passwordHash" value="$2a$10$..."/>
 * </customChange>
 * }</pre>
 */
public class DataUpsertUser extends CustomDataTaskChange {

	private String id;
	private String email;
	private String passwordHash;

	@Override
	public void handleUpdate() throws Exception {
		try (PreparedStatement check = connection.prepareStatement(
				"SELECT COUNT(*) FROM user_account WHERE id = ?")) {
			setData(check, 1, id);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = connection.prepareStatement(
							"UPDATE user_account SET email = ?, password_hash = ?, email_verified_at = ?, updated_at = ? WHERE id = ?")) {
						setData(ps, 1, email);
						setData(ps, 2, passwordHash);
						setData(ps, 3, ts);
						setData(ps, 4, ts);
						setData(ps, 5, id);
						ps.executeUpdate();
					}
					return;
				}
			}
		}
		try (PreparedStatement ps = connection.prepareStatement(
				"INSERT INTO user_account (id, email, password_hash, email_verified_at, created_at, updated_at) VALUES (?,?,?,?,?,?)")) {
			setData(ps, 1, id);
			setData(ps, 2, email);
			setData(ps, 3, passwordHash);
			setData(ps, 4, ts);
			setData(ps, 5, ts);
			setData(ps, 6, ts);
			ps.executeUpdate();
		}
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }

	public String getPasswordHash() { return passwordHash; }
	public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
