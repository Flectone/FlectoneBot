package net.flectone.bot.module.discord.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.core.object.component.Label;
import discord4j.core.object.component.TextInput;
import discord4j.core.spec.InteractionPresentModalSpec;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.util.file.FileFacade;

import java.util.Optional;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class ModalService {

    private final FileFacade fileFacade;

    public Optional<InteractionPresentModalSpec> createTicketModal(String id) {
        Integration.Discord.Ticket ticket = fileFacade.integration().discord().ticket();

        Integration.Discord.Ticket.Modal modal = ticket.modals().get(id);
        if (modal == null || modal.textInputs().isEmpty()) return Optional.empty();

        InteractionPresentModalSpec.Builder builder = InteractionPresentModalSpec.builder()
                .title(modal.name())
                .customId(id);

        for (Integration.Discord.Ticket.Modal.TextInput textInput : modal.textInputs()) {
            TextInput textInputComponent = TextInput.of(
                    textInput.style(),
                    textInput.id(),
                    null,
                    textInput.maxLength() > 0 ? textInput.maxLength() : null,
                    null,
                    textInput.placeholder()
            );

            if (Boolean.TRUE.equals(textInput.required())) {
                textInputComponent = textInputComponent.required();
            }

            builder.addComponent(Label.of(textInput.name(), textInputComponent));
        }

        return Optional.of(builder.build());
    }

}