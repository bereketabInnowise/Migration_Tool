package org.bereketab.commands;

import org.bereketab.MigrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

@Command(name = "migrate", description = "Apply pending migrations")
public class MigrateCommand implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(MigrateCommand.class);
    private final MigrationService service;

    public MigrateCommand(MigrationService service) {
        this.service = service;
    }

    @Override
    public void run() {
        try {
            logger.info("Starting migration process...");
            for (Path file : service.getMigrationFiles()) {
                processMigrationFile(file);
            }
        } catch (IOException | SQLException e) {
            logger.error("Migration failed", e);
            throw new RuntimeException("Migration failed", e);
        }
    }

    private void processMigrationFile(Path file) throws IOException, SQLException {
        String filename = file.getFileName().toString();
        String version = filename.split("__")[0];
        try (Connection conn = service.dataSource.getConnection()) {
            if (checkExistingMigration(conn, version, filename, file)) {
                logger.info("Skipping: {}", filename);
                return;
            }
            String sql = Files.readString(file);
            String checksum = service.calculateChecksum(sql);
            logger.info("Applying: {} (checksum: {})", filename, checksum);
            service.applyMigration(conn, version, filename, sql);
        }
    }

    private boolean checkExistingMigration(Connection conn, String version, String filename, Path file) throws IOException, SQLException {
        if (!service.isMigrationApplied(conn, version)) {
            return false;
        }
        String existingChecksum = service.getExistingChecksum(conn, version);
        String currentChecksum = service.calculateChecksum(Files.readString(file));
        if (!existingChecksum.equals(currentChecksum)) {
            // Warn if file changed since last applied
            logger.warn("Migration {} has changed! Existing: {}, Current: {}", filename, existingChecksum, currentChecksum);
        }
        return true;
    }
}