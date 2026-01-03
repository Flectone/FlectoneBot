package net.flectone.bot.module.discord.sender;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.module.discord.formatter.DiscordFormatter;
import net.flectone.bot.module.discord.service.WebhookService;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class MessageSender {

    private final DiscordFormatter discordFormatter;
    private final WebhookService webhookService;

    public Mono<Void> sendMessage(DeferrableInteractionEvent event,
                                  Integration.Discord.WithEmbed withEmbed,
                                  String sourceName,
                                  Map<String, String> additionalPlaceholders,
                                  boolean privateReply) {

        Map<String, String> placeholders = discordFormatter.collectPlaceholders(event, sourceName);
        placeholders.putAll(additionalPlaceholders);

        UnaryOperator<String> formatter = text -> discordFormatter.formatMessage(text, placeholders);
        List<Button> buttons = discordFormatter.createButtons(withEmbed, "");

        if (StringUtils.isNotEmpty(withEmbed.webhookAvatar())) {
            return sendViaWebhook(event, withEmbed, formatter, buttons);
        }

        return sendDirectReply(event, withEmbed, formatter, buttons, privateReply);
    }

    public Mono<Void> sendMessage(DeferrableInteractionEvent event,
                                  Integration.Discord.WithEmbed withEmbed,
                                  String sourceName,
                                  boolean privateReply) {
        return sendMessage(event, withEmbed, sourceName, Collections.emptyMap(), privateReply);
    }

    private Mono<Void> sendViaWebhook(DeferrableInteractionEvent event,
                                      Integration.Discord.WithEmbed withEmbed,
                                      UnaryOperator<String> formatter,
                                      List<Button> buttons) {

        String senderName = event.getInteraction().getUser().getUsername();
        Snowflake channelId = event.getInteraction().getChannelId();

        return event.deferReply()
                .withEphemeral(true)
                .then(Mono.fromRunnable(() ->
                        webhookService.sendMessage(senderName, channelId, withEmbed, formatter, buttons)
                ))
                .then(event.deleteReply());
    }

    private Mono<Void> sendDirectReply(DeferrableInteractionEvent event,
                                       Integration.Discord.WithEmbed withEmbed,
                                       UnaryOperator<String> formatter,
                                       List<Button> buttons,
                                       boolean privateReply) {

        return event.reply(
                InteractionApplicationCommandCallbackSpec.builder()
                        .content(StringUtils.isNotEmpty(withEmbed.message()) ?
                                formatter.apply(withEmbed.message()) : "")
                        .addAllEmbeds(withEmbed.embed() != null ?
                                List.of(webhookService.createEmbed(Objects.requireNonNull(withEmbed.embed()), formatter)) : List.of())
                        .components(!buttons.isEmpty() ?
                                List.of(ActionRow.of(buttons)) : List.of())
                        .ephemeral(privateReply)
                        .build()
        ).then();
    }

}