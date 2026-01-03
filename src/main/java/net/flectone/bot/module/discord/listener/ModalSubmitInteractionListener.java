package net.flectone.bot.module.discord.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.module.discord.formatter.DiscordFormatter;
import net.flectone.bot.module.discord.sender.MessageSender;
import net.flectone.bot.module.discord.service.TicketService;
import net.flectone.bot.util.file.FileFacade;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.UnaryOperator;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class ModalSubmitInteractionListener implements EventListener<ModalSubmitInteractionEvent> {

    private final DiscordFormatter discordFormatter;
    private final FileFacade fileFacade;
    private final MessageSender messageSender;
    private final TicketService ticketService;


    @Override
    public Class<ModalSubmitInteractionEvent> getEventType() {
        return ModalSubmitInteractionEvent.class;
    }

    @Override
    public Mono<Void> handle(ModalSubmitInteractionEvent event) {
        String modalId = event.getCustomId();

        Integration.Discord.Ticket.Modal modalConfig = fileFacade.integration().discord().ticket().modals().get(modalId);
        if (modalConfig == null) return Mono.empty();

        Map<String, String> placeholders = discordFormatter.collectPlaceholders(event, modalId);
        UnaryOperator<String> formatter = text -> discordFormatter.formatMessage(text, placeholders);

        return ticketService.createForumThread(modalId, placeholders, event.getUser(), formatter)
                .flatMap(thread -> messageSender.sendMessage(
                        event,
                        modalConfig.createMessage(),
                        modalId,
                        Map.of("thread", thread.getMention()),
                        true
                ));
    }

}
