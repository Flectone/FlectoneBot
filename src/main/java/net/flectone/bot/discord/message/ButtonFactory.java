package net.flectone.bot.discord.message;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.core.object.component.Button;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class ButtonFactory {

    public List<Button> create(Integration.WithEmbed config, String customIdSuffix) {
        if (config == null || config.buttons() == null) return List.of();

        return config.buttons().stream()
                .filter(button -> StringUtils.isNotEmpty(button.id()))
                .map(button -> create(button, customIdSuffix))
                .toList();
    }

    private Button create(Integration.Discord.Button config, String customIdSuffix) {
        String customId = config.id() + StringUtils.defaultString(customIdSuffix);

        return switch (config.style()) {
            case SUCCESS -> Button.success(customId, config.convertEmoji(), config.name());
            case DANGER -> Button.danger(customId, config.convertEmoji(), config.name());
            case SECONDARY -> Button.secondary(customId, config.convertEmoji(), config.name());
            default -> Button.primary(customId, config.convertEmoji(), config.name());
        };
    }

}
