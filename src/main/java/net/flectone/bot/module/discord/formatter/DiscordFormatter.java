package net.flectone.bot.module.discord.formatter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.TextInput;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class DiscordFormatter {

    public List<Button> createButtons(Integration.Discord.WithEmbed withEmbed, String infoId) {
        if (withEmbed.buttons() == null) return Collections.emptyList();

        List<Button> buttons = new ArrayList<>();

        for (Integration.Discord.Button btnConfig : withEmbed.buttons()) {
            Button button = switch (btnConfig.style()) {
                case SECONDARY -> Button.secondary(btnConfig.id() + infoId, btnConfig.convertEmoji(), btnConfig.name());
                case SUCCESS -> Button.success(btnConfig.id() + infoId, btnConfig.convertEmoji(), btnConfig.name());
                case DANGER -> Button.danger(btnConfig.id() + infoId, btnConfig.convertEmoji(), btnConfig.name());
                default ->  Button.primary(btnConfig.id() + infoId, btnConfig.convertEmoji(), btnConfig.name());
            };

            buttons.add(button);
        }

        return buttons;
    }

    public Map<String, String> collectPlaceholders(InteractionCreateEvent event, String sourceName) {
        Map<String, String> placeholders = new HashMap<>();

        event.getInteraction().getMember().ifPresent(member -> {
            placeholders.put("global_name", member.getGlobalName().orElse(""));
            placeholders.put("nickname", member.getNickname().orElse(""));
            placeholders.put("display_name", member.getDisplayName());
            placeholders.put("username", member.getUsername());
        });

        placeholders.put("user", event.getInteraction().getUser().getMention());
        placeholders.put("skin", event.getInteraction().getUser().getAvatarUrl());
        placeholders.put("source", sourceName);
        placeholders.put("channel", event.getInteraction().getChannelId().asString());

        if (event instanceof discord4j.core.event.domain.interaction.ChatInputInteractionEvent chatEvent) {
            chatEvent.getOptions().forEach(opt ->
                    placeholders.put(opt.getName(),
                            opt.getValue().map(ApplicationCommandInteractionOptionValue::asString).orElse(""))
            );
        }

        if (event instanceof discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent modalEvent) {
            modalEvent.getComponents(TextInput.class).forEach(input ->
                    placeholders.put(input.getCustomId(), input.getValue().orElse(""))
            );
        }

        return placeholders;
    }

    public String formatMessage(String message, Map<String, String> placeholders) {
        String[] searchList = placeholders.keySet().stream()
                .map(key -> "<" + key + ">")
                .toArray(String[]::new);

        String[] replacementList = placeholders.values().toArray(new String[0]);

        return StringUtils.replaceEach(message, searchList, replacementList);
    }

}
