package net.flectone.bot.module.discord.command;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import net.flectone.bot.config.Integration;
import reactor.core.publisher.Mono;

public interface Command {

    Integration.Discord.Command getConfig();

    ApplicationCommandRequest getRequest();

    Mono<Void> handle(ChatInputInteractionEvent event);

}
