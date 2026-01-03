package net.flectone.bot;

import com.alessiodp.libby.LibraryManager;
import com.alessiodp.libby.StandaloneLibraryManager;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.flectone.bot.module.discord.DiscordBot;
import net.flectone.bot.module.telegram.TelegramBot;
import net.flectone.bot.platform.adapter.LoggerAdapter;
import net.flectone.bot.platform.resolver.LibraryResolver;
import net.flectone.bot.util.file.FileFacade;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class FlectoneBot {

    private final Injector injector;

    public static void main(String[] args) {
        org.apache.logging.log4j.Logger logger = LogManager.getLogger(FlectoneBot.class);
        logger.info("Enabling...");

        Path projectPath = Paths.get(System.getProperty("user.dir")).resolve("FlectoneBot");

        LoggerAdapter loggerAdapter = new LoggerAdapter(logger);
        LibraryManager libraryManager = new StandaloneLibraryManager(loggerAdapter, projectPath, "libraries");
        LibraryResolver libraryResolver = new LibraryResolver(libraryManager);
        libraryResolver.addLibraries();
        libraryResolver.resolveRepositories();
        libraryResolver.loadLibraries();

        Injector injector = Guice.createInjector(new FlectoneInjector(logger, projectPath, loggerAdapter, libraryManager, libraryResolver));
        FlectoneBot flectoneBot = new FlectoneBot(injector);
        flectoneBot.start();
    }

    @SneakyThrows
    public void start() {
        Logger logger = injector.getInstance(Logger.class);
        logger.info("Starting...");

        FileFacade fileFacade = injector.getInstance(FileFacade.class);
        fileFacade.reload();

        DiscordBot discordBot = injector.getInstance(DiscordBot.class);
        discordBot.startup();

        TelegramBot telegramBot = injector.getInstance(TelegramBot.class);
        telegramBot.startup();

        CountDownLatch latch = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down...");
            discordBot.shutdown();
            telegramBot.shutdown();
        }));

        latch.await();

        logger.info("Shutdown completed");
    }
}
