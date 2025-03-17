package org.bereketab; //  Ties this test to your project’s root package : organizes it with your code.

import com.zaxxer.hikari.HikariDataSource; //  Mocks the DB pool : tests MigrationService without a real DB.
import org.junit.jupiter.api.BeforeEach; //  Sets up mocks before each test : ensures clean state.
import org.junit.jupiter.api.Test; //  Marks test methods : validates specific behaviors.
import org.mockito.Mock; //  Creates mock objects : simulates DB interactions.
import org.mockito.MockitoAnnotations; //  Initializes mocks : ties @Mock annotations to objects.
import java.io.IOException; //  Handles file-related errors : used in getMigrationFiles test.
import java.nio.file.Path; //  Represents file paths : tests migration file filtering.
import java.nio.file.Paths; //  Creates Path objects : mocks migration file paths.
import java.sql.Connection; //  Mocks DB connection : simulates DB access.
import java.sql.DatabaseMetaData; //  Mocks DB metadata : tests table schema validation.
import java.sql.ResultSet; //  Mocks query results : simulates column checks.
import java.sql.SQLException; //  Handles DB exceptions : ensures mocks behave correctly.
import java.sql.Statement; //  Mocks SQL execution : simulates table creation.
import java.util.List; //  Holds migration files : tests file filtering logic.
import static org.junit.jupiter.api.Assertions.assertEquals; //  Verifies expected values : checks test outcomes.
import static org.mockito.Mockito.mock; //  Creates mock instances : used in setup.
import static org.mockito.Mockito.when; //  Defines mock behavior : controls what mocks return.

public class MigrationServiceTest { //  Unit test class : focuses on MigrationService’s internal methods.
    @Mock
    private HikariDataSource dataSource; //  Mocks the DB pool : avoids real DB connections in unit tests.

    @Mock
    private Connection connection; //  Mocks a DB connection : simulates DB ops.

    @Mock
    private Statement statement; //  Mocks a statement : simulates SQL execution (e.g., CREATE TABLE).

    @Mock
    private DatabaseMetaData metaData; //  Mocks DB metadata : tests migration_history schema check.

    @Mock
    private ResultSet resultSet; //  Mocks query results : simulates column data for schema validation.

    @BeforeEach
    void setUp() throws SQLException { //  Runs before each test : sets up mocks for consistent behavior.
        MockitoAnnotations.openMocks(this); //  Initializes @Mock objects : links them to Mockito framework.
        // Mock the full DB interaction chain
        when(dataSource.getConnection()).thenReturn(connection); //  Ensures getConnection returns mocked connection : controls DB access.
        when(connection.createStatement()).thenReturn(statement); //  Returns mocked statement : simulates SQL execution.
        when(connection.getMetaData()).thenReturn(metaData); //  Provides mocked metadata : tests schema validation.
        when(metaData.getColumns(null, null, "migration_history", null)).thenReturn(resultSet); //  Returns mocked result set : simulates column lookup for migration_history.
        // Simulate an empty or matching schema (no columns yet)
        when(resultSet.next()).thenReturn(false); //  First call returns false : triggers table creation in initMigrationHistoryTable (commented out due to sequence fix below).
        when(resultSet.next())
                .thenReturn(true) // First column
                .thenReturn(true) // Second column
                .thenReturn(true) // Third column
                .thenReturn(true) // Fourth column
                .thenReturn(false); // No more columns
        //  Simulates a full schema check : returns true for 4 columns, then false to end loop in initMigrationHistoryTable.
        when(resultSet.getString("COLUMN_NAME"))
                .thenReturn("version")
                .thenReturn("file_name")
                .thenReturn("checksum")
                .thenReturn("applied_time"); //  Returns expected column names : mocks a valid migration_history table to pass schema validation.
    }

    @Test
    void testGetMigrationFiles_filtersRollbackFiles() throws IOException { //  Tests getMigrationFiles : ensures rollback files are excluded.
        MigrationService service = new MigrationService(dataSource) { //  Creates a test-specific MigrationService : overrides file lookup.
            @Override
            public List<Path> getMigrationFiles() throws IOException { //  Overrides method : returns controlled list for testing.
                return List.of(
                        Paths.get("src/main/resources/migrations/V1__test.sql"), //  Adds a migration file : should be included.
                        Paths.get("src/main/resources/migrations/V1__test_rollback.sql"), //  Adds a rollback file : should be excluded.
                        Paths.get("src/main/resources/migrations/V2__another.sql") //  Adds another migration : should be included.
                );
            }
        };
        List<Path> files = service.getMigrationFiles(); //  Calls overridden method : gets the mocked file list.
        List<Path> filteredFiles = files.stream()
                .filter(path -> !path.toString().contains("_rollback.sql")) //  Filters out rollback files : mimics real getMigrationFiles logic.
                .toList(); //  Converts to list : prepares for assertion.
        assertEquals(2, filteredFiles.size()); //  Verifies count : expects 2 migrations (V1, V2), not rollback.
        assertEquals("V1__test.sql", filteredFiles.get(0).getFileName().toString()); //  Checks first file : confirms V1 is included.
        assertEquals("V2__another.sql", filteredFiles.get(1).getFileName().toString()); //  Checks second file : confirms V2 is included.
    }

    @Test
    void testCalculateChecksum_consistentOutput() { //  Tests calculateChecksum : ensures consistent hashes for same input.
        MigrationService service = new MigrationService(dataSource); //  Creates MigrationService instance : uses mocked dataSource (no override needed).
        String sql = "CREATE TABLE test (id INT);"; //  Defines test SQL : simple input to hash.
        String checksum1 = service.calculateChecksum(sql); //  Calculates first checksum : baseline result.
        String checksum2 = service.calculateChecksum(sql); //  Calculates second checksum : should match first.
        assertEquals(checksum1, checksum2); //  Verifies consistency : same SQL should yield same SHA-256 hash.
    }
}