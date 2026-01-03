package net.flectone.bot.model.file;

import lombok.With;
import net.flectone.bot.config.Config;
import net.flectone.bot.config.Integration;

@With
public record FilePack(
        Config config,
        Integration integration
) {
}
