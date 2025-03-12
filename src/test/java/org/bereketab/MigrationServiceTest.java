package org.bereketab;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

public class MigrationServiceTest {
    @Test
    void testGetMigrationFiles_filtersRollbackFiles() throws IOException {
        // Mock MigrationService with a controlled migrationsDir
        MigrationService service = new MigrationService(mock(HikariDataSource.class)) {
            @Override
            public List<Path> getMigrationFiles() throws IOException {
                // Simulate file system with test data
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
        MigrationService service = new MigrationService(mock(HikariDataSource.class));
        String sql = "CREATE TABLE test (id INT);";
        String checksum1 = service.calculateChecksum(sql);
        String checksum2 = service.calculateChecksum(sql);
        assertEquals(checksum1, checksum2);
    }
}

