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
 * Drops the currency_code column from platform_bank_template and tenant_bank_template.
 * For databases created before this change was applied to the initial DDL.
 */
public class DdlM11BankTemplatesDropCurrency1 implements CustomTaskChange {

	@Override
	public void execute(Database database) throws CustomChangeException {
		try {
			Connection c = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
			c.setAutoCommit(false);
			try (Statement s = c.createStatement()) {
				s.execute("ALTER TABLE platform_bank_template DROP COLUMN IF EXISTS currency_code");
				s.execute("ALTER TABLE tenant_bank_template DROP COLUMN IF EXISTS currency_code");
				c.commit();
			}
			catch (Exception e) {
				c.rollback();
				throw e;
			}
		}
		catch (Exception e) {
			throw new CustomChangeException(e.getMessage(), e);
		}
	}

	@Override
	public String getConfirmationMessage() {
		return "Dropped currency_code from platform_bank_template and tenant_bank_template";
	}

	@Override
	public void setUp() throws SetupException {
	}

	@Override
	public void setFileOpener(ResourceAccessor resourceAccessor) {
	}

	@Override
	public ValidationErrors validate(Database database) {
		return new ValidationErrors();
	}
}
