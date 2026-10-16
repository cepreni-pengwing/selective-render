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
    void validationFailureAlsoRecoversTheBackup() throws IOException {
        Path primary = directory.resolve("invalid-bounds.json");
        Files.writeString(primary, "syntactically valid but invalid region");
        Files.writeString(ConfigRecovery.backupPath(primary), "valid region");
        var result = ConfigRecovery.load(primary, path -> {
            if (path.equals(primary)) throw new IllegalArgumentException("Region bounds are not normalized");
            return "valid region";
        });
        assertTrue(result.recoveredFromBackup());
        assertEquals("valid region", result.value());
        assertEquals("valid region", Files.readString(ConfigRecovery.backupPath(primary)));
    }

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
