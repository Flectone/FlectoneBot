package net.flectone.bot.discord.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.emoji.Emoji;
import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.discord.DiscordBot;
import net.flectone.bot.discord.message.StreamingMessage;
import net.flectone.bot.file.FileFacade;
import net.flectone.bot.rag.RagBot;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import reactor.core.publisher.Mono;

import java.util.List;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class AiListener implements EventListener<MessageCreateEvent> {

    private static final String RESET_REACTION = "✅";
    private static final List<String> RESET_COMMANDS = List.of("reset", "/reset", "сброс");

    private final RagBot ragBot;
    private final DiscordBot discordBot;
    private final FileFacade fileFacade;
    private final Logger logger;

    @Override
    public Class<MessageCreateEvent> eventType() {
        return MessageCreateEvent.class;
    }

    @Override
    public Mono<Void> handle(MessageCreateEvent event) {
        if (!ragBot.isEnabled()) return Mono.empty();

        Member member = event.getMember().orElse(null);
        if (member == null || member.isBot()) return Mono.empty();

        Message message = event.getMessage();
        if (!isAsked(message)) return Mono.empty();

        String question = question(message);
        if (RESET_COMMANDS.contains(question.toLowerCase(java.util.Locale.ROOT))) {
            return reset(message, member);
        }

        if (question.isEmpty() && message.getAttachments().isEmpty()) return Mono.empty();

        return hasAccess(member, message)
                .filter(Boolean::booleanValue)
                .flatMap(access -> answer(message, member));
    }

    private boolean isAsked(Message message) {
        Long aiChannel = fileFacade.discord().channelIdForAi();
        if (aiChannel != null && aiChannel != 0 && message.getChannelId().asLong() == aiChannel) return true;

        return message.getUserMentionIds().stream()
                .anyMatch(id -> id.asLong() == discordBot.getClientId());
    }

    private Mono<Boolean> hasAccess(Member member, Message message) {
        Integration.Discord config = fileFacade.discord();

        Long aiChannel = config.channelIdForAi();
        if (aiChannel != null && aiChannel != 0 && message.getChannelId().asLong() == aiChannel) {
            return Mono.just(true);
        }

        Long role = config.roleIdForAi();
        if (role == null || role == 0) return Mono.just(true);

        return Mono.just(member.getRoleIds().stream().anyMatch(id -> id.asLong() == role));
    }

    private Mono<Void> reset(Message message, Member member) {
        return Mono.fromFuture(ragBot.reset(member.getId().asLong()))
                .then(message.addReaction(Emoji.unicode(RESET_REACTION)))
                .doOnError(error -> logger.warn("Failed to reset the RAG thread of {}", member.getId().asLong(), error))
                .onErrorResume(error -> Mono.empty());
    }

    private Mono<Void> answer(Message message, Member member) {
        List<String> attachments = message.getAttachments().stream()
                .map(Attachment::getUrl)
                .toList();

        return message.getChannel()
                .flatMap(channel -> channel.typeUntil(Mono.create(sink -> {
                    StreamingMessage answer = stream(channel, message);

                    ragBot.ask(
                            member.getId().asLong(),
                            question(message),
                            attachments,
                            answer::append,
                            () -> {
                                answer.complete();
                                sink.success();
                            },
                            error -> {
                                answer.fail(error);
                                sink.success();
                            }
                    );
                })).then());
    }

    private StreamingMessage stream(MessageChannel channel, Message message) {
        return new StreamingMessage(channel, message.getId().asLong(), fileFacade.discord().aiNote(), logger);
    }

    private String question(Message message) {
        return StringUtils.normalizeSpace(message.getContent()
                .replace("<@" + discordBot.getClientId() + ">", StringUtils.EMPTY)
                .replace("<@!" + discordBot.getClientId() + ">", StringUtils.EMPTY)
        );
    }

}
