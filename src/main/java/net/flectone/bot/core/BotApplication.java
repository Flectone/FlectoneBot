package net.flectone.bot.core;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.discord.DiscordBot;
import net.flectone.bot.file.FileFacade;
import net.flectone.bot.rag.RagBot;
import net.flectone.bot.telegram.TelegramBot;
import org.apache.logging.log4j.Logger;

import java.util.List;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class BotApplication {

    private final FileFacade fileFacade;
    private final RagBot ragBot;
    private final DiscordBot discordBot;
    private final TelegramBot telegramBot;
    private final Logger logger;

    public void start() {
        fileFacade.reload();

        bots().forEach(bot -> {
            try {
                bot.startup();
                logger.info("{} is {}", bot.name(), bot.isEnabled() ? "enabled" : "disabled");
            } catch (Exception e) {
                logger.error("{} failed to start", bot.name(), e);
            }
        });
    }

    public void stop() {
        bots().reversed().forEach(bot -> {
            try {
                bot.shutdown();
            } catch (Exception e) {
                logger.error("{} failed to stop", bot.name(), e);
            }
        });
    }

    private List<Bot> bots() {
        return List.of(ragBot, discordBot, telegramBot);
    }

}
