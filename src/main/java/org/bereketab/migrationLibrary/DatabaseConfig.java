package org.bereketab.migrationLibrary;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class DatabaseConfig {
    private static HikariDataSource dataSource;

    public static HikariDataSource getDataSource() {
        // Initialize and return shared connection pool
        if (dataSource == null) {
            Properties props = loadConfigProperties();
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(props.getProperty("db.url"));
            config.setUsername(props.getProperty("db.username"));
            config.setPassword(props.getProperty("db.password"));
            config.setDriverClassName(props.getProperty("db.driver", "org.postgresql.Driver"));
            config.setMaximumPoolSize(10);
            validateConfig(config);
            dataSource = new HikariDataSource(config);
        }
        return dataSource;
    }

    private static Properties loadConfigProperties() {
        // Load config from migration.conf or fallback to application.properties
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream("migration.conf")) {
            props.load(fis);
            System.out.println("Loaded migration.conf from working directory");
        } catch (IOException e) {
            try {
                props.load(DatabaseConfig.class.getClassLoader().getResourceAsStream("application.properties"));
                System.out.println("Loaded application.properties from classpath (fallback)");
            } catch (IOException ex) {
                throw new RuntimeException("Failed to load migration.conf or application.properties", ex);
            }
        }
        return props;
    }

    private static void validateConfig(HikariConfig config) {
        // Validate required config properties
        if (config.getJdbcUrl() == null || config.getJdbcUrl().trim().isEmpty()) {
            throw new RuntimeException("db.url is missing or empty in config file");
        }
        if (config.getUsername() == null || config.getUsername().trim().isEmpty()) {
            throw new RuntimeException("db.username is missing or empty in config file");
        }
        if (config.getPassword() == null || config.getPassword().trim().isEmpty()) {
            throw new RuntimeException("db.password is missing or empty in config file");
        }
    }
}