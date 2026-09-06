package net.flectone.bot.file;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.exception.FileWriteException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class FileWriter {

    public static final long LAST_MODIFIED_TIME = System.currentTimeMillis();

    private final ObjectMapper yamlMapper;
    private final FilePathProvider filePathProvider;

    public void save(FilePack files, boolean onlyMissing) {
        save(filePathProvider.get(files.config()), files.config(), onlyMissing);
        save(filePathProvider.get(files.integration()), files.integration(), onlyMissing);
    }

    public void save(Path pathToFile, Object file) {
        save(pathToFile, file, false);
    }

    private void save(Path pathToFile, Object file, boolean onlyMissing) {
        if (onlyMissing && Files.exists(pathToFile)) return;
        if (pathToFile.toFile().lastModified() == LAST_MODIFIED_TIME) return;

        try {
            Files.createDirectories(pathToFile.getParent());
            Files.writeString(pathToFile, yamlMapper.writeValueAsString(file));

            pathToFile.toFile().setLastModified(LAST_MODIFIED_TIME);
        } catch (IOException e) {
            throw new FileWriteException(pathToFile.getFileName().toString(), e);
        }
    }

}
