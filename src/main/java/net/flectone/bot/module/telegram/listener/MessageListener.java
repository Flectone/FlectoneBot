package net.flectone.bot.module.telegram.listener;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.module.discord.sender.MessageSender;
import net.flectone.bot.module.telegram.TelegramBot;
import net.flectone.bot.processing.SystemVariableResolver;
import net.flectone.bot.util.file.FileFacade;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.GetUserProfilePhotos;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class MessageListener implements LongPollingSingleThreadUpdateConsumer {

    private final LoadingCache<Long, String> userPhotos = CacheBuilder.newBuilder()
            .expireAfterAccess(50, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build(new CacheLoader<>() {

                @NotNull
                public String load(@NotNull Long userId) {
                    return getUserPhotoAsync(userId).join();
                }

            });

    private final FileFacade fileFacade;
    private final Provider<TelegramBot> telegramBotProvider;
    private final MessageSender discordMessageSender;
    private final SystemVariableResolver systemVariableResolver;
    private final Logger logger;

    public Integration.Telegram config() {
        return fileFacade.integration().telegram();
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage()) return;

        Message message = update.getMessage();

        String chatId = getChatId(message);

        User author = message.getFrom();
        if (author == null) return;

        logger.info("TELEGRAM chat_id={}, author_id={}, message_id={}", chatId, author.getId(), message.getMessageId());

        String discordChannelId = config().channels().get(chatId);
        if (discordChannelId == null) return;

        String text = message.getText();
        if (text == null) return;

        String chat = message.getChat().getTitle();
        if (chat == null) return;

        Pair<String, String> reply = null;
        if (isRealReply(message)) {
            Message replied = message.getReplyToMessage();
            User user = replied.getFrom();
            if (user != null) {
                reply = Pair.of(user.getUserName(), replied.getText());
            }
        }

        String userName = StringUtils.defaultString(author.getUserName());
        String firstName = author.getFirstName();
        String lastName = StringUtils.defaultString(author.getLastName());
        String avatar = getUserPhoto(author);
        String formatReply = formatReplyForDiscord(reply);

        discordMessageSender.sendMessage(userName.isEmpty() ? firstName : userName, discordChannelId, fileFacade.integration().discord(), s -> StringUtils.replaceEach(
                s,
                new String[]{"<name>", "<user_name>", "<first_name>", "<last_name>", "<chat>", "<message>", "<avatar>", "<reply>"},
                new String[]{userName, userName, firstName, lastName, StringUtils.defaultString(chat), text, avatar, formatReply}
        ), List.of());
    }

    private String formatReplyForDiscord(Pair<String, String> reply) {
        if (reply == null) return "";

        return StringUtils.replaceEach(
                fileFacade.integration().discord().formatReply(),
                new String[]{"<reply_user>", "<reply_message>"},
                new String[]{StringUtils.defaultString(reply.getLeft()), StringUtils.defaultString(reply.getRight())}
        );
    }

    private String getUserPhoto(User user) {
        try {
            return userPhotos.get(user.getId());
        } catch (Exception e) {
            return "";
        }
    }

    private CompletableFuture<String> getUserPhotoAsync(Long userId) {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            telegramBotProvider.get().getTelegramClient().executeAsync(
                    GetUserProfilePhotos.builder()
                            .userId(userId)
                            .limit(1)
                            .build()
            ).thenAcceptAsync(userProfilePhotos -> {
                if (userProfilePhotos.getPhotos() == null || userProfilePhotos.getPhotos().isEmpty()) {
                    future.complete("");
                    return;
                }

                PhotoSize photoSize = userProfilePhotos.getPhotos().getFirst().getLast();

                try {
                    telegramBotProvider.get().getTelegramClient().executeAsync(
                            GetFile.builder().fileId(photoSize.getFileId()).build()
                    ).thenAcceptAsync(file -> future.complete(getPhotoUrl(file)));
                } catch (TelegramApiException e) {
                    future.complete("");
                }

            });
        } catch (TelegramApiException e) {
            future.complete(null);
        }

        return future;
    }

    private boolean isRealReply(Message message) {
        if (message.getReplyToMessage() == null) {
            return false;
        }

        Message replied = message.getReplyToMessage();

        boolean hasContent = replied.hasText()
                || replied.hasPhoto()
                || replied.hasDocument()
                || replied.hasVideo()
                || replied.getAudio() != null
                || replied.getVoice() != null
                || replied.getSticker() != null;

        boolean isNotTopicCreation = replied.getForumTopicCreated() == null
                && replied.getForumTopicEdited() == null
                && replied.getForumTopicClosed() == null
                && replied.getForumTopicReopened() == null;

        return hasContent && isNotTopicCreation;
    }

    private boolean isNewChatNameMessage(Message message) {
        if (message.getNewChatTitle() == null && message.getForumTopicEdited() == null) return false;

        User user = message.getFrom();
        return user != null && user.getIsBot();
    }

    private String getPhotoUrl(File file) {
        String token = systemVariableResolver.substituteEnvVars(config().token());
        return "https://api.telegram.org/file/bot" + token + "/" + file.getFilePath();
    }

    public String getChatId(Message message) {
        return message.getChatId() + (message.isTopicMessage() ? "_" + message.getMessageThreadId() : "");
    }

    public void deleteMessage(String chatId, Integer messageId) {
        telegramBotProvider.get().executeMethod(DeleteMessage.builder()
                .chatId(chatId)
                .messageId(messageId)
                .build()
        );
    }

}
