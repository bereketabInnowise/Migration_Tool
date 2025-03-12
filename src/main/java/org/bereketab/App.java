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