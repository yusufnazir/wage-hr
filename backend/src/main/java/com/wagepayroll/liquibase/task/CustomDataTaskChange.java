package com.wagepayroll.liquibase.task;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.DatabaseException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

/**
 * Abstract base class for all DML seed tasks.
 *
 * <p>Subclasses declare fields with getters/setters; Liquibase injects {@code <param>} values
 * from the changeset XML before calling {@link #handleUpdate()}. The connection is committed
 * automatically after a successful {@code handleUpdate()} call.
 *
 * <p>Use {@link #setData(PreparedStatement, int, Object)} for null-safe, type-aware binding.
 * A current {@link Timestamp} is pre-computed in {@link #ts} for convenience.
 */
public abstract class CustomDataTaskChange implements CustomTaskChange {

	protected JdbcConnection connection;

	/** Execution timestamp shared across all SQL statements in one changeset. */
	protected Timestamp ts;

	// -------------------------------------------------------------------------
	// Lifecycle
	// -------------------------------------------------------------------------

	@Override
	public final void execute(Database database) throws CustomChangeException {
		connection = (JdbcConnection) database.getConnection();
		ts = Timestamp.from(Instant.now());
		try {
			handleUpdate();
			connection.commit();
		} catch (DatabaseException | SQLException e) {
			throw new CustomChangeException(e.getMessage(), e);
		} catch (Exception e) {
			throw new CustomChangeException(e.getMessage(), e);
		}
	}

	/**
	 * Implement the DML logic here. Use {@link #connection} and {@link #ts}.
	 * Do NOT call {@code commit()} — the base class does that.
	 */
	public abstract void handleUpdate() throws Exception;

	// -------------------------------------------------------------------------
	// Boilerplate stubs
	// -------------------------------------------------------------------------

	@Override
	public String getConfirmationMessage() {
		return getClass().getSimpleName() + " applied";
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

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Null-safe, type-aware {@link PreparedStatement} binder.
	 */
	protected void setData(PreparedStatement ps, int index, Object value) throws SQLException {
		if (value == null) {
			ps.setNull(index, Types.NULL);
		} else if (value instanceof String s) {
			if (s.isBlank()) {
				ps.setNull(index, Types.NULL);
			} else {
				ps.setString(index, s);
			}
		} else if (value instanceof Boolean b) {
			ps.setBoolean(index, b);
		} else if (value instanceof Integer i) {
			ps.setInt(index, i);
		} else if (value instanceof Long l) {
			ps.setLong(index, l);
		} else if (value instanceof Double d) {
			ps.setDouble(index, d);
		} else if (value instanceof BigDecimal bd) {
			ps.setBigDecimal(index, bd);
		} else if (value instanceof Timestamp t) {
			ps.setTimestamp(index, t);
		} else {
			ps.setObject(index, value);
		}
	}
}
