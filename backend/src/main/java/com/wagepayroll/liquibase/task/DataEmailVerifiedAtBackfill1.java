package com.wagepayroll.liquibase.task;

import java.sql.Connection;
import java.sql.Statement;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

/**
 * One-time: existing {@code user_account} rows pre-email-verification feature must be treated as verified so seeds and
 * ITs keep working (see {@code docs/modules/account-registration.md}).
 */
public class DataEmailVerifiedAtBackfill1 implements CustomTaskChange {

	@Override
	public void execute(Database database) throws CustomChangeException {
		try {
			Connection c = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
			try (Statement st = c.createStatement()) {
				st.executeUpdate(
						"UPDATE user_account SET email_verified_at = created_at WHERE email_verified_at IS NULL");
			}
		}
		catch (Exception e) {
			throw new CustomChangeException(e.getMessage(), e);
		}
	}

	@Override
	public String getConfirmationMessage() {
		return "Backfilled email_verified_at for existing user_account rows";
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
