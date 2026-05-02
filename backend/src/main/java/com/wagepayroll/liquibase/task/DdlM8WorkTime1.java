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
 * M8 work time table.
 */
public class DdlM8WorkTime1 implements CustomTaskChange {

	@Override
	public void execute(Database database) throws CustomChangeException {
		try {
			Connection c = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
			c.setAutoCommit(false);
			try (Statement s = c.createStatement()) {
				s.execute("""
						CREATE TABLE tenant_work_time (
						  id VARCHAR(36) PRIMARY KEY,
						  tenant_id VARCHAR(36) NOT NULL,
						  company_id VARCHAR(36) NOT NULL,
						  name VARCHAR(120) NOT NULL,
						  code VARCHAR(40) NOT NULL,
						  hours_per_day DECIMAL(4,2) NOT NULL,
						  work_days_per_week SMALLINT NOT NULL,
						  description VARCHAR(500) NULL,
						  active BOOLEAN NOT NULL,
						  created_at TIMESTAMP NOT NULL,
						  updated_at TIMESTAMP NOT NULL,
						  CONSTRAINT fk_tenant_work_time_company FOREIGN KEY (company_id, tenant_id)
						    REFERENCES tenant_company(id, tenant_id)
						)
						""");
				s.execute("CREATE UNIQUE INDEX uidx_tenant_work_time_id_company_tenant ON tenant_work_time (id, company_id, tenant_id)");
				s.execute("CREATE UNIQUE INDEX uidx_tenant_work_time_company_code ON tenant_work_time (company_id, code)");
				s.execute("CREATE INDEX idx_tenant_work_time_tenant_company ON tenant_work_time (tenant_id, company_id)");

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
		return "M8 work time table created";
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
