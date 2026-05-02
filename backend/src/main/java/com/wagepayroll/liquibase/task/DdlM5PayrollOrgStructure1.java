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
 * M5 payroll organization structure tables.
 */
public class DdlM5PayrollOrgStructure1 implements CustomTaskChange {

	@Override
	public void execute(Database database) throws CustomChangeException {
		try {
			Connection c = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
			c.setAutoCommit(false);
			try (Statement s = c.createStatement()) {
				s.execute("""
						CREATE TABLE tenant_company (
						  id VARCHAR(36) PRIMARY KEY,
						  tenant_id VARCHAR(36) NOT NULL,
						  name VARCHAR(120) NOT NULL,
						  legal_name VARCHAR(180) NOT NULL,
						  registration_number VARCHAR(80) NULL,
						  tax_id VARCHAR(80) NOT NULL,
						  payroll_country CHAR(2) NOT NULL,
						  currency CHAR(3) NOT NULL,
						  payroll_frequency VARCHAR(20) NOT NULL,
						  timezone VARCHAR(60) NOT NULL,
						  date_format VARCHAR(20) NOT NULL,
						  contact_email VARCHAR(190) NULL,
						  contact_phone VARCHAR(40) NULL,
						  address_line1 VARCHAR(180) NULL,
						  address_line2 VARCHAR(180) NULL,
						  city VARCHAR(120) NULL,
						  state_region VARCHAR(120) NULL,
						  postal_code VARCHAR(30) NULL,
						  country CHAR(2) NULL,
						  active BOOLEAN NOT NULL,
						  created_at TIMESTAMP NOT NULL,
						  updated_at TIMESTAMP NOT NULL,
						  CONSTRAINT fk_tenant_company_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
						)
						""");
				s.execute("CREATE UNIQUE INDEX uidx_tenant_company_id_tenant ON tenant_company (id, tenant_id)");
				s.execute("CREATE UNIQUE INDEX uidx_tenant_company_tenant_tax_id ON tenant_company (tenant_id, tax_id)");
				s.execute("CREATE INDEX idx_tenant_company_tenant_active ON tenant_company (tenant_id, active)");

				s.execute("""
						CREATE TABLE tenant_department (
						  id VARCHAR(36) PRIMARY KEY,
						  tenant_id VARCHAR(36) NOT NULL,
						  company_id VARCHAR(36) NOT NULL,
						  name VARCHAR(120) NOT NULL,
						  code VARCHAR(40) NOT NULL,
						  description VARCHAR(500) NULL,
						  parent_department_id VARCHAR(36) NULL,
						  manager_employee_id VARCHAR(36) NULL,
						  active BOOLEAN NOT NULL,
						  created_at TIMESTAMP NOT NULL,
						  updated_at TIMESTAMP NOT NULL,
						  CONSTRAINT fk_tenant_department_company FOREIGN KEY (company_id, tenant_id)
						    REFERENCES tenant_company(id, tenant_id)
						)
						""");
				s.execute("CREATE UNIQUE INDEX uidx_tenant_department_id_company_tenant ON tenant_department (id, company_id, tenant_id)");
				s.execute("CREATE UNIQUE INDEX uidx_tenant_department_company_code ON tenant_department (company_id, code)");
				s.execute("CREATE INDEX idx_tenant_department_tenant_company ON tenant_department (tenant_id, company_id)");
				s.execute("CREATE INDEX idx_tenant_department_parent ON tenant_department (parent_department_id)");
				s.execute("ALTER TABLE tenant_department ADD CONSTRAINT fk_tenant_department_parent FOREIGN KEY (parent_department_id, company_id, tenant_id) REFERENCES tenant_department(id, company_id, tenant_id)");

				s.execute("""
						CREATE TABLE tenant_job (
						  id VARCHAR(36) PRIMARY KEY,
						  tenant_id VARCHAR(36) NOT NULL,
						  company_id VARCHAR(36) NOT NULL,
						  department_id VARCHAR(36) NOT NULL,
						  title VARCHAR(140) NOT NULL,
						  code VARCHAR(40) NOT NULL,
						  description VARCHAR(500) NULL,
						  salary_type VARCHAR(20) NOT NULL,
						  default_salary DECIMAL(18,2) NULL,
						  default_hourly_rate DECIMAL(18,2) NULL,
						  standard_hours_per_week DECIMAL(5,2) NULL,
						  job_level VARCHAR(40) NULL,
						  job_category VARCHAR(60) NULL,
						  active BOOLEAN NOT NULL,
						  created_at TIMESTAMP NOT NULL,
						  updated_at TIMESTAMP NOT NULL,
						  CONSTRAINT fk_tenant_job_department FOREIGN KEY (department_id, company_id, tenant_id)
						    REFERENCES tenant_department(id, company_id, tenant_id)
						)
						""");
				s.execute("CREATE UNIQUE INDEX uidx_tenant_job_id_company_tenant ON tenant_job (id, company_id, tenant_id)");
				s.execute("CREATE UNIQUE INDEX uidx_tenant_job_company_code ON tenant_job (company_id, code)");
				s.execute("CREATE INDEX idx_tenant_job_tenant_company ON tenant_job (tenant_id, company_id)");
				s.execute("CREATE INDEX idx_tenant_job_department ON tenant_job (department_id)");

				s.execute("""
						CREATE TABLE tenant_employee_group (
						  id VARCHAR(36) PRIMARY KEY,
						  tenant_id VARCHAR(36) NOT NULL,
						  company_id VARCHAR(36) NOT NULL,
						  name VARCHAR(100) NOT NULL,
						  code VARCHAR(40) NOT NULL,
						  description VARCHAR(500) NULL,
						  active BOOLEAN NOT NULL,
						  created_at TIMESTAMP NOT NULL,
						  updated_at TIMESTAMP NOT NULL,
						  CONSTRAINT fk_tenant_employee_group_company FOREIGN KEY (company_id, tenant_id)
						    REFERENCES tenant_company(id, tenant_id)
						)
						""");
				s.execute("CREATE UNIQUE INDEX uidx_tenant_employee_group_id_company_tenant ON tenant_employee_group (id, company_id, tenant_id)");
				s.execute("CREATE UNIQUE INDEX uidx_tenant_employee_group_company_code ON tenant_employee_group (company_id, code)");
				s.execute("CREATE INDEX idx_tenant_employee_group_tenant_company ON tenant_employee_group (tenant_id, company_id)");

				s.execute("""
						CREATE TABLE tenant_employee (
						  id VARCHAR(36) PRIMARY KEY,
						  tenant_id VARCHAR(36) NOT NULL,
						  company_id VARCHAR(36) NOT NULL,
						  department_id VARCHAR(36) NOT NULL,
						  job_id VARCHAR(36) NOT NULL,
						  employee_group_id VARCHAR(36) NOT NULL,
						  first_name VARCHAR(100) NOT NULL,
						  last_name VARCHAR(100) NOT NULL,
						  date_of_birth DATE NULL,
						  hire_date DATE NOT NULL,
						  email VARCHAR(190) NULL,
						  phone VARCHAR(40) NULL,
						  status VARCHAR(30) NOT NULL,
						  active BOOLEAN NOT NULL,
						  created_at TIMESTAMP NOT NULL,
						  updated_at TIMESTAMP NOT NULL,
						  CONSTRAINT fk_tenant_employee_company FOREIGN KEY (company_id, tenant_id)
						    REFERENCES tenant_company(id, tenant_id),
						  CONSTRAINT fk_tenant_employee_department FOREIGN KEY (department_id, company_id, tenant_id)
						    REFERENCES tenant_department(id, company_id, tenant_id),
						  CONSTRAINT fk_tenant_employee_job FOREIGN KEY (job_id, company_id, tenant_id)
						    REFERENCES tenant_job(id, company_id, tenant_id),
						  CONSTRAINT fk_tenant_employee_group FOREIGN KEY (employee_group_id, company_id, tenant_id)
						    REFERENCES tenant_employee_group(id, company_id, tenant_id)
						)
						""");
				s.execute("CREATE UNIQUE INDEX uidx_tenant_employee_id_company_tenant ON tenant_employee (id, company_id, tenant_id)");
				s.execute("CREATE UNIQUE INDEX uidx_tenant_employee_company_email ON tenant_employee (company_id, email)");
				s.execute("CREATE INDEX idx_tenant_employee_tenant_company ON tenant_employee (tenant_id, company_id)");
				s.execute("CREATE INDEX idx_tenant_employee_department ON tenant_employee (department_id)");
				s.execute("CREATE INDEX idx_tenant_employee_job ON tenant_employee (job_id)");
				s.execute("CREATE INDEX idx_tenant_employee_group ON tenant_employee (employee_group_id)");
				s.execute("CREATE INDEX idx_tenant_employee_status ON tenant_employee (status)");

				s.execute("ALTER TABLE tenant_department ADD CONSTRAINT fk_tenant_department_manager_employee FOREIGN KEY (manager_employee_id, company_id, tenant_id) REFERENCES tenant_employee(id, company_id, tenant_id)");

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
		return "M5 payroll organization structure tables created";
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
