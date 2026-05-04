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
 * M11 platform + tenant bank template tables.
 */
public class DdlM11PlatformBankTemplates1 implements CustomTaskChange {

	@Override
	public void execute(Database database) throws CustomChangeException {
		try {
			Connection c = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
			c.setAutoCommit(false);
			try (Statement s = c.createStatement()) {
				s.execute("""
						CREATE TABLE platform_bank_template (
						  id VARCHAR(36) PRIMARY KEY,
						  country_code CHAR(2) NOT NULL,
						  name VARCHAR(150) NOT NULL,
						  bank_name VARCHAR(150) NULL,
						  swift_bic VARCHAR(11) NULL,
						  bank_code VARCHAR(30) NULL,
						  account_number_format VARCHAR(100) NULL,
												  active BOOLEAN NOT NULL DEFAULT true,
						  created_at TIMESTAMP NOT NULL,
						  updated_at TIMESTAMP NOT NULL
						)
						""");
				s.execute("CREATE INDEX idx_platform_bank_template_country_active ON platform_bank_template (country_code, active)");
				s.execute("CREATE INDEX idx_platform_bank_template_country ON platform_bank_template (country_code)");

				s.execute("""
						CREATE TABLE tenant_bank_template (
						  id VARCHAR(36) PRIMARY KEY,
						  tenant_id VARCHAR(36) NOT NULL,
						  company_id VARCHAR(36) NOT NULL,
						  platform_bank_template_id VARCHAR(36) NULL,
						  country_code CHAR(2) NOT NULL,
						  name VARCHAR(150) NOT NULL,
						  bank_name VARCHAR(150) NULL,
						  swift_bic VARCHAR(11) NULL,
						  bank_code VARCHAR(30) NULL,
						  account_number_format VARCHAR(100) NULL,
												  active BOOLEAN NOT NULL DEFAULT true,
						  created_at TIMESTAMP NOT NULL,
						  updated_at TIMESTAMP NOT NULL,
						  CONSTRAINT fk_tenant_bank_template_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
						  CONSTRAINT fk_tenant_bank_template_company FOREIGN KEY (company_id, tenant_id)
						    REFERENCES tenant_company(id, tenant_id),
						  CONSTRAINT fk_tenant_bank_template_platform FOREIGN KEY (platform_bank_template_id)
						    REFERENCES platform_bank_template(id)
						)
						""");
				s.execute("CREATE INDEX idx_tenant_bank_template_tenant_company ON tenant_bank_template (tenant_id, company_id)");
				s.execute("CREATE INDEX idx_tenant_bank_template_tenant ON tenant_bank_template (tenant_id)");
				s.execute("CREATE INDEX idx_tenant_bank_template_company ON tenant_bank_template (company_id)");

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
		return "M11 platform bank template tables created";
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
