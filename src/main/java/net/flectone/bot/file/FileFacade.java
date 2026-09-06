package net.flectone.bot.file;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.BuildConfig;
import net.flectone.bot.config.Config;
import net.flectone.bot.config.Integration;
import org.apache.logging.log4j.Logger;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class FileFacade {

    private final FileLoader fileLoader;
    private final FileWriter fileWriter;
    private final FilePathProvider filePathProvider;
    private final BackupCreator backupCreator;
    private final Logger logger;

    @Getter
    private volatile String previousVersion;
    private volatile FilePack files;

    public void reload() {
        fileLoader.init();

        previousVersion = fileLoader.loadConfig(files).version();

        boolean versionChanged = !previousVersion.equals(BuildConfig.PROJECT_VERSION);
        if (versionChanged) {
            logger.info("Updating files from {} to {}", previousVersion, BuildConfig.PROJECT_VERSION);
            backupCreator.backup(previousVersion, filePathProvider.get(fileLoader.getDefaultFiles().integration()));
        }

        files = fileLoader.loadFiles(files);

        if (versionChanged) {
            files = files.withConfig(files.config().withVersion(BuildConfig.PROJECT_VERSION));
        }

        fileWriter.save(files, false);
    }

    public Config config() {
        return files.config();
    }

    public Integration integration() {
        return files.integration();
    }

    public Integration.Discord discord() {
        return integration().discord();
    }

    public Integration.Discord.Forum forum() {
        return discord().forum();
    }

    public Integration.Telegram telegram() {
        return integration().telegram();
    }

    public Integration.Rag rag() {
        return integration().rag();
    }

}
