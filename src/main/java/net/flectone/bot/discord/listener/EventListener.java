package net.flectone.bot.discord.listener;

import discord4j.core.event.domain.Event;
import reactor.core.publisher.Mono;

public interface EventListener<E extends Event> {

    Class<E> eventType();

    Mono<Void> handle(E event);

}
