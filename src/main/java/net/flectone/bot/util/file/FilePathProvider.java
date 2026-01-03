package net.flectone.bot.util.file;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Config;
import net.flectone.bot.config.Integration;
import net.flectone.bot.util.constant.FilePath;

import java.nio.file.Path;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class FilePathProvider {

    private final @Named("projectPath") Path projectPath;

    public Path get(Object file) {
        return switch (file) {
            case Config ignored -> projectPath.resolve(FilePath.CONFIG.getPath());
            case Integration ignored -> projectPath.resolve(FilePath.INTEGRATION.getPath());
            default -> throw new IllegalArgumentException("Incorrect file format: " + file);
        };
    }

}
