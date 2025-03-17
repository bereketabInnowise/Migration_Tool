package org.bereketab.migrationLibrary; // Why: Defines the namespace—keeps your classes organized under your project’s domain.

import com.zaxxer.hikari.HikariConfig; // Why: Imports HikariCP’s config class—lets you set up the connection pool properties.
import com.zaxxer.hikari.HikariDataSource; // Why: Imports the actual pool implementation—manages DB connections efficiently.
import java.io.FileInputStream; // Why: Allows reading the external `migration.conf` file from the filesystem.
import java.io.IOException; // Why: Handles exceptions when file operations (like reading config) fail.
import java.util.Properties; // Why: Provides a key-value store to load and access config settings (e.g., db.url).

public class DatabaseConfig {
    // Why: This class sets up and provides a single, shared connection pool for the migration tool.
    private static HikariDataSource dataSource; // Why: Static field ensures one pool instance—shared across all uses (singleton pattern).

    public static HikariDataSource getDataSource() {
        // Why: Public method gives access to the pool—called by MigrationService to get DB connections.
        if (dataSource == null) { // Why: Lazy initialization—only creates the pool the first time it’s needed, saves resources.
            Properties props = new Properties(); // Why: Creates a container to hold config key-value pairs (e.g., db.url=...).
            try (FileInputStream fis = new FileInputStream("migration.conf")) { // Why: Tries to open migration.conf from the current directory—primary config source.
                props.load(fis); // Why: Reads the file’s contents into props—parses lines like `db.url=jdbc:...` into key-value pairs.
                System.out.println("Loaded migration.conf from working directory"); // Why: Confirms to user that the external file was found—helps debugging.
            } catch (IOException e) { // Why: Catches case where migration.conf is missing or unreadable—needs a fallback.
                try {
                    props.load(DatabaseConfig.class.getClassLoader().getResourceAsStream("application.properties")); // Why: Fallback—loads from classpath (e.g., src/main/resources) if external file fails.
                    System.out.println("Loaded application.properties from classpath (fallback)"); // Why: Informs user of fallback success—useful for app integration or testing.
                } catch (IOException ex) { // Why: Catches failure of both attempts—neither config file is available.
                    throw new RuntimeException("Failed to load migration.conf or application.properties. Provide migration.conf in the working directory or application.properties in the classpath.", ex); // Why: Stops the app with a clear error—guides user to fix config setup.
                }
            }
            HikariConfig config = new HikariConfig(); // Why: Creates a HikariCP config object—needed to set up the pool with your settings.
            config.setJdbcUrl(props.getProperty("db.url")); // Why: Sets the DB connection string (e.g., jdbc:postgresql://localhost:5432/yourdb)—tells Hikari where to connect.
            config.setUsername(props.getProperty("db.username")); // Why: Sets the DB username—required for authentication.
            config.setPassword(props.getProperty("db.password")); // Why: Sets the DB password—pairs with username for access.
            config.setDriverClassName(props.getProperty("db.driver", "org.postgresql.Driver")); // Why: Specifies the JDBC driver—defaults to PostgreSQL but allows override for other DBs.
            config.setMaximumPoolSize(10); // Why: Limits the pool to 20 connections—balances performance and DB load (tweakable).
            String url = config.getJdbcUrl(); // Why: Grabs the URL to validate it—ensures it’s not missing.
            if (url == null || url.trim().isEmpty()) { // Why: Checks if URL is absent or blank—critical for DB connection.
                throw new RuntimeException("db.url is missing or empty in config file"); // Why: Halts with error—user must fix config.
            }
            if (config.getUsername() == null || config.getUsername().trim().isEmpty()) { // Why: Validates username—can’t connect without it.
                throw new RuntimeException("db.username is missing or empty in config file"); // Why: Forces user to provide a username.
            }
            if (config.getPassword() == null || config.getPassword().trim().isEmpty()) { // Why: Ensures password isn’t missing—DBs often require it.
                throw new RuntimeException("db.password is missing or empty in config file"); // Why: Stops execution—prompts config correction.
            }
            dataSource = new HikariDataSource(config); // Why: Initializes the pool with your settings—now ready to hand out connections.
        }
        return dataSource; // Why: Returns the pool instance—caller (e.g., MigrationService) uses it to get DB connections.
    }
}