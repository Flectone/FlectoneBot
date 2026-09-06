package net.flectone.bot;

import com.alessiodp.libby.LibraryManager;
import com.alessiodp.libby.StandaloneLibraryManager;
import com.google.inject.Guice;
import com.google.inject.Injector;
import net.flectone.bot.core.BotApplication;
import net.flectone.bot.platform.adapter.LoggerAdapter;
import net.flectone.bot.platform.resolver.LibraryResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

public final class FlectoneBot {

    private FlectoneBot() {
    }

    public static void main(String[] args) {
        Logger logger = LogManager.getLogger(FlectoneBot.class);
        Path projectPath = Paths.get(System.getProperty("user.dir")).resolve(BuildConfig.PROJECT_NAME);

        logger.info("Starting {} {}", BuildConfig.PROJECT_NAME, BuildConfig.PROJECT_VERSION);

        LoggerAdapter loggerAdapter = new LoggerAdapter(logger);
        LibraryManager libraryManager = new StandaloneLibraryManager(loggerAdapter, projectPath, "libraries");

        LibraryResolver libraryResolver = new LibraryResolver(libraryManager);
        libraryResolver.addLibraries();
        libraryResolver.resolveRepositories();
        libraryResolver.loadLibraries();

        Injector injector = Guice.createInjector(new BotModule(logger, projectPath, loggerAdapter, libraryManager, libraryResolver));
        BotApplication application = injector.getInstance(BotApplication.class);

        CountDownLatch running = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down...");
            application.stop();
            running.countDown();
        }, "flectonebot-shutdown"));

        try {
            application.start();
            running.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Startup failed", e);
            application.stop();
        }

        logger.info("Shutdown completed");
    }

}
