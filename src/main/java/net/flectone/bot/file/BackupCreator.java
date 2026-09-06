package net.flectone.bot.file;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class BackupCreator {

    private final @Named("backupPath") Path backupPath;
    private final Logger logger;

    public void backup(String version, Path pathToFile) {
        if (!Files.exists(pathToFile)) return;

        Path target = backupPath.resolve(version).resolve(pathToFile.getFileName().toString());

        try {
            Files.createDirectories(target.getParent());
            Files.copy(pathToFile, target, StandardCopyOption.REPLACE_EXISTING);

            logger.info("Backed up {} to {}", pathToFile.getFileName(), target);
        } catch (IOException e) {
            logger.warn("Failed to back up {}", pathToFile.getFileName(), e);
        }
    }

}
