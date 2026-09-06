package net.flectone.bot.file;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Config;
import net.flectone.bot.config.Integration;
import net.flectone.bot.config.merger.ConfigMergerImpl;
import net.flectone.bot.config.merger.IntegrationMergerImpl;
import net.flectone.bot.exception.FileLoadException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.MismatchedInputException;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BinaryOperator;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class FileLoader {

    private final FileWriter fileWriter;
    private final ObjectMapper yamlMapper;
    private final @Named("projectPath") Path projectPath;
    private final ConfigMergerImpl configMerger;
    private final IntegrationMergerImpl integrationMerger;

    @Getter
    private FilePack defaultFiles;

    public void init() {
        if (defaultFiles != null) return;

        defaultFiles = new FilePack(
                loadFromResource(FilePath.CONFIG, Config.class),
                loadFromResource(FilePath.INTEGRATION, Integration.class)
        );

        fileWriter.save(defaultFiles, true);
    }

    public FilePack loadFiles(FilePack currentFiles) {
        FilePack files = currentFiles == null ? defaultFiles : currentFiles;

        return new FilePack(loadConfig(files), loadIntegration(files));
    }

    public Config loadConfig(FilePack currentFiles) {
        FilePack files = currentFiles == null ? defaultFiles : currentFiles;

        return loadOrDefault(FilePath.CONFIG, files.config(), (defaults, local) ->
                configMerger.merge(defaults.toBuilder(), local)
        );
    }

    private Integration loadIntegration(FilePack files) {
        return loadOrDefault(FilePath.INTEGRATION, files.integration(), (defaults, local) ->
                integrationMerger.merge(defaults.toBuilder(), local)
        );
    }

    private <T> T loadOrDefault(FilePath filePath, T defaultFile, BinaryOperator<T> merger) {
        Path pathToFile = projectPath.resolve(filePath.getPath());
        if (!Files.exists(pathToFile)) return defaultFile;
        if (pathToFile.toFile().lastModified() == FileWriter.LAST_MODIFIED_TIME) return defaultFile;

        return load(pathToFile, defaultFile)
                .map(local -> merger.apply(defaultFile, local))
                .orElse(defaultFile);
    }

    private <T> T loadFromResource(FilePath filePath, Class<T> type) {
        String path = "config/" + filePath.getPath();

        try (InputStream resource = getClass().getClassLoader().getResourceAsStream(path)) {
            if (resource == null) {
                throw new FileLoadException(path, new IllegalStateException("Resource not found"));
            }

            return yamlMapper.readValue(resource, type);
        } catch (Exception e) {
            throw new FileLoadException(path, e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> load(Path pathToFile, T defaultFile) {
        try {
            return Optional.of((T) yamlMapper.readValue(pathToFile.toFile(), defaultFile.getClass()));
        } catch (Exception e) {
            if (isEmptyFile(e)) {
                fileWriter.save(pathToFile, defaultFile);
                return Optional.empty();
            }

            throw new FileLoadException(pathToFile.toString(), e);
        }
    }

    private boolean isEmptyFile(Exception e) {
        return e instanceof MismatchedInputException mismatched
                && mismatched.getMessage() != null
                && mismatched.getMessage().contains("No content to map due to end-of-input");
    }

}
