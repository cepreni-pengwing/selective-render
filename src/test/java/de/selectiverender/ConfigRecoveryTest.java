package de.selectiverender;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigRecoveryTest {
    @TempDir Path directory;

    @Test
    void fallsBackToBackupWhenThePrimaryCannotBeRead() throws IOException {
        Path primary = directory.resolve("world.json");
        Files.writeString(primary, "broken");
        Files.writeString(ConfigRecovery.backupPath(primary), "backup");

        ConfigRecovery.Result<String> result = ConfigRecovery.load(primary, path -> {
            try {
                String value = Files.readString(path);
                return "broken".equals(value) ? null : value;
            } catch (IOException ignored) {
                return null;
            }
        });

        assertEquals("backup", result.value());
        assertTrue(result.primaryExisted());
        assertTrue(result.recoveredFromBackup());
    }
}
