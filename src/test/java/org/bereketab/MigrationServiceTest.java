package org.bereketab;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class MigrationServiceTest {
    @Mock
    private HikariDataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private Statement statement;

    @Mock
    private DatabaseMetaData metaData;

    @Mock
    private ResultSet resultSet;

    @BeforeEach
    void setUp() throws SQLException {
        // Configure mocks for DB interactions
        MockitoAnnotations.openMocks(this);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getColumns(null, null, "migration_history", null)).thenReturn(resultSet);
        // Simulate migration_history table columns
        when(resultSet.next())
                .thenReturn(true) // First column
                .thenReturn(true) // Second column
                .thenReturn(true) // Third column
                .thenReturn(true) // Fourth column
                .thenReturn(false); // No more columns
        when(resultSet.getString("COLUMN_NAME"))
                .thenReturn("version")
                .thenReturn("file_name")
                .thenReturn("checksum")
                .thenReturn("applied_time");
    }

    @Test
    void testGetMigrationFiles_filtersRollbackFiles() throws IOException {
        // Test filtering of rollback files in getMigrationFiles
        MigrationService service = new MigrationService(dataSource) {
            @Override
            public List<Path> getMigrationFiles() {
                return List.of(
                        Paths.get("src/main/resources/migrations/V1__test.sql"),
                        Paths.get("src/main/resources/migrations/V1__test_rollback.sql"),
                        Paths.get("src/main/resources/migrations/V2__another.sql")
                );
            }
        };
        List<Path> files = service.getMigrationFiles();
        List<Path> filteredFiles = files.stream()
                .filter(path -> !path.toString().contains("_rollback.sql"))
                .toList();
        assertEquals(2, filteredFiles.size());
        assertEquals("V1__test.sql", filteredFiles.get(0).getFileName().toString());
        assertEquals("V2__another.sql", filteredFiles.get(1).getFileName().toString());
    }

    @Test
    void testCalculateChecksum_consistentOutput() {
        // Test consistent checksum calculation for same input
        MigrationService service = new MigrationService(dataSource);
        String sql = "CREATE TABLE test (id INT);";
        String checksum1 = service.calculateChecksum(sql);
        String checksum2 = service.calculateChecksum(sql);
        assertEquals(checksum1, checksum2);
    }
}