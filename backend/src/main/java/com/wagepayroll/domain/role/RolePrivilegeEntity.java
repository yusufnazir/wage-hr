package com.wagepayroll.domain.role;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.wagepayroll.domain.TenantScopedEntity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "role_privilege")
public class RolePrivilegeEntity extends TenantScopedEntity {

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "role_id", length = 36, nullable = false)
	private UUID roleId;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "privilege_id", length = 36, nullable = false)
	private UUID privilegeId;

	public UUID getRoleId() {
		return roleId;
	}

	public void setRoleId(UUID roleId) {
		this.roleId = roleId;
	}

	public UUID getPrivilegeId() {
		return privilegeId;
	}

	public void setPrivilegeId(UUID privilegeId) {
		this.privilegeId = privilegeId;
	}
}
