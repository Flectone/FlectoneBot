package net.flectone.bot.util.file;


import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.BuildConfig;
import net.flectone.bot.config.Config;
import net.flectone.bot.config.Integration;
import net.flectone.bot.model.file.FilePack;
import net.flectone.bot.util.comparator.VersionComparator;
import net.flectone.bot.util.creator.BackupCreator;

import java.io.IOException;
import java.util.function.UnaryOperator;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class FileFacade {

    private final FileLoader fileLoader;
    private final FileWriter fileWriter;
    private final FileMigrator fileMigrator;
    private final FilePathProvider filePathProvider;
    private final BackupCreator backupCreator;
    private final VersionComparator versionComparator;

    @Getter
    private String preInitVersion;
    private FilePack files;


    public void reload() throws IOException {
        fileLoader.init();

        // this is to check FlectonePulse version
        // mb in the future we should put version in a separate file, but I think it's not so important
        preInitVersion = fileLoader.loadAndMergeConfig(files).version();
        boolean versionChanged = !preInitVersion.equals(BuildConfig.PROJECT_VERSION);

        // backup if version changed
        if (versionChanged) {
            backupFiles(preInitVersion);
        }

        // load local files
        updateFiles();

        // migrate if version changed
        if (versionChanged) {
            migrateFiles(preInitVersion);
        }

        saveFiles();

        // fix migration problems
        if (versionChanged) {
            updateFiles();
        }
    }

    public Config config() {
        return files.config();
    }

    public Integration integration() {
        return files.integration();
    }

    public void saveFiles() {
        fileWriter.save(files, false);
    }

    public void updateFiles() {
        files = fileLoader.loadFiles(files);
    }

    public void updateFilePack(UnaryOperator<FilePack> filePackOperator) {
        files = filePackOperator.apply(files);
    }

    private void backupFiles(String preInitVersion) {
        backupCreator.setPreInitVersion(preInitVersion);

        FilePack defaultFiles = fileLoader.getDefaultFiles();

        // we can't backup config.yml because it has already been reloaded
        backupCreator.backup(filePathProvider.get(defaultFiles.integration()));
    }

    private void migrateFiles(String preInitVersion) {
        files = files.withConfig(files.config().withVersion(BuildConfig.PROJECT_VERSION));
    }
}
