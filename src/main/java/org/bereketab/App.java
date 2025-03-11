//package org.bereketab;
//
//import com.zaxxer.hikari.HikariDataSource;
//import org.bereketab.migrationLibrary.DatabaseConfig;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import picocli.CommandLine;
//import picocli.CommandLine.Command;
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
//@Command(name = "Migration Tool", mixinStandardHelpOptions = true, version = "1.0", description = "A simple database migration tool")
//public class App implements Runnable{
//    private static final Logger logger = LoggerFactory.getLogger(App.class);
//    private final HikariDataSource dataSource;
//    private final String migrationsDir = "migrations";
//
//    public App() {
//        this.dataSource = DatabaseConfig.getDataSource();
//    }
//    @Override
//    public void run(){
//        try{
//            runMigrations();
//        } catch (SQLException | IOException e){
//            logger.error("Migration process failed: ", e);
//            throw new RuntimeException("Failed to run migrations: ", e);
//        }
//
//    }
//
//    public void runMigrations() throws IOException, SQLException {
//        logger.info("Starting migration process...");
//        Path dir = Paths.get("src/main/resources", migrationsDir);
//        List<Path> migrationFiles = Files.list(dir)
//                .filter(path -> path.toString().endsWith(".sql"))
//                .sorted()
//                .toList();
//
//        try (Connection conn = dataSource.getConnection()) {
//            for (Path file : migrationFiles) {
//                String filename = file.getFileName().toString();
//                String version = filename.split("__")[0];
//
//                if (isMigrationApplied(conn, version)) {
//                    String existingChecksum = getExistingChecksum(conn, version);
//                    String currentChecksum = calculateChecksum(Files.readString(file));
//                    if(!existingChecksum.equals(currentChecksum)){
//                        logger.warn("Migration {} has changed since the last application! Existing Checksum: {}, Current Checksum: {}", filename, existingChecksum, currentChecksum);
//                    }
//                    logger.info("Skipping already applied migration: {}", filename);
//                    continue;
//                }
//
//                try (Connection migrationConn = dataSource.getConnection()) {
//                    migrationConn.setAutoCommit(false);
//                    try {
//                        String sql = Files.readString(file);
//                        String checksum = calculateChecksum(sql);
//                        logger.info("Applying migration: {} (checksum: {})", filename, checksum);
//                        executeMigration(migrationConn, sql);
//                        recordMigration(migrationConn, version, filename, checksum);
//                        migrationConn.commit();
//                    } catch (SQLException e) {
//                        migrationConn.rollback();
//                        logger.error("Migration failed, rolling back: {}", filename, e);
//                        throw e;
//                    }
//                }
//            }
//        }
//    }
//
//    private boolean isMigrationApplied(Connection conn, String version) throws SQLException {
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
//    private void executeMigration(Connection conn, String sql) throws SQLException {
//        try (Statement stmt = conn.createStatement()) {
//            stmt.execute(sql);
//        }
//    }
//
//    private void recordMigration(Connection conn, String version, String filename, String checksum) throws SQLException {
//        String insertSql = "INSERT INTO migration_history (version, file_name, checksum) VALUES (?, ?, ?)";
//        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
//            insertStmt.setString(1, version);
//            insertStmt.setString(2, filename);
//            insertStmt.setString(3, checksum);
//            insertStmt.executeUpdate();
//        }
//    }
//    private String getExistingChecksum(Connection conn, String version) throws SQLException{
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
//    private String calculateChecksum(String sql) {
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
//    public static void main(String[] args) throws IOException, SQLException {
//        int exitCode = new CommandLine(new App()).execute(args);
//        System.exit(exitCode);
//    }
//}
package org.bereketab;

import org.bereketab.commands.MigrateCommand;
import org.bereketab.commands.RollbackCommand;
import org.bereketab.commands.StatusCommand;
import org.bereketab.commands.ValidateCommand;
import org.bereketab.migrationLibrary.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "migration-tool", mixinStandardHelpOptions = true, version = "1.0",
        description = "A simple database migration tool")
public class App {
    public static void main(String[] args) {
        MigrationService migrationService = new MigrationService(DatabaseConfig.getDataSource());
        int exitCode = new CommandLine(new App())
                .addSubcommand("migrate", new MigrateCommand(migrationService))
                .addSubcommand("status", new StatusCommand(migrationService))
                .addSubcommand("rollback", new RollbackCommand(migrationService))
                .addSubcommand("validate", new ValidateCommand(migrationService))
                .execute(args);
        System.exit(exitCode);
    }
}