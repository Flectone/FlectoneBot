package net.flectone.bot.discord.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.Webhook;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.file.FileFacade;
import net.flectone.bot.telegram.TelegramBot;
import net.flectone.bot.telegram.TelegramSender;
import net.flectone.bot.util.Placeholders;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class BridgeListener implements EventListener<MessageCreateEvent> {

    private final FileFacade fileFacade;
    private final TelegramBot telegramBot;
    private final TelegramSender telegramSender;

    @Override
    public Class<MessageCreateEvent> eventType() {
        return MessageCreateEvent.class;
    }

    @Override
    public Mono<Void> handle(MessageCreateEvent event) {
        if (!telegramBot.isEnabled()) return Mono.empty();

        Member member = event.getMember().orElse(null);
        if (member == null || member.isBot()) return Mono.empty();

        Message message = event.getMessage();

        String chat = fileFacade.discord().channels().get(message.getChannelId().asLong());
        if (chat == null) return Mono.empty();

        return reply(message).map(reply -> Placeholders.create()
                        .put("name", member.getGlobalName().orElse(member.getUsername()))
                        .put("global_name", member.getGlobalName().orElse(StringUtils.EMPTY))
                        .put("nickname", member.getNickname().orElse(StringUtils.EMPTY))
                        .put("display_name", member.getDisplayName())
                        .put("user_name", member.getUsername())
                        .put("message", content(message))
                        .put("reply", reply)
                )
                .doOnNext(placeholders -> telegramSender.send(chat, placeholders))
                .then();
    }

    private Mono<String> reply(Message message) {
        Optional<Message> referenced = message.getReferencedMessage();
        if (referenced.isEmpty()) return Mono.just(StringUtils.EMPTY);

        Message reply = referenced.get();

        return author(reply).map(author -> Placeholders.create()
                .put("reply_user", author)
                .put("reply_message", content(reply))
                .apply(fileFacade.discord().formatReply())
        );
    }

    private Mono<String> author(Message message) {
        Optional<String> user = message.getAuthor().map(User::getUsername);
        if (user.isPresent()) return Mono.just(user.get());

        return message.getWebhook()
                .map(Webhook::getName)
                .map(name -> name.orElse("Unknown"))
                .defaultIfEmpty("Unknown");
    }

    private String content(Message message) {
        List<String> attachments = message.getAttachments().stream()
                .map(Attachment::getUrl)
                .toList();

        if (attachments.isEmpty()) return message.getContent();

        String urls = String.join(" ", attachments);
        return message.getContent().isEmpty() ? urls : message.getContent() + " " + urls;
    }

    private Integration.Discord config() {
        return fileFacade.discord();
    }

}
