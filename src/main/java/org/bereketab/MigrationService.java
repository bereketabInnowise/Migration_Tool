//package org.bereketab;
//
//import com.zaxxer.hikari.HikariDataSource;
//import org.bereketab.migrationLibrary.DatabaseConfig;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.io.File;
//import java.util.Properties;
//
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.security.MessageDigest;
//import java.security.NoSuchAlgorithmException;
//import java.sql.Connection;
//import java.sql.PreparedStatement;
//import java.sql.ResultSet;
//import java.sql.SQLException;
//import java.sql.Statement;
//import java.util.List;
//
//public class MigrationService{
//    private static final Logger logger = LoggerFactory.getLogger(MigrationService.class);
//    public final HikariDataSource dataSource;
//    private final String migrationsDir;
//
//    public MigrationService(HikariDataSource dataSource) {
//        this.dataSource = dataSource;
//        Properties props = new Properties();
//
//        try {
//            props.load(MigrationService.class.getClassLoader().getResourceAsStream("application.properties"));
//            this.migrationsDir = props.getProperty("migrations.dir");
//        } catch (IOException e) {
//            throw new RuntimeException("Failed to load migrations.dir",e);
//        }
//    }
//
//    public List<Path> getMigrationFiles() throws IOException {
//        Path dir = Paths.get("src/main/resources", migrationsDir);
//        return Files.list(dir)
//                .filter(path -> path.toString().endsWith(".sql"))
//                .sorted()
//                .toList();
//    }
////    public void runMigrations() throws IOException, SQLException {
////        logger.info("Starting migration process...");
////        Path dir = Paths.get("src/main/resources", migrationsDir);
////        List<Path> migrationFiles = Files.list(dir)
////                .filter(path -> path.toString().endsWith(".sql"))
////                .sorted()
////                .toList();
////
////        try (Connection conn = dataSource.getConnection()) {
////            for (Path file : migrationFiles) {
////                String filename = file.getFileName().toString();
////                String version = filename.split("__")[0];
////
////                if (isMigrationApplied(conn, version)) {
////                    String existingChecksum = getExistingChecksum(conn, version);
////                    String currentChecksum = calculateChecksum(Files.readString(file));
////                    if(!existingChecksum.equals(currentChecksum)){
////                        logger.warn("Migration {} has changed since the last application! Existing Checksum: {}, Current Checksum: {}", filename, existingChecksum, currentChecksum);
////                    }
////                    logger.info("Skipping already applied migration: {}", filename);
////                    continue;
////                }
////
////                try (Connection migrationConn = dataSource.getConnection()) {
////                    migrationConn.setAutoCommit(false);
////                    try {
////                        String sql = Files.readString(file);
////                        String checksum = calculateChecksum(sql);
////                        logger.info("Applying migration: {} (checksum: {})", filename, checksum);
////                        executeMigration(migrationConn, sql);
////                        recordMigration(migrationConn, version, filename, checksum);
////                        migrationConn.commit();
////                    } catch (SQLException e) {
////                        migrationConn.rollback();
////                        logger.error("Migration failed, rolling back: {}", filename, e);
////                        throw e;
////                    }
////                }
////            }
////        }
////    }
//
//    public boolean isMigrationApplied(Connection conn, String version) throws SQLException {
//        String checkSql = "SELECT COUNT(*) FROM migration_history WHERE version = ?";
//        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
//            checkStmt.setString(1, version);
//            ResultSet rs = checkStmt.executeQuery();
//            rs.next();
//            return rs.getInt(1) > 0;
//        } catch (SQLException e) {
//            if (e.getSQLState().equals("42P01")) { // Table doesn’t exist
//                return false;
//            }
//            throw e;
//        }
//    }
//
////    private void executeMigration(Connection conn, String sql) throws SQLException {
////        try (Statement stmt = conn.createStatement()) {
////            stmt.execute(sql);
////        }
////    }
//
////    private void recordMigration(Connection conn, String version, String filename, String checksum) throws SQLException {
////        String insertSql = "INSERT INTO migration_history (version, file_name, checksum) VALUES (?, ?, ?)";
////        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
////            insertStmt.setString(1, version);
////            insertStmt.setString(2, filename);
////            insertStmt.setString(3, checksum);
////            insertStmt.executeUpdate();
////        }
////    }
//    public void applyMigration(Connection conn, String version, String filename, String sql) throws SQLException {
//        conn.setAutoCommit(false);
//        try{
//            try(Statement stmt = conn.createStatement()){
//                stmt.execute(sql);
//            }
//            String insertSql = "INSERT INTO migration_history (version, file_name, checksum) VALUES (?, ?, ?)";
//            try(PreparedStatement insertStmt = conn.prepareStatement(insertSql)){
//                insertStmt.setString(1,version);
//                insertStmt.setString(2, filename);
//                insertStmt.setString(3, calculateChecksum(sql));
//                insertStmt.executeUpdate();
//            }
//            conn.commit();
//        } catch (SQLException e) {
//            conn.rollback();
//            throw e;
//        }
//    }
//    public void rollbackMigration(Connection conn, String version, String filename) throws SQLException, IOException{
//        conn.setAutoCommit(false);
//        try{
//            String rollbackFileName = filename.replace(".sql","_rollback.sql");
//            Path rollbackPath = Paths.get("src/main/resources", migrationsDir,rollbackFileName);
//            if(Files.exists(rollbackPath)){
//                String rollbackSql = Files.readString(rollbackPath);
//                try(Statement stmt = conn.createStatement()){
//                    stmt.execute(rollbackSql);
//                }
//                logger.info("Executed rollback script: {}", rollbackFileName);
//            }else {
//                logger.warn("No rollback script found for: {}", filename);
//            }
//            String deleteSql = "DELETE FROM migration_history WHERE version = ?";
//            try(PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)){
//                deleteStmt.setString(1, version);
//                deleteStmt.executeUpdate();
//            }
//            conn.commit();
//        }catch (SQLException | IOException e){
//            conn.rollback();
//            throw e;
//        }
//
//    }
//    public String getExistingChecksum(Connection conn, String version) throws SQLException{
//        String sql = "SELECT checksum FROM migration_history WHERE  version = ?";
//        try (PreparedStatement stmt = conn.prepareStatement(sql)){
//            stmt.setString(1, version);
//            ResultSet rs = stmt.executeQuery();
//            if(rs.next()){
//                return rs.getString("checksum");
//            }
//            return null;
//        }
//    }
//    public String calculateChecksum(String sql) {
//        try {
//            MessageDigest digest = MessageDigest.getInstance("SHA-256");
//            byte[] hash = digest.digest(sql.getBytes());
//            StringBuilder hexString = new StringBuilder();
//            for (byte b : hash) {
//                String hex = Integer.toHexString(0xff & b);
//                if (hex.length() == 1) hexString.append('0');
//                hexString.append(hex);
//            }
//            return hexString.toString();
//        } catch (NoSuchAlgorithmException e) {
//            logger.error("Failed to calculate checksum", e);
//            return "error_checksum";
//        }
//    }
//
//}
package org.bereketab;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

public class MigrationService {
    private static final Logger logger = LoggerFactory.getLogger(MigrationService.class);
    public final HikariDataSource dataSource;
    private final String migrationsDir;

    public MigrationService(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        Properties props = new Properties();
        try {
            props.load(MigrationService.class.getClassLoader().getResourceAsStream("application.properties"));
            this.migrationsDir = props.getProperty("migrations.dir");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load migrations.dir", e);
        }
    }

    public List<Path> getMigrationFiles() throws IOException {
        Path dir = Paths.get("src/main/resources", migrationsDir);
        return Files.list(dir)
                .filter(path -> path.toString().endsWith(".sql") && !path.toString().contains("_rollback.sql"))
                .sorted()
                .toList();
    }

    public boolean isMigrationApplied(Connection conn, String version) throws SQLException {
        String sql = "SELECT COUNT(*) FROM migration_history WHERE version = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, version);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getInt(1) > 0;
        } catch (SQLException e) {
            if (e.getSQLState().equals("42P01")) return false;
            throw e;
        }
    }

    public String getExistingChecksum(Connection conn, String version) throws SQLException {
        String sql = "SELECT checksum FROM migration_history WHERE version = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, version);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("checksum");
            return null;
        }
    }

    public void applyMigration(Connection conn, String version, String filename, String sql) throws SQLException {
        conn.setAutoCommit(false);
        try {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
            String insertSql = "INSERT INTO migration_history (version, file_name, checksum) VALUES (?, ?, ?)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, version);
                insertStmt.setString(2, filename);
                insertStmt.setString(3, calculateChecksum(sql));
                insertStmt.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        }
    }

    public void rollbackMigration(Connection conn, String version, String filename) throws SQLException, IOException {
        conn.setAutoCommit(false);
        try {

            // Look for a rollback script (e.g., V1__create_schema_rollback.sql)
            String rollbackFileName = filename.replace(".sql", "_rollback.sql");
            Path rollbackPath = Paths.get("target/test-migrations", rollbackFileName);
            if (!Files.exists(rollbackPath)){
                rollbackPath = Paths.get("src/main/resources", migrationsDir, rollbackFileName);
            }
            if (Files.exists(rollbackPath)) {
                String rollbackSql = Files.readString(rollbackPath);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(rollbackSql);
                }
                String deleteSql = "DELETE FROM migration_history WHERE version = ?";
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setString(1, version);
                    deleteStmt.executeUpdate();
                }
                conn.commit();
                logger.info("Executed rollback script: {}", rollbackFileName);
            } else {
                logger.warn("No rollback script found for {}", filename);
            }
        } catch (SQLException | IOException e) {
            conn.rollback();
            throw e;
        }
    }

    public String calculateChecksum(String sql) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sql.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to calculate checksum", e);
            return "error_checksum";
        }
    }
}