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

@Command(name = "validate", description = "Validate applied migrations against files")
public class ValidateCommand implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(ValidateCommand.class);
    private final MigrationService service;

    public ValidateCommand(MigrationService service) {
        this.service = service;
    }

    @Override
    public void run() {
        try {
            for (Path file : service.getMigrationFiles()) {
                if (!validateMigrationFile(file)) {
                    // Stop on first validation failure
                    break;
                }
            }
        } catch (IOException | SQLException e) {

            logger.error("Validation failed", e);
            throw new RuntimeException("Validation failed", e);
        }
    }

    private boolean validateMigrationFile(Path file) throws IOException, SQLException {
        String filename = file.getFileName().toString();
        String version = filename.split("__")[0];
        try (Connection conn = service.dataSource.getConnection()) {
            if (service.isMigrationApplied(conn, version)) {
                return compareChecksums(conn, version, filename, file);
            } else {
                logger.warn("Migration {} in files but not applied", filename);
                return true;
            }
        }
    }

    private boolean compareChecksums(Connection conn, String version, String filename, Path file) throws IOException, SQLException {
        String existingChecksum = service.getExistingChecksum(conn, version);
        String currentChecksum = service.calculateChecksum(Files.readString(file));
        if (!existingChecksum.equals(currentChecksum)) {
            // Log mismatch to alert user of file changes
            logger.error("Validation failed for {}: Checksum mismatch (DB: {}, File: {})",
                    filename, existingChecksum, currentChecksum);
            return false;
        }
        logger.info("Validated: {}", filename);
        return true;
    }
}