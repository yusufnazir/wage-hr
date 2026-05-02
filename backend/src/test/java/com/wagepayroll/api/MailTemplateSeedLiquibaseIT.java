package com.wagepayroll.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

class MailTemplateSeedLiquibaseIT {

	@Test
	void seedChangesetAppliesAndIsIdempotent() throws Exception {
		String dbName = "mail_seed_" + UUID.randomUUID().toString().replace("-", "");
		String jdbcUrl = "jdbc:h2:mem:" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";

		try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "")) {
			Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
			Liquibase liquibase = new Liquibase("db/changelog/db.changelog-master.yaml", new ClassLoaderResourceAccessor(), database);

			liquibase.update(new Contexts(), new LabelExpression());
			assertThat(countCode(conn, "EMAIL_VERIFICATION")).isEqualTo(1);
			assertThat(countCode(conn, "PASSWORD_RESET_REQUEST")).isEqualTo(1);
			liquibase.update(new Contexts(), new LabelExpression());
			assertThat(countCode(conn, "EMAIL_VERIFICATION")).isEqualTo(1);
			assertThat(countCode(conn, "PASSWORD_RESET_REQUEST")).isEqualTo(1);
		}
	}

	private static int countCode(Connection conn, String code) throws Exception {
		try (Statement st = conn.createStatement();
				ResultSet rs = st.executeQuery("select count(*) from mail_template where code = '" + code + "'")) {
			rs.next();
			return rs.getInt(1);
		}
	}
}
