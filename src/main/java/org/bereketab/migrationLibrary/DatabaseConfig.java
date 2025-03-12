package org.bereketab.migrationLibrary;
//importing the necessary libraries to build the connection pool
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
//import Properties to retrieve data from application.properties
import java.util.Properties;
//import IOException to handle the input output exception.
import java.io.IOException;

public class DatabaseConfig {
//    defining the datasource to establish the connection with the postgresql database. In ,JDBC we may establish connection using the Connection cn = DriverManager.getConnection(url, username, password).
    public static HikariDataSource dataSource;

    static{
        Properties props = new Properties();
        try{
            props.load(DatabaseConfig.class.getClassLoader().getResourceAsStream("application.properties"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load application.properties",e);
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(props.getProperty("db.url"));
        config.setUsername(props.getProperty("db.username"));
        config.setPassword(props.getProperty("db.password"));
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(20);
        dataSource = new HikariDataSource(config);
    }
    public static HikariDataSource getDataSource(){
        return dataSource;
    }

}
