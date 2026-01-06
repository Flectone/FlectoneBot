package net.flectone.bot.module.discord.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.*;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.module.telegram.sender.MessageSender;
import net.flectone.bot.util.file.FileFacade;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class MessageCreateListener implements EventListener<MessageCreateEvent> {

    private final FileFacade fileFacade;
    private final MessageSender telegramMessageSender;
    private final Logger logger;

    public Integration.Discord config() {
        return fileFacade.integration().discord();
    }

    @Override
    public Class<MessageCreateEvent> getEventType() {
        return MessageCreateEvent.class;
    }

    @Override
    public Mono<Void> handle(MessageCreateEvent event) {
        Message discordMessage = event.getMessage();

        Optional<Member> user = event.getMember();
        if (user.isEmpty()) return Mono.empty();

        Member member = user.get();
        if (member.isBot()) return Mono.empty();

        Long discordChannelId = discordMessage.getChannelId().asLong();
        logger.info("DISCORD chat_id={}, author_id={}, message_id={}", discordChannelId, member.getId(), discordMessage.getId());

        String telegramChannelId = config().channels().get(discordChannelId);
        if (telegramChannelId == null) return Mono.empty();

        String globalName = member.getGlobalName().orElse("");
        String nickname = member.getNickname().orElse("");
        String displayName = member.getDisplayName();
        String userName = member.getUsername();
        String formatReply = retrieveReply(discordMessage)
                .map(this::formatReplyForTelegram)
                .orElse("");

        telegramMessageSender.sendMessage(telegramChannelId, s -> StringUtils.replaceEach(
                s,
                new String[]{"<name>", "<global_name>", "<nickname>", "<display_name>", "<user_name>", "<message>", "<reply>"},
                new String[]{globalName, globalName, nickname, displayName, userName, StringUtils.defaultString(getMessageContent(discordMessage)), formatReply}
        ));

        return Mono.empty();
    }

    private Optional<Pair<String, String>> retrieveReply(Message message) {
        Optional<Message> optionalReferencedMessage = message.getReferencedMessage();
        if (optionalReferencedMessage.isEmpty()) return Optional.empty();

        Message referencedMessage = optionalReferencedMessage.get();

        String content = getMessageContent(referencedMessage);

        Optional<User> author = referencedMessage.getAuthor();
        if (author.isPresent()) return Optional.of(Pair.of(author.get().getUsername(), content));

        Optional<Snowflake> webhookId = referencedMessage.getWebhookId();
        if (webhookId.isPresent()) {
            Webhook webhook = referencedMessage.getWebhook().block();
            if (webhook != null) {
                return Optional.of(Pair.of(webhook.getName().orElse("Unknown"), content));
            }
        }

        return Optional.of(Pair.of("Unknown", content));
    }

    private String formatReplyForTelegram(Pair<String, String> reply) {
        if (reply == null) return "";

        return StringUtils.replaceEach(
                fileFacade.integration().telegram().formatReply(),
                new String[]{"<reply_user>", "<reply_message>"},
                new String[]{StringUtils.defaultString(reply.getLeft()), StringUtils.defaultString(reply.getRight())}
        );
    }

    private String getMessageContent(Message message) {
        String content = message.getContent();
        if (!message.getAttachments().isEmpty()) {
            content = (content.isEmpty() ? "" : content + " ") + String.join(" ", message.getAttachments()
                    .stream()
                    .map(Attachment::getUrl)
                    .toList()
            );
        }

        return content;
    }

}
