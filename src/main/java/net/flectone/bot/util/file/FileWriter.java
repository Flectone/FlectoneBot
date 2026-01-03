package net.flectone.bot.util.file;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.exception.FileWriteException;
import net.flectone.bot.model.file.FilePack;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class FileWriter {

    public static final long LAST_MODIFIED_TIME = System.currentTimeMillis();

    private final ObjectMapper yamlMapper;
    private final FilePathProvider filePathProvider;

    public void save(FilePack files, boolean checkExist) {
        Path configPath = filePathProvider.get(files.config());
        if (!checkExist || !Files.exists(configPath)) {
            save(configPath, files.config());
        }

        Path integrationPath = filePathProvider.get(files.integration());
        if (!checkExist || !Files.exists(integrationPath)) {
            save(integrationPath, files.integration());
        }
    }

    public void save(Path pathToFile, Object fileResource) {
        if (pathToFile.toFile().lastModified() == LAST_MODIFIED_TIME) return;

        try {
            String yaml = yamlMapper.writeValueAsString(fileResource);

            Files.createDirectories(pathToFile.getParent());
            Files.writeString(pathToFile, yaml);
            pathToFile.toFile().setLastModified(LAST_MODIFIED_TIME);
        } catch (IOException e) {
            throw new FileWriteException(pathToFile.toFile().getName(), e);
        }
    }

}
