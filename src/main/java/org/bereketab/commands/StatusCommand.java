package org.bereketab.commands;

import org.bereketab.MigrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Command(name = "status", description = "Show applied migrations")
public class StatusCommand implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(StatusCommand.class);
    private final MigrationService service;

    public StatusCommand(MigrationService service) {
        this.service = service;
    }

    @Override
    public void run() {
        try (Connection conn = service.dataSource.getConnection()) {
            String sql = "SELECT version, file_name, checksum, applied_time FROM migration_history ORDER BY applied_time";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                logger.info("Applied Migrations:");
                while (rs.next()) {
                    logger.info("Version: {}, File: {}, Checksum: {}, Applied: {}",
                            rs.getString("version"), rs.getString("file_name"),
                            rs.getString("checksum"), rs.getTimestamp("applied_time"));
                }
            }
        } catch (SQLException e) {
            logger.error("Status check failed", e);
            throw new RuntimeException("Failed to get status", e);
        }
    }
}