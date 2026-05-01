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

public class DdlExchangeRateTable1 implements CustomTaskChange {

	@Override
	public void execute(Database database) throws CustomChangeException {
		try {
			Connection c = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
			c.setAutoCommit(false);
			try (Statement s = c.createStatement()) {
				s.execute("""
						CREATE TABLE tenant_exchange_rate (
						  id VARCHAR(36) PRIMARY KEY,
						  tenant_id VARCHAR(36) NOT NULL,
						  from_currency_id VARCHAR(36) NOT NULL,
						  to_currency_id VARCHAR(36) NOT NULL,
						  rate DECIMAL(18,8) NOT NULL,
						  effective_date DATE NOT NULL,
						  created_at TIMESTAMP NOT NULL,
						  updated_at TIMESTAMP NOT NULL,
						  CONSTRAINT fk_tenant_exchange_rate_tenant
						    FOREIGN KEY (tenant_id) REFERENCES tenant(id),
						  CONSTRAINT fk_tenant_exchange_rate_from_currency
						    FOREIGN KEY (from_currency_id) REFERENCES platform_currency(id),
						  CONSTRAINT fk_tenant_exchange_rate_to_currency
						    FOREIGN KEY (to_currency_id) REFERENCES platform_currency(id),
						  CONSTRAINT chk_tenant_exchange_rate_rate_positive CHECK (rate > 0)
						)
						""");
				s.execute(
						"CREATE UNIQUE INDEX uidx_tenant_exchange_rate_pair_date ON tenant_exchange_rate (tenant_id, from_currency_id, to_currency_id, effective_date)");
				s.execute("CREATE INDEX idx_tenant_exchange_rate_tenant ON tenant_exchange_rate (tenant_id)");
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
		return "Exchange rate table created";
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
