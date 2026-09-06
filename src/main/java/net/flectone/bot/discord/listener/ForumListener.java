package net.flectone.bot.discord.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.core.event.domain.thread.ThreadChannelCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.ThreadChannel;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.discord.forum.ForumService;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Optional;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class ForumListener implements EventListener<ThreadChannelCreateEvent> {

    private static final Duration STARTER_RETRY_DELAY = Duration.ofSeconds(2);
    private static final int STARTER_RETRIES = 3;

    private final ForumService forumService;

    @Override
    public Class<ThreadChannelCreateEvent> eventType() {
        return ThreadChannelCreateEvent.class;
    }

    @Override
    public Mono<Void> handle(ThreadChannelCreateEvent event) {
        if (!event.isNewlyCreated()) return Mono.empty();

        ThreadChannel post = event.getChannel();
        if (!forumService.isWatched(post)) return Mono.empty();

        return starter(post).flatMap(starter -> forumService.welcome(post, starter.orElse(null))
                .then(starter
                        .map(message -> forumService.answerStarter(post, message))
                        .orElse(Mono.empty())
                )
        );
    }

    private Mono<Optional<Message>> starter(ThreadChannel post) {
        return post.getMessageById(post.getId())
                .retryWhen(Retry.fixedDelay(STARTER_RETRIES, STARTER_RETRY_DELAY))
                .map(Optional::of)
                .onErrorReturn(Optional.empty())
                .defaultIfEmpty(Optional.empty());
    }

}
