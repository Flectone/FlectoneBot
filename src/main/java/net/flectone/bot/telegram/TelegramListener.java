package net.flectone.bot.telegram;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.discord.message.WebhookSender;
import net.flectone.bot.file.FileFacade;
import net.flectone.bot.util.Placeholders;
import net.flectone.bot.util.SystemVariableResolver;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.GetUserProfilePhotos;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class TelegramListener implements LongPollingSingleThreadUpdateConsumer {

    private static final String TOPIC_SEPARATOR = "_";
    private static final String FILE_URL = "https://api.telegram.org/file/bot%s/%s";

    private final Cache<Long, String> avatars = CacheBuilder.newBuilder()
            .expireAfterWrite(50, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();

    private final FileFacade fileFacade;
    private final Provider<TelegramBot> telegramBot;
    private final WebhookSender webhookSender;
    private final SystemVariableResolver systemVariableResolver;
    private final Logger logger;

    @Override
    public void consume(Update update) {
        if (!update.hasMessage()) return;

        Message message = update.getMessage();

        User author = message.getFrom();
        if (author == null || Boolean.TRUE.equals(author.getIsBot())) return;

        String text = message.getText();
        if (StringUtils.isEmpty(text)) return;

        String discordChannel = fileFacade.telegram().channels().get(chatId(message));
        if (discordChannel == null) return;

        String userName = StringUtils.defaultString(author.getUserName());
        String name = userName.isEmpty() ? author.getFirstName() : userName;

        Placeholders placeholders = Placeholders.create()
                .put("name", name)
                .put("user_name", userName)
                .put("first_name", author.getFirstName())
                .put("last_name", StringUtils.defaultString(author.getLastName()))
                .put("chat", StringUtils.defaultString(message.getChat().getTitle()))
                .put("message", text)
                .put("avatar", avatar(author))
                .put("reply", reply(message));

        webhookSender.send(name, discordChannel, fileFacade.discord(), placeholders);
    }

    private String chatId(Message message) {
        return message.getChatId() + (Boolean.TRUE.equals(message.getIsTopicMessage())
                ? TOPIC_SEPARATOR + message.getMessageThreadId()
                : StringUtils.EMPTY);
    }

    private String reply(Message message) {
        Message replied = message.getReplyToMessage();
        if (replied == null || !hasContent(replied) || isTopicService(replied)) return StringUtils.EMPTY;

        User author = replied.getFrom();
        if (author == null) return StringUtils.EMPTY;

        Integration.Discord config = fileFacade.discord();

        return Placeholders.create()
                .put("reply_user", StringUtils.defaultString(author.getUserName(), author.getFirstName()))
                .put("reply_message", StringUtils.defaultString(replied.getText()))
                .apply(config.formatReply());
    }

    private boolean hasContent(Message message) {
        return message.hasText()
                || message.hasPhoto()
                || message.hasDocument()
                || message.hasVideo()
                || message.getAudio() != null
                || message.getVoice() != null
                || message.getSticker() != null;
    }

    private boolean isTopicService(Message message) {
        return message.getForumTopicCreated() != null
                || message.getForumTopicEdited() != null
                || message.getForumTopicClosed() != null
                || message.getForumTopicReopened() != null;
    }

    private String avatar(User user) {
        String cached = avatars.getIfPresent(user.getId());
        if (cached != null) return cached;

        String avatar = requestAvatar(user.getId());
        avatars.put(user.getId(), avatar);

        return avatar;
    }

    private String requestAvatar(long userId) {
        TelegramBot bot = telegramBot.get();
        if (!bot.isEnabled()) return StringUtils.EMPTY;

        try {
            List<List<PhotoSize>> photos = bot.getClient().execute(GetUserProfilePhotos.builder()
                    .userId(userId)
                    .limit(1)
                    .build()
            ).getPhotos();

            Optional<PhotoSize> photo = photos.stream()
                    .filter(sizes -> !sizes.isEmpty())
                    .map(List::getLast)
                    .findFirst();

            if (photo.isEmpty()) return StringUtils.EMPTY;

            String filePath = bot.getClient()
                    .execute(GetFile.builder().fileId(photo.get().getFileId()).build())
                    .getFilePath();

            String token = systemVariableResolver.substitute(fileFacade.telegram().token());
            return String.format(FILE_URL, token, filePath);
        } catch (Exception e) {
            logger.warn("Failed to read the telegram avatar of {}", userId, e);
            return StringUtils.EMPTY;
        }
    }

}
