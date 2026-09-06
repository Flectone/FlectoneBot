package net.flectone.bot.discord.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Logger;
import reactor.core.Disposable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class ListenerRegistry {

    private final List<Disposable> subscriptions = new CopyOnWriteArrayList<>();

    private final Logger logger;

    public <E extends Event> void register(GatewayDiscordClient client, EventListener<E> listener) {
        String name = listener.getClass().getSimpleName();

        Disposable subscription = client.on(listener.eventType())
                .flatMap(event -> listener.handle(event)
                        .onErrorResume(error -> {
                            logger.error("{} failed", name, error);
                            return reactor.core.publisher.Mono.empty();
                        })
                )
                .subscribe(null, error -> logger.error("{} stopped", name, error));

        subscriptions.add(subscription);
        logger.info("Registered listener {}", name);
    }

    public void unregisterAll() {
        subscriptions.forEach(Disposable::dispose);
        subscriptions.clear();
    }

}
