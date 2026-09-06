package net.flectone.bot.discord.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.discord.forum.ForumService;
import reactor.core.publisher.Mono;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class ButtonListener implements EventListener<ButtonInteractionEvent> {

    private final ForumService forumService;

    @Override
    public Class<ButtonInteractionEvent> eventType() {
        return ButtonInteractionEvent.class;
    }

    @Override
    public Mono<Void> handle(ButtonInteractionEvent event) {
        String customId = event.getCustomId();
        if (!customId.startsWith(ForumService.CLOSE_BUTTON_ID + "_")) return Mono.empty();

        return forumService.close(event, customId);
    }

}
