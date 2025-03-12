package org.bereketab.commands;

import org.bereketab.MigrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Command(name = "rollback", description = "Rollback the last applied migration")
public class RollbackCommand implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(RollbackCommand.class);
    private final MigrationService service;

    public RollbackCommand(MigrationService service) {
        this.service = service;
    }

    @Override
    public void run() {
        try (Connection conn = service.dataSource.getConnection()) {
            String sql = "SELECT version, file_name FROM migration_history ORDER BY applied_time DESC LIMIT 1";
            String version;
            String filename;
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                if (!rs.next()) {
                    logger.info("No migrations to rollback");
                    return;
                }
                version = rs.getString("version");
                filename = rs.getString("file_name");
            }
            logger.info("Rolling back: {}", filename);
            service.rollbackMigration(conn, version, filename);
        } catch (SQLException | IOException e) {
            logger.error("Rollback failed", e);
            throw new RuntimeException("Rollback failed", e);
        }
    }
}