package net.flectone.bot.telegram;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.file.FileFacade;
import net.flectone.bot.util.Placeholders;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class TelegramSender {

    private static final String TOPIC_SEPARATOR = "_";

    private final FileFacade fileFacade;
    private final TelegramBot telegramBot;
    private final Logger logger;

    public void send(String chat, Placeholders placeholders) {
        Integration.Telegram config = fileFacade.telegram();

        String text = placeholders.apply(config.message());
        if (StringUtils.isEmpty(text)) return;

        SendMessage.SendMessageBuilder<?, ?> builder = SendMessage.builder()
                .chatId(StringUtils.substringBefore(chat, TOPIC_SEPARATOR))
                .text(text);

        topicId(chat).ifPresent(builder::messageThreadId);

        SendMessage message = builder.build();

        switch (config.parseMode()) {
            case MARKDOWN -> message.enableMarkdown(true);
            case MARKDOWN_V2 -> message.enableMarkdownV2(true);
            case HTML -> message.enableHtml(true);
            case NONE -> message.setParseMode(null);
        }

        telegramBot.execute(message);
    }

    private java.util.Optional<Integer> topicId(String chat) {
        String topic = StringUtils.substringAfter(chat, TOPIC_SEPARATOR);
        if (topic.isEmpty()) return java.util.Optional.empty();

        try {
            return java.util.Optional.of(Integer.parseInt(topic));
        } catch (NumberFormatException e) {
            logger.warn("Telegram chat {} has no valid topic id", chat);
            return java.util.Optional.empty();
        }
    }

}
