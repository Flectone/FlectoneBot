package net.flectone.bot.discord.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.discord.command.Command;
import net.flectone.bot.discord.command.CommandRegistry;
import net.flectone.bot.file.FileFacade;
import org.apache.logging.log4j.Logger;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class CommandListener implements EventListener<ChatInputInteractionEvent> {

    private final CommandRegistry commandRegistry;
    private final FileFacade fileFacade;
    private final Logger logger;

    @Override
    public Class<ChatInputInteractionEvent> eventType() {
        return ChatInputInteractionEvent.class;
    }

    @Override
    public Mono<Void> handle(ChatInputInteractionEvent event) {
        String name = event.getCommandName();

        Optional<Command> command = commandRegistry.get(name);
        if (command.isEmpty()) {
            logger.warn("Unknown command /{}", name);
            return event.reply(fileFacade.discord().messages().unknownCommand()).withEphemeral(true).then();
        }

        return command.get().handle(event)
                .onErrorResume(error -> {
                    logger.error("Command /{} failed", name, error);
                    return event.reply(fileFacade.discord().messages().commandError()).withEphemeral(true).then();
                });
    }

}
