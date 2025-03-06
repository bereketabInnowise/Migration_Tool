package org.bereketab.migrationLibrary;
//importing the necessary libraries to build the connection pool
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DatabaseConfig {
//    defining the datasource to establish the connection with the postgresql database. In JDBC we may establish connection using the Connection cn = DriverManager.getConnection(url, username, password).
    private static final HikariDataSource dataSource;

    static{
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("bereketab");
        config.setPassword("bereketabInnowise");
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(20);
        dataSource = new HikariDataSource(config);
    }
    public static HikariDataSource getDataSource(){
        return dataSource;
    }

}
