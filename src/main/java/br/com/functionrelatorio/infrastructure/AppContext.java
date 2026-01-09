package br.com.functionrelatorio.infrastructure;

import br.com.functionrelatorio.repository.JdbcReportRepository;
import br.com.functionrelatorio.repository.ReportRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public final class AppContext {

    private static final HikariDataSource DATA_SOURCE = buildDataSource();
    private static final ReportRepository REPORT_REPOSITORY = new JdbcReportRepository(DATA_SOURCE);

    private AppContext() {}

    public static ReportRepository reportRepository() {
        return REPORT_REPOSITORY;
    }

    public static DataSource dataSource() {
        return DATA_SOURCE;
    }

    private static HikariDataSource buildDataSource() {
        String jdbcUrl = mustGetEnv("DB_URL");
        String user = mustGetEnv("DB_USER");
        String password = mustGetEnv("DB_PASSWORD");

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(user);
        cfg.setPassword(password);

        cfg.setMaximumPoolSize(5);
        cfg.setMinimumIdle(0);
        cfg.setConnectionTimeout(10_000);

        return new HikariDataSource(cfg);
    }

    private static String mustGetEnv(String name) {
        String v = System.getenv(name);
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalStateException("Variável de ambiente obrigatória não definida: " + name);
        }
        return v.trim();
    }
}