package net.flectone.bot.module.discord.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.module.discord.command.Command;
import net.flectone.bot.module.discord.register.CommandRegistry;
import org.apache.logging.log4j.Logger;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class ChatInputInteractionListener implements EventListener<ChatInputInteractionEvent> {

    private final Logger logger;
    private final CommandRegistry commandRegistry;

    @Override
    public Class<ChatInputInteractionEvent> getEventType() {
        return ChatInputInteractionEvent.class;
    }

    @Override
    public Mono<Void> handle(ChatInputInteractionEvent event) {
        String commandName = event.getCommandName();
        Optional<Command> command = commandRegistry.getCommand(commandName);

        if (command.isEmpty()) {
            logger.warn("Unknown command received: {}", commandName);
            return event.reply("Unknown command!")
                    .withEphemeral(true)
                    .then();
        }

        return command.get().handle(event)
                .onErrorResume(error -> {
                    logger.error("Error executing command: {}", commandName, error);
                    return event.reply("An error occurred while executing the command!")
                            .withEphemeral(true)
                            .then();
                });
    }
}
