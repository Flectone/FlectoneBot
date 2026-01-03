package net.flectone.bot.module.discord.register;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.module.discord.listener.EventListener;
import org.apache.logging.log4j.Logger;
import reactor.core.Disposable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class ListenerRegistry {

    private final Map<Class<? extends EventListener<?>>, Disposable> subscriptions = new ConcurrentHashMap<>();
    private final Logger logger;

    @SuppressWarnings("unchecked")
    public <E extends Event> void register(GatewayDiscordClient client, EventListener<E> listener) {
        Disposable disposable = client.on(listener.getEventType())
                .flatMap(listener::handle)
                .onErrorContinue((throwable, obj) ->
                        logger.error("Error in listener {}: {}", listener.getClass().getSimpleName(), throwable.getMessage())
                )
                .subscribe();

        subscriptions.put((Class<? extends EventListener<?>>) listener.getClass(), disposable);
        logger.info("Registered listener: {}", listener.getClass().getSimpleName());
    }

    public List<String> getRegisteredListeners() {
        return subscriptions.keySet().stream()
                .map(Class::getSimpleName)
                .toList();
    }

    public boolean isRegistered(Class<? extends EventListener<?>> listenerClass) {
        return subscriptions.containsKey(listenerClass);
    }

}