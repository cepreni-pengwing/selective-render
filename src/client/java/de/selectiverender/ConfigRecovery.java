package de.selectiverender;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

final class ConfigRecovery {
    private ConfigRecovery() { }

    static <T> Result<T> load(Path primary, Function<Path, T> reader) {
        boolean primaryExists = Files.isRegularFile(primary);
        T value = reader.apply(primary);
        if (value != null) return new Result<>(value, primaryExists, false);

        Path backup = backupPath(primary);
        value = reader.apply(backup);
        return new Result<>(value, primaryExists, value != null);
    }

    static Path backupPath(Path path) {
        return path.resolveSibling(path.getFileName() + ".bak");
    }

    record Result<T>(T value, boolean primaryExisted, boolean recoveredFromBackup) { }
}
