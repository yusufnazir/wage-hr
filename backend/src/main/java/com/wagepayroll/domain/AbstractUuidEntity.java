package com.wagepayroll.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@MappedSuperclass
public abstract class AbstractUuidEntity {

	@Id
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "id", length = 36, nullable = false, updatable = false)
	private UUID id;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}
}
