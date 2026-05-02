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
 * M9 pay period and pay period run tables.
 */
public class DdlM9PayPeriod1 implements CustomTaskChange {

	@Override
	public void execute(Database database) throws CustomChangeException {
		try {
			Connection c = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
			c.setAutoCommit(false);
			try (Statement s = c.createStatement()) {
				s.execute("""
						CREATE TABLE tenant_pay_period (
						  id VARCHAR(36) PRIMARY KEY,
						  tenant_id VARCHAR(36) NOT NULL,
						  company_id VARCHAR(36) NOT NULL,
						  year SMALLINT NOT NULL,
						  start_date DATE NOT NULL,
						  end_date DATE NOT NULL,
						  status VARCHAR(20) NOT NULL,
						  created_at TIMESTAMP NOT NULL,
						  updated_at TIMESTAMP NOT NULL,
						  CONSTRAINT fk_tenant_pay_period_company FOREIGN KEY (company_id, tenant_id)
						    REFERENCES tenant_company(id, tenant_id)
						)
						""");
				s.execute("CREATE UNIQUE INDEX uidx_tenant_pay_period_id_company_tenant ON tenant_pay_period (id, company_id, tenant_id)");
				s.execute("CREATE INDEX idx_tenant_pay_period_tenant_company_year ON tenant_pay_period (tenant_id, company_id, year)");

				s.execute("""
						CREATE TABLE tenant_pay_period_run (
						  id VARCHAR(36) PRIMARY KEY,
						  tenant_id VARCHAR(36) NOT NULL,
						  pay_period_id VARCHAR(36) NOT NULL,
						  run_type VARCHAR(20) NOT NULL,
						  run_number SMALLINT NOT NULL,
						  created_at TIMESTAMP NOT NULL,
						  updated_at TIMESTAMP NOT NULL,
						  CONSTRAINT fk_tenant_pay_period_run_period FOREIGN KEY (pay_period_id, tenant_id)
						    REFERENCES tenant_pay_period(id, tenant_id)
						)
						""");
				s.execute("CREATE UNIQUE INDEX uidx_tenant_pay_period_run_id_period_tenant ON tenant_pay_period_run (id, pay_period_id, tenant_id)");
				s.execute("CREATE INDEX idx_tenant_pay_period_run_tenant_period ON tenant_pay_period_run (tenant_id, pay_period_id)");
				s.execute("CREATE UNIQUE INDEX uidx_tenant_pay_period_run_period_number ON tenant_pay_period_run (pay_period_id, run_number)");

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
		return "M9 pay period and pay period run tables created";
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
