package net.flectone.bot.module.discord.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.component.*;
import discord4j.core.object.entity.channel.ThreadChannel;
import discord4j.core.spec.InteractionPresentModalSpec;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.module.discord.service.ModalService;
import net.flectone.bot.util.file.FileFacade;
import reactor.core.publisher.Mono;

import java.util.*;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class ButtonListener implements EventListener<ButtonInteractionEvent> {

    private final FileFacade fileFacade;
    private final ModalService modalService;

    @Override
    public Class<ButtonInteractionEvent> getEventType() {
        return ButtonInteractionEvent.class;
    }

    @Override
    public Mono<Void> handle(ButtonInteractionEvent event) {
        String customId = event.getCustomId();

        if (customId.startsWith("close_button_")) {
            return handleCloseTicket(event, customId);
        }

        Optional<InteractionPresentModalSpec> presentModalSpec = modalService.createTicketModal(customId);
        return presentModalSpec.map(event::presentModal).orElseGet(Mono::empty);

    }

    private Mono<Void> handleCloseTicket(ButtonInteractionEvent event, String customId) {
        String creatorIdStr = customId.substring("close_button_".length());
        Snowflake creatorId = Snowflake.of(creatorIdStr);
        Snowflake userId = event.getInteraction().getUser().getId();
        Snowflake adminRole = Snowflake.of(fileFacade.integration().discord().ticket().permissionRole());

        boolean isCreator = userId.equals(creatorId);
        boolean hasRole = event.getInteraction().getMember()
                .map(member -> member.getRoleIds().contains(adminRole))
                .orElse(false);

        if (!isCreator && !hasRole) {
            return event.reply("❌ Только создатель тикета или модератор может его закрыть")
                    .withEphemeral(true)
                    .then();
        }

        return event.deferReply()
                .withEphemeral(true)
                .then(event.getInteraction().getChannel()
                        .flatMap(channel -> {
                            if (channel instanceof ThreadChannel thread) {
                                return disableAllButtons(event.getMessageId(), thread)
                                        .then(thread.edit()
                                                .withArchived(true)
                                                .withLocked(true)
                                                .withAppliedTags(getClosedTags(thread)))
                                        .then(event.editReply("✅ Тикет закрыт"))
                                        .then();
                            }
                            return event.editReply("❌ Это не тред").then();
                        })
                )
                .then();
    }

    private Mono<Void> disableAllButtons(Snowflake messageId, ThreadChannel thread) {
        return thread.getMessageById(messageId)
                .flatMap(message -> {
                    List<TopLevelMessageComponent> components = message.getComponents();
                    List<TopLevelMessageComponent> disabledComponents = new ArrayList<>();

                    for (TopLevelMessageComponent component : components) {
                        if (component instanceof ActionRow actionRow) {
                            List<Button> disabledChildren = new ArrayList<>();

                            for (MessageComponent child : actionRow.getChildren()) {
                                if (child instanceof Button button) {
                                    disabledChildren.add(button.disabled(true));
                                }
                            }

                            disabledComponents.add(ActionRow.of(disabledChildren));
                        } else {
                            disabledComponents.add(component);
                        }
                    }

                    return message.edit()
                            .withComponentsOrNull(disabledComponents.isEmpty() ? null : disabledComponents)
                            .then();
                })
                .onErrorResume(e -> Mono.empty());
    }

    private Set<Snowflake> getClosedTags(ThreadChannel thread) {
        Set<Snowflake> appliedTags = new HashSet<>(thread.getAppliedTagsIds());
        appliedTags.add(Snowflake.of(fileFacade.integration().discord().ticket().closeTagId()));
        return appliedTags;
    }
}
