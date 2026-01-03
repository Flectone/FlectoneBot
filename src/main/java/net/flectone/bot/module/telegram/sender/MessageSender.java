package net.flectone.bot.module.telegram.sender;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.module.telegram.TelegramBot;
import net.flectone.bot.util.file.FileFacade;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.util.function.UnaryOperator;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class MessageSender {

    private final FileFacade fileFacade;
    private final TelegramBot telegramBot;

    public void sendMessage(String chat, UnaryOperator<String> telegramString) {
        String message = fileFacade.integration().telegram().message();
        if (StringUtils.isEmpty(message)) return;

        message = telegramString.apply(message);
        if (StringUtils.isEmpty(message)) return;

        SendMessage.SendMessageBuilder<?, ?> sendMessageBuilder = SendMessage.builder()
                .chatId(chat)
                .text(message);

        if (chat.contains("_")) {
            sendMessageBuilder
                    .messageThreadId(Integer.parseInt(chat.split("_")[1]));
        }

        SendMessage sendMessage = sendMessageBuilder.build();

        switch (fileFacade.integration().telegram().parseMode()) {
            case MARKDOWN -> sendMessage.enableMarkdown(true);
            case MARKDOWN_V2 -> sendMessage.enableMarkdownV2(true);
            case HTML -> sendMessage.enableHtml(true);
        }

        telegramBot.executeMethod(sendMessage);
    }

}
