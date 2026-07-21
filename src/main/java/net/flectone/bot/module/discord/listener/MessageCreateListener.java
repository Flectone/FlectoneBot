package net.flectone.bot.module.discord.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.emoji.Emoji;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.BanQuerySpec;
import discord4j.core.spec.MessageEditSpec;
import discord4j.discordjson.json.MessageReferenceData;
import discord4j.discordjson.possible.Possible;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.module.discord.DiscordBot;
import net.flectone.bot.module.rag.RagBot;
import net.flectone.bot.module.telegram.TelegramBot;
import net.flectone.bot.module.telegram.sender.MessageSender;
import net.flectone.bot.util.file.FileFacade;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class MessageCreateListener implements EventListener<MessageCreateEvent> {

    private static final int MAX_MESSAGE_LENGTH = 1900;
    private static final String SUCCESS_EMOJI_RESET = "✅";

    private final FileFacade fileFacade;
    private final MessageSender telegramMessageSender;
    private final Logger logger;
    private final RagBot ragBot;
    private final DiscordBot discordBot;
    private final TelegramBot telegramBot;

    @Override
    public Class<MessageCreateEvent> getEventType() {
        return MessageCreateEvent.class;
    }

    @Override
    public Mono<Void> handle(MessageCreateEvent event) {
        Message discordMessage = event.getMessage();
        Member member = event.getMember().orElse(null);

        if (member == null || member.isBot()) {
            return Mono.empty();
        }

        if (discordMessage.getChannelId().asLong() == getConfig().channelIdForHoneypot()) {
            logger.info("BAN SCAM chat_id={}, author_id={}, message_id={}", discordMessage.getChannelId().asLong(), member.getId().asLong(), discordMessage.getId().asLong());
            return member.ban(BanQuerySpec.builder().reason("Scam in honeypot channel").deleteMessageSeconds(60).build());
        }

        if (shouldHandleWithRag(discordMessage)) {
            handleRagMessage(discordMessage, member);
            return Mono.empty();
        }

        return handleTelegramBridge(discordMessage, member);
    }

    private boolean shouldHandleWithRag(Message message) {
        if (!ragBot.isInitialized()) {
            return false;
        }

        Integration.Discord config = getConfig();
        boolean isAiChannel = message.getChannelId().asLong() == config.channelIdForAi();
        boolean isBotMentioned = message.getMemberMentions().stream()
                .anyMatch(member -> member.getId().asLong() == discordBot.getClientID());

        return isAiChannel || isBotMentioned;
    }

    private void handleRagMessage(Message message, Member member) {
        String content = replaceMentions(message.getContent());

        if (content.equalsIgnoreCase("reset") || content.equalsIgnoreCase("/reset")) {
            handleReset(message, member);
            return;
        }

        if (content.isEmpty()) {
            return;
        }

        List<String> attachments = extractAttachmentUrls(message);

        CompletableFuture.runAsync(() ->
                member.getRoles()
                        .any(role -> hasRagAccess(message, role))
                        .flatMap(hasAccess -> processRagRequest(message, member, content, attachments, hasAccess))
                        .block()
        );
    }

    private void handleReset(Message message, Member member) {
        long userId = member.getId().asLong();

        ragBot.resetThread(userId)
                .thenRun(() -> {
                    message.addReaction(Emoji.unicode(SUCCESS_EMOJI_RESET)).subscribe();
                    logger.info("Reset RAG thread for user {}", userId);
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to reset thread for user {}", userId, throwable);
                    message.getChannel()
                            .flatMap(channel -> channel.createMessage("❌ Failed to reset conversation"))
                            .subscribe();
                    return null;
                });
    }

    private boolean hasRagAccess(Message message, Role role) {
        Integration.Discord config = getConfig();
        boolean isAiChannel = message.getChannelId().asLong() == config.channelIdForAi();
        return isAiChannel || role.getId().asLong() == config.roleIdForAi();
    }

    private Mono<Void> processRagRequest(Message message, Member member, String content, List<String> attachments, Boolean hasAccess) {
        if (!Boolean.TRUE.equals(hasAccess)) {
            return Mono.empty();
        }

        return message.getChannel()
                .flatMap(channel -> channel.typeUntil(
                        Mono.create(sink -> streamRagResponse(
                                channel,
                                member.getId().asLong(),
                                message.getId().asLong(),
                                content,
                                attachments,
                                sink::success,
                                error -> sink.error(new RuntimeException(error))
                        ))
                ).then());
    }

    private void streamRagResponse(
            MessageChannel channel,
            long userId,
            long replyMessageId,
            String content,
            List<String> attachments,
            Runnable onDone,
            java.util.function.Consumer<String> onError
    ) {
        StreamingMessageManager messageManager = new StreamingMessageManager(channel, replyMessageId);

        ragBot.streamQuestion(
                userId,
                content,
                attachments,
                messageManager::updateMessages,
                onDone,
                error -> {
                    messageManager.showError(error);
                    onError.accept(error);
                }
        );
    }

    private Mono<Void> handleTelegramBridge(Message message, Member member) {
        if (!telegramBot.isEnabled()) return Mono.empty();

        Integration.Discord config = getConfig();
        String telegramChannelId = config.channels().get(message.getChannelId().asLong());

        if (telegramChannelId == null) {
            return Mono.empty();
        }

        TelegramMessageData messageData = new TelegramMessageData(
                member.getGlobalName().orElse(""),
                member.getNickname().orElse(""),
                member.getDisplayName(),
                member.getUsername(),
                extractMessageContent(message),
                formatReply(message)
        );

        telegramMessageSender.sendMessage(telegramChannelId, messageData::format);
        return Mono.empty();
    }

    private String replaceMentions(String content) {
        return content.replace("<@" + discordBot.getClientID() + ">", "").trim();
    }

    private String extractMessageContent(Message message) {
        String content = message.getContent();
        List<String> attachmentUrls = extractAttachmentUrls(message);

        if (attachmentUrls.isEmpty()) {
            return content;
        }

        return content.isEmpty()
                ? String.join(" ", attachmentUrls)
                : content + " " + String.join(" ", attachmentUrls);
    }

    private List<String> extractAttachmentUrls(Message message) {
        return message.getAttachments().stream()
                .map(Attachment::getUrl)
                .toList();
    }

    private String formatReply(Message message) {
        return retrieveReply(message)
                .map(this::formatReplyForTelegram)
                .orElse("");
    }

    private Optional<Pair<String, String>> retrieveReply(Message message) {
        return message.getReferencedMessage()
                .map(referencedMessage -> {
                    String content = extractMessageContent(referencedMessage);
                    String author = extractAuthorName(referencedMessage);
                    return Pair.of(author, content);
                });
    }

    private String extractAuthorName(Message message) {
        return message.getAuthor()
                .map(User::getUsername)
                .or(() -> extractWebhookName(message))
                .orElse("Unknown");
    }

    private Optional<String> extractWebhookName(Message message) {
        return message.getWebhookId()
                .map(id -> message.getWebhook().block())
                .flatMap(Webhook::getName);
    }

    private String formatReplyForTelegram(Pair<String, String> reply) {
        Integration.Telegram config = fileFacade.integration().telegram();
        return StringUtils.replaceEach(
                config.formatReply(),
                new String[]{"<reply_user>", "<reply_message>"},
                new String[]{
                        StringUtils.defaultString(reply.getLeft()),
                        StringUtils.defaultString(reply.getRight())
                }
        );
    }

    private Integration.Discord getConfig() {
        return fileFacade.integration().discord();
    }

    private static class StreamingMessageManager {

        private final MessageChannel channel;
        private final long replyMessageId;

        private final List<Message> messages = new ArrayList<>();
        private final StringBuilder buffer = new StringBuilder();

        public StreamingMessageManager(MessageChannel channel, long replyMessageId) {
            this.channel = channel;
            this.replyMessageId = replyMessageId;
        }

        public void updateMessages(String chunk) {
            buffer.append(chunk);
            List<String> parts = splitText(buffer.toString());

            if (messages.isEmpty()) {
                createFirstMessage(parts.getFirst());
            } else {
                updateExistingMessages(parts);
            }
        }

        public void showError(String error) {
            String errorMessage = "❌ Error: " + error;

            if (messages.isEmpty()) {
                createMessage(errorMessage);
            } else {
                updateLastMessage(errorMessage);
            }
        }

        private void createFirstMessage(String content) {
            Message message = createMessage(content);
            messages.add(message);
        }

        private void updateExistingMessages(List<String> parts) {
            int currentCount = messages.size();
            int newCount = parts.size();

            if (newCount > currentCount) {
                updateLastMessage(parts.get(currentCount - 1));
                createAdditionalMessages(parts, currentCount);
            } else {
                updateLastMessage(parts.getLast());
            }
        }

        private void createAdditionalMessages(List<String> parts, int startIndex) {
            for (int i = startIndex; i < parts.size(); i++) {
                Message message = createMessage(parts.get(i));
                messages.add(message);
            }
        }

        private Message createMessage(String content) {
            return channel.createMessage(content)
                    .withMessageReference(MessageReferenceData.builder()
                            .messageId(replyMessageId)
                            .build())
                    .block();
        }

        private void updateLastMessage(String content) {
            messages.getLast()
                    .edit(MessageEditSpec.builder().content(Possible.of(Optional.of(content))).build())
                    .subscribe();
        }

        private List<String> splitText(String text) {
            List<String> result = new ArrayList<>();

            while (text.length() > MAX_MESSAGE_LENGTH) {
                int splitIndex = findBestSplitIndex(text);
                result.add(text.substring(0, splitIndex));
                text = text.substring(splitIndex).trim();
            }

            if (!text.isEmpty()) {
                result.add(text);
            }

            return result;
        }

        private int findBestSplitIndex(String text) {
            int newlineIndex = text.lastIndexOf('\n', MAX_MESSAGE_LENGTH);
            if (newlineIndex != -1) {
                return newlineIndex;
            }

            int spaceIndex = text.lastIndexOf(' ', MAX_MESSAGE_LENGTH);
            return spaceIndex != -1 ? spaceIndex : MAX_MESSAGE_LENGTH;
        }
    }

    private record TelegramMessageData(
            String globalName,
            String nickname,
            String displayName,
            String userName,
            String message,
            String reply
    ) {
        String format(String template) {
            return StringUtils.replaceEach(
                    template,
                    new String[]{"<name>", "<global_name>", "<nickname>", "<display_name>", "<user_name>", "<message>", "<reply>"},
                    new String[]{globalName, globalName, nickname, displayName, userName, StringUtils.defaultString(message), reply}
            );
        }
    }
}