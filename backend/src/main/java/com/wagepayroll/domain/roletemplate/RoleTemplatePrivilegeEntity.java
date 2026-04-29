package com.wagepayroll.domain.roletemplate;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.AbstractUuidEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "role_template_privilege")
public class RoleTemplatePrivilegeEntity extends AbstractUuidEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "role_template_id", length = 36, nullable = false)
	private UUID roleTemplateId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "privilege_id", length = 36, nullable = false)
	private UUID privilegeId;

	public UUID getRoleTemplateId() {
		return roleTemplateId;
	}

	public void setRoleTemplateId(UUID roleTemplateId) {
		this.roleTemplateId = roleTemplateId;
	}

	public UUID getPrivilegeId() {
		return privilegeId;
	}

	public void setPrivilegeId(UUID privilegeId) {
		this.privilegeId = privilegeId;
	}
}

