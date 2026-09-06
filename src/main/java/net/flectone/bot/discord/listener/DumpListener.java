package net.flectone.bot.discord.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.discord.forum.ForumService;
import reactor.core.publisher.Mono;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class DumpListener implements EventListener<MessageCreateEvent> {

    private final ForumService forumService;

    @Override
    public Class<MessageCreateEvent> eventType() {
        return MessageCreateEvent.class;
    }

    @Override
    public Mono<Void> handle(MessageCreateEvent event) {
        Message message = event.getMessage();
        if (message.getAuthor().map(User::isBot).orElse(true)) return Mono.empty();

        return message.getChannel()
                .map(forumService::watchedPost)
                .flatMap(post -> forumService.answerDump(message, post.orElse(null)));
    }

}
