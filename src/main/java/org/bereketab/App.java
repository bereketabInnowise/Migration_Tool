package org.bereketab;

import org.bereketab.commands.MigrateCommand;
import org.bereketab.commands.RollbackCommand;
import org.bereketab.commands.StatusCommand;
import org.bereketab.commands.ValidateCommand;
import org.bereketab.migrationLibrary.DatabaseConfig;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "migration-tool", mixinStandardHelpOptions = true, version = "1.0",
        description = "A simple database migration tool")
public class App implements Runnable {
    @Option(names = {"-m", "--migrations-dir"}, description = "Directory containing migration files", defaultValue = "migrations")
    private String migrationsDir;

    public static void main(String[] args) {
        App app = new App();
        MigrationService migrationService = new MigrationService(DatabaseConfig.getDataSource());
        CommandLine cmd = new CommandLine(app)
                .addSubcommand("migrate", new MigrateCommand(migrationService))
                .addSubcommand("status", new StatusCommand(migrationService))
                .addSubcommand("rollback", new RollbackCommand(migrationService))
                .addSubcommand("validate", new ValidateCommand(migrationService));
        int exitCode = cmd.execute(args);
        if (cmd.getParseResult().subcommand() != null) {
            // Set migrations dir only for valid subcommands
            migrationService.setMigrationsDir(app.migrationsDir);
        }
        System.exit(exitCode);
    }

    @Override
    public void run() {
        System.out.println("Use a subcommand: migrate, status, rollback, validate");
    }
}