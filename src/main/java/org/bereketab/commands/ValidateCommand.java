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
                String filename = file.getFileName().toString();
                String version = filename.split("__")[0];
                try (Connection conn = service.dataSource.getConnection()) {
                    if (service.isMigrationApplied(conn, version)) {
                        String existingChecksum = service.getExistingChecksum(conn, version);
                        String currentChecksum = service.calculateChecksum(Files.readString(file));
                        if (!existingChecksum.equals(currentChecksum)) {
                            logger.error("Validation failed for {}: Checksum mismatch (DB: {}, File: {})",
                                    filename, existingChecksum, currentChecksum);
                            break;
                        }
                        logger.info("Validated: {}", filename);
                    } else {
                        logger.warn("Migration {} in files but not applied", filename);
                    }
                }
            }
        } catch (IOException | SQLException e) {
            logger.error("Validation failed", e);
            throw new RuntimeException("Validation failed", e);
        }
    }
}