# Migration Tool

A lightweight, flexible database migration tool designed for both standalone command-line use and seamless integration into Java applications. Inspired by Flyway, it supports migrations for any database with a JDBC driver, offering a simple way to manage schema changes.

---

## Features

- **Standalone CLI**: Run migrations, check status, rollback, or validate with a single executable JAR.
- **App Integration**: Embed into Spring Boot or any Java project—runs migrations automatically on startup.
- **Database Agnostic**: Works with PostgreSQL, MySQL, SQLite, or any JDBC-supported DB.
- **Commands**: Supports `migrate`, `status`, `rollback`, and `validate`.
- **Simple Configuration**: External `migration.conf` for CLI, standard `application.properties` for apps.

---

## Requirements

- **Java**: 21.
- **Maven**: For building from source or integrating as a dependency.
- **Database**: Any JDBC-compatible database (e.g., PostgreSQL, MySQL).
- **Dependencies**: Included in the standalone JAR (HikariCP, PicoCLI, and others).

---

## Installation

### For Standalone Use

1. **Download**: Get `migration-tool.zip` from https://github.com/bereketabInnowise/migration-tool.zip.
2. **Unzip**: Extract to a directory (e.g., `migration-tool/`).
3. **Configure**: Edit `migration.conf` with your database details:
   ```plaintext
   # Migration Tool Configuration
   db.url=jdbc:postgresql://localhost:5432/yourdb
   db.username=youruser
   db.password=yourpassword
   db.driver=database_driver
   ```
4. **Add Migrations**: Place SQL migration files `migrations/` (e.g., `V1__create_schema.sql`).

### For Project Integration

1. **Install Locally**: After building from this repo or obtaining `Migration_Tool-1.0-SNAPSHOT.jar`:
   ```bash
   mvn install:install-file -Dfile=Migration_Tool-1.0-SNAPSHOT.jar -DgroupId=org.bereketab -DartifactId=migration-tool -Dversion=1.0-SNAPSHOT -Dpackaging=jar
   ```
2. **Add Dependency**: In your project’s `pom.xml`:
   ```xml
   <dependency>
       <groupId>org.bereketab.migrationLibrary</groupId>
       <artifactId>Migration_Tool</artifactId>
       <version>1.0-SNAPSHOT</version>
   </dependency>
   ```

---

## Usage

### Standalone CLI

Run commands from the directory containing `migration-tool.jar` and `migration.conf`.

- **Migrate**: Apply pending migrations.
  ```bash
  java -jar migration-tool.jar migrate --migrations-dir=/path/to/migrations
  ```
- **Status**: View applied and pending migrations.
  ```bash
  java -jar migration-tool.jar status --migrations-dir=/path/to/migrations
  ```
- **Rollback**: Undo the last applied migration.
  ```bash
  java -jar migration-tool.jar rollback --migrations-dir=/path/to/migrations
  ```
- **Validate**: Check if applied migrations match the files.
  ```bash
  java -jar migration-tool.jar validate --migrations-dir=/path/to/migrations
  ```
- **Help**: See all options.
  ```bash
  java -jar migration-tool.jar --help
  ```

**Notes**:

- `--migrations-dir` defaults to `./migrations/` if not specified.
- `migration.conf` must be in the working directory (where you run `java -jar`).

### Integration in a Project

Embed migrations in your app (e.g., Spring Boot) using your own DataSource.

#### Spring Boot Example

- **Configure**: In `src/main/resources/application.properties`:
  ```properties
  spring.datasource.url=jdbc:postgresql://localhost:5432/yourdb
  spring.datasource.username=youruser
  spring.datasource.password=yourpassword
  spring.datasource.driver-class-name=org.postgresql.Driver(any db driver)
  spring.jpa.hibernate.ddl-auto=none
  ```
- **Integrate**: In your main class (e.g., `Application.java`):
  ```java
  import org.bereketab.MigrationService;
  import org.bereketab.commands.MigrateCommand;
  import org.springframework.beans.factory.annotation.Value;
  import org.springframework.boot.CommandLineRunner;
  import org.springframework.boot.SpringApplication;
  import org.springframework.boot.autoconfigure.SpringBootApplication;
  import com.zaxxer.hikari.HikariConfig;
  import com.zaxxer.hikari.HikariDataSource;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;

  @SpringBootApplication
  public class Application implements CommandLineRunner {
      private static final Logger logger = LoggerFactory.getLogger(Application.class);

      @Value("${spring.datasource.url}") private String jdbcUrl;
      @Value("${spring.datasource.username}") private String username;
      @Value("${spring.datasource.password}") private String password;
      @Value("${spring.datasource.driver-class-name}") private String driverClassName;

      public static void main(String[] args) {
          SpringApplication.run(Application.class, args);
      }

      @Override
      public void run(String... args) throws Exception {
          logger.info("Running migrations with Migration Tool");
          HikariConfig config = new HikariConfig();
          config.setJdbcUrl(jdbcUrl);
          config.setUsername(username);
          config.setPassword(password);
          config.setDriverClassName(driverClassName);
          config.setMaximumPoolSize(10);
          HikariDataSource ds = new HikariDataSource(config);

          MigrationService migrationService = new MigrationService(ds);
          migrationService.setMigrationsDir("src/main/resources/migrations");
          new MigrateCommand(migrationService).run();
          logger.info("Migrations completed");
      }
  }
  ```
- **Add Migrations**: Place SQL files in `src/main/resources/migrations/` (e.g., `V1__create_table.sql`).

**Notes**:

- Runs migrate on startup—mimics Flyway’s auto-migration.
- Uses your app’s DataSource by ignoring `migration.conf`.

---
## How It Works
- **Core**: `MigrationService` reads SQL files, tracks history in `migration_history`, and applies changes transactionally and in version order.
- **CLI**: PicoCLI parses commands—config loaded from `migration.conf`.
- **Checksums**: SHA-256 ensures migration integrity—validated on `validate`.
- **Migrations**: Named `V<version>__description.sql` (e.g., `V1__create_schema.sql`).  ; tracked in a `migration_history` table.
- **Rollback**: Looks for `V<version>__description_rollback.sql`—executes if found. Removes the last migration’s entry and assumes the SQL has a DOWN section (manual rollback logic needed).
- **History**: Stored in `migration_history` (version, file_name, checksum, applied_time).

---
### Configuration

- **Standalone**: Reads `migration.conf` from the working directory at runtime.
- **Integrated**: Uses app-provided DataSource (e.g., Spring’s HikariCP setup).


## Sample Migration
`migrations/V1__create_schema.sql` includes:

- **OneToOne**: users ↔ profiles.
- **OneToMany**: users → orders.
- **ManyToMany**: students ↔ courses via `student_courses`.
- **Indexes**: On `orders(user_id)` and `profiles(user_id)`.
- **Data**: 190 rows across tables.

---

## Building from Source
- **Clone**: `git clone https://github.com/bereketabInnowise/Migration_Tool.git`.
- **Build**:

```bash
cd Migration_Tool
mvn clean install
```

- **Output**:
  - `target/migration-tool.jar`: Standalone CLI JAR.
  - `target/Migration_Tool-1.0-SNAPSHOT.jar`: Dependency JAR.

---

## Troubleshooting
- **"Cannot load migration.conf"**: Ensure it’s in the same directory as `migration-tool.jar`.
- **"No migrations found"**: Check `--migrations-dir` points to a folder with `.sql` files.
- **DB Errors**: Verify `db.url`, `db.username`, `db.password`, and `db.driver` in `migration.conf`.

---

## Author
Bereketab: bereketab.shanka@innowise.com questions or feedback welcome!

Happy migrating!

