package net.flectone.bot.module.telegram;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.module.Bot;
import net.flectone.bot.module.telegram.listener.MessageListener;
import net.flectone.bot.processing.SystemVariableResolver;
import net.flectone.bot.util.file.FileFacade;
import org.apache.logging.log4j.Logger;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class TelegramBot implements Bot {

    private final FileFacade fileFacade;
    private final MessageListener messageListener;
    private final SystemVariableResolver systemVariableResolver;
    private final Logger logger;

    private TelegramBotsLongPollingApplication botsApplication;
    @Getter private OkHttpTelegramClient telegramClient;

    public Integration.Telegram config() {
        return fileFacade.integration().telegram();
    }

    @Override
    public void startup() {
        String token = systemVariableResolver.substituteEnvVars(config().token());
        if (token.isEmpty()) return;

        try {
            telegramClient = new OkHttpTelegramClient(token);

            botsApplication = new TelegramBotsLongPollingApplication();
            botsApplication.registerBot(token, messageListener);

            logger.info("✔ Telegram integration enabled");

        } catch (Exception e) {
            logger.warn(e);
        }
    }

    @Override
    public void shutdown() {
        if (botsApplication == null) return;

        try {
            botsApplication.close();
        } catch (Exception e) {
            logger.warn(e);
        }
    }

    public void executeMethod(BotApiMethod<?> method) {
        try {
            telegramClient.executeAsync(method);
        } catch (TelegramApiException e) {
           logger.warn(e);
        }
    }

}
