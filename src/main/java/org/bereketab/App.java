package org.bereketab;

import org.bereketab.commands.MigrateCommand;
import org.bereketab.commands.RollbackCommand;
import org.bereketab.commands.StatusCommand;
import org.bereketab.commands.ValidateCommand;
import org.bereketab.migrationLibrary.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "migration-tool", mixinStandardHelpOptions = true, version = "1.0",
        description = "A simple database migration tool")
public class App {
    @Option(names = {"-m", "--migrations-dir"}, description = "Directory containing migration files", defaultValue = "migrations")
    private String migrationsDir;
    public static void main(String[] args) {
        MigrationService migrationService = new MigrationService(DatabaseConfig.getDataSource());
        App app = new App();
        CommandLine cmd = new CommandLine(app)
                .addSubcommand("migrate", new MigrateCommand(migrationService))
                .addSubcommand("status", new StatusCommand(migrationService))
                .addSubcommand("rollback", new RollbackCommand(migrationService))
                .addSubcommand("validate", new ValidateCommand(migrationService));
        int exitCode = cmd.execute(args);
        if(cmd.getParseResult().subcommand() != null){
            migrationService.setMigrationsDir(app.migrationsDir);
        }
        System.exit(exitCode);
    }
}