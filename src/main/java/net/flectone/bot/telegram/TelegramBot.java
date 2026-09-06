package net.flectone.bot.telegram;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.core.Bot;
import net.flectone.bot.file.FileFacade;
import net.flectone.bot.util.SystemVariableResolver;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class TelegramBot implements Bot {

    private final FileFacade fileFacade;
    private final SystemVariableResolver systemVariableResolver;
    private final Injector injector;
    private final Logger logger;

    private @Nullable TelegramBotsLongPollingApplication application;
    @Getter private @Nullable OkHttpTelegramClient client;

    @Override
    public String name() {
        return "Telegram";
    }

    @Override
    public void startup() {
        String token = systemVariableResolver.substitute(config().token());
        if (StringUtils.isEmpty(token)) {
            logger.info("Telegram token is missing, the bridge stays off");
            return;
        }

        try {
            client = new OkHttpTelegramClient(token);

            application = new TelegramBotsLongPollingApplication();
            application.registerBot(token, injector.getInstance(TelegramListener.class));
        } catch (Exception e) {
            logger.error("Telegram login failed", e);
            shutdown();
        }
    }

    @Override
    public void shutdown() {
        client = null;

        if (application == null) return;

        try {
            application.close();
        } catch (Exception e) {
            logger.warn("Telegram shutdown failed", e);
        }

        application = null;
    }

    @Override
    public boolean isEnabled() {
        return application != null && application.isRunning() && client != null;
    }

    public void execute(BotApiMethod<?> method) {
        if (!isEnabled()) return;

        try {
            client.executeAsync(method).exceptionally(throwable -> {
                logger.warn("Telegram request failed", throwable);
                return null;
            });
        } catch (Exception e) {
            logger.warn("Telegram request failed", e);
        }
    }

    public Integration.Telegram config() {
        return fileFacade.telegram();
    }

}
