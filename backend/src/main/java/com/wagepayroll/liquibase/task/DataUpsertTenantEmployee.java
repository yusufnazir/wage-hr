package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Upserts {@code tenant_employee}. */
public class DataUpsertTenantEmployee extends CustomDataTaskChange {

	private String id;
	private String tenantId;
	private String companyId;
	private String departmentId;
	private String jobId;
	private String employeeGroupId;
	private String firstName;
	private String lastName;
	private String dateOfBirth;
	private String hireDate;
	private String email;
	private String phone;
	private String status;
	private String active;
	private String badgeNumber;
	private String idNumber;
	private String gender;
	private String nationality;
	private String placeOfBirth;
	private String civilState;
	private String resignationDate;
	private String addressStreet;
	private String addressNumber;
	private String addressCity;
	private String addressCountry;
	private String addressPostalCode;

	@Override
	public void handleUpdate() throws Exception {
		boolean activeBool = active == null || active.isBlank() || Boolean.parseBoolean(active.trim());
		try (PreparedStatement check = connection.prepareStatement("SELECT COUNT(*) FROM tenant_employee WHERE id = ?")) {
			setData(check, 1, id);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = connection.prepareStatement("""
							UPDATE tenant_employee SET
							  tenant_id = ?, company_id = ?, department_id = ?, job_id = ?, employee_group_id = ?,
							  first_name = ?, last_name = ?, date_of_birth = ?, hire_date = ?, email = ?, phone = ?,
							  status = ?, active = ?, badge_number = ?, id_number = ?, gender = ?, nationality = ?,
							  place_of_birth = ?, civil_state = ?, resignation_date = ?, address_street = ?,
							  address_number = ?, address_city = ?, address_country = ?, address_postal_code = ?,
							  updated_at = ?
							WHERE id = ?
							""")) {
						int i = 1;
						setData(ps, i++, tenantId);
						setData(ps, i++, companyId);
						setData(ps, i++, departmentId);
						setData(ps, i++, jobId);
						setData(ps, i++, employeeGroupId);
						setData(ps, i++, firstName);
						setData(ps, i++, lastName);
						setDate(ps, i++, dateOfBirth);
						setDate(ps, i++, hireDate);
						setData(ps, i++, email);
						setData(ps, i++, phone);
						setData(ps, i++, status);
						ps.setBoolean(i++, activeBool);
						setData(ps, i++, badgeNumber);
						setData(ps, i++, idNumber);
						setData(ps, i++, gender);
						setData(ps, i++, nationality);
						setData(ps, i++, placeOfBirth);
						setData(ps, i++, civilState);
						setDate(ps, i++, resignationDate);
						setData(ps, i++, addressStreet);
						setData(ps, i++, addressNumber);
						setData(ps, i++, addressCity);
						setData(ps, i++, addressCountry);
						setData(ps, i++, addressPostalCode);
						setData(ps, i++, ts);
						setData(ps, i++, id);
						ps.executeUpdate();
					}
					return;
				}
			}
		}
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO tenant_employee (
				  id, tenant_id, company_id, department_id, job_id, employee_group_id,
				  first_name, last_name, date_of_birth, hire_date, email, phone, status, active,
				  badge_number, id_number, gender, nationality, place_of_birth, civil_state, resignation_date,
				  address_street, address_number, address_city, address_country, address_postal_code,
				  created_at, updated_at
				) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
				""")) {
			int i = 1;
			setData(ps, i++, id);
			setData(ps, i++, tenantId);
			setData(ps, i++, companyId);
			setData(ps, i++, departmentId);
			setData(ps, i++, jobId);
			setData(ps, i++, employeeGroupId);
			setData(ps, i++, firstName);
			setData(ps, i++, lastName);
			setDate(ps, i++, dateOfBirth);
			setDate(ps, i++, hireDate);
			setData(ps, i++, email);
			setData(ps, i++, phone);
			setData(ps, i++, status);
			ps.setBoolean(i++, activeBool);
			setData(ps, i++, badgeNumber);
			setData(ps, i++, idNumber);
			setData(ps, i++, gender);
			setData(ps, i++, nationality);
			setData(ps, i++, placeOfBirth);
			setData(ps, i++, civilState);
			setDate(ps, i++, resignationDate);
			setData(ps, i++, addressStreet);
			setData(ps, i++, addressNumber);
			setData(ps, i++, addressCity);
			setData(ps, i++, addressCountry);
			setData(ps, i++, addressPostalCode);
			setData(ps, i++, ts);
			setData(ps, i++, ts);
			ps.executeUpdate();
		}
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
	public String getTenantId() { return tenantId; }
	public void setTenantId(String tenantId) { this.tenantId = tenantId; }
	public String getCompanyId() { return companyId; }
	public void setCompanyId(String companyId) { this.companyId = companyId; }
	public String getDepartmentId() { return departmentId; }
	public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
	public String getJobId() { return jobId; }
	public void setJobId(String jobId) { this.jobId = jobId; }
	public String getEmployeeGroupId() { return employeeGroupId; }
	public void setEmployeeGroupId(String employeeGroupId) { this.employeeGroupId = employeeGroupId; }
	public String getFirstName() { return firstName; }
	public void setFirstName(String firstName) { this.firstName = firstName; }
	public String getLastName() { return lastName; }
	public void setLastName(String lastName) { this.lastName = lastName; }
	public String getDateOfBirth() { return dateOfBirth; }
	public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }
	public String getHireDate() { return hireDate; }
	public void setHireDate(String hireDate) { this.hireDate = hireDate; }
	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }
	public String getPhone() { return phone; }
	public void setPhone(String phone) { this.phone = phone; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public String getActive() { return active; }
	public void setActive(String active) { this.active = active; }
	public String getBadgeNumber() { return badgeNumber; }
	public void setBadgeNumber(String badgeNumber) { this.badgeNumber = badgeNumber; }
	public String getIdNumber() { return idNumber; }
	public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
	public String getGender() { return gender; }
	public void setGender(String gender) { this.gender = gender; }
	public String getNationality() { return nationality; }
	public void setNationality(String nationality) { this.nationality = nationality; }
	public String getPlaceOfBirth() { return placeOfBirth; }
	public void setPlaceOfBirth(String placeOfBirth) { this.placeOfBirth = placeOfBirth; }
	public String getCivilState() { return civilState; }
	public void setCivilState(String civilState) { this.civilState = civilState; }
	public String getResignationDate() { return resignationDate; }
	public void setResignationDate(String resignationDate) { this.resignationDate = resignationDate; }
	public String getAddressStreet() { return addressStreet; }
	public void setAddressStreet(String addressStreet) { this.addressStreet = addressStreet; }
	public String getAddressNumber() { return addressNumber; }
	public void setAddressNumber(String addressNumber) { this.addressNumber = addressNumber; }
	public String getAddressCity() { return addressCity; }
	public void setAddressCity(String addressCity) { this.addressCity = addressCity; }
	public String getAddressCountry() { return addressCountry; }
	public void setAddressCountry(String addressCountry) { this.addressCountry = addressCountry; }
	public String getAddressPostalCode() { return addressPostalCode; }
	public void setAddressPostalCode(String addressPostalCode) { this.addressPostalCode = addressPostalCode; }
}
