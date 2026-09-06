package net.flectone.bot.discord.message;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.core.event.domain.interaction.DeferrableInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.discordjson.json.MessageReferenceData;
import discord4j.rest.util.AllowedMentions;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.util.Placeholders;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class MessageSender {

    private static final int EMBEDS_LIMIT = 10;

    private static final AllowedMentions MENTION_USERS = AllowedMentions.builder()
            .parseType(AllowedMentions.Type.USER)
            .repliedUser(true)
            .build();

    private final EmbedFactory embedFactory;
    private final ButtonFactory buttonFactory;

    public Mono<Void> reply(Message target, Integration.WithEmbed config, Placeholders placeholders) {
        return reply(target, config, placeholders, StringUtils.EMPTY);
    }

    public Mono<Void> reply(Message target,
                            Integration.WithEmbed config,
                            Placeholders placeholders,
                            String customIdSuffix) {

        if (config == null) return Mono.empty();

        MessageCreateSpec spec = build(List.of(config), placeholders, customIdSuffix, target.getId().asLong());
        if (spec == null) return Mono.empty();

        return target.getChannel()
                .flatMap(channel -> channel.createMessage(spec))
                .then();
    }

    public Mono<Void> send(MessageChannel channel,
                           List<Integration.WithEmbed> configs,
                           Placeholders placeholders,
                           String customIdSuffix) {

        MessageCreateSpec spec = build(configs, placeholders, customIdSuffix, null);
        if (spec == null) return Mono.empty();

        return channel.createMessage(spec).then();
    }

    public Mono<Void> reply(DeferrableInteractionEvent event,
                            Integration.WithEmbed config,
                            Placeholders placeholders,
                            boolean ephemeral) {

        if (config == null) return Mono.empty();

        String content = placeholders.apply(config.message());
        List<EmbedCreateSpec> embeds = embedFactory.create(config.embed(), placeholders).stream().toList();
        List<Button> buttons = buttonFactory.create(config, StringUtils.EMPTY);

        if (StringUtils.isEmpty(content) && embeds.isEmpty()) {
            return Mono.empty();
        }

        return event.reply(InteractionApplicationCommandCallbackSpec.builder()
                .content(content)
                .addAllEmbeds(embeds)
                .components(buttons.isEmpty() ? List.of() : List.of(ActionRow.of(buttons)))
                .ephemeral(ephemeral)
                .build()
        ).then();
    }

    private @Nullable MessageCreateSpec build(List<Integration.WithEmbed> configs,
                                              Placeholders placeholders,
                                              String customIdSuffix,
                                              @Nullable Long replyToMessageId) {

        StringBuilder content = new StringBuilder();
        List<EmbedCreateSpec> embeds = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();

        for (Integration.WithEmbed config : configs) {
            if (config == null) continue;

            String text = placeholders.apply(config.message());
            if (StringUtils.isNotEmpty(text)) {
                content.append(content.isEmpty() ? StringUtils.EMPTY : "\n").append(text);
            }

            if (embeds.size() < EMBEDS_LIMIT) {
                embedFactory.create(config.embed(), placeholders).ifPresent(embeds::add);
            }

            buttons.addAll(buttonFactory.create(config, customIdSuffix));
        }

        if (content.isEmpty() && embeds.isEmpty()) return null;

        MessageCreateSpec.Builder builder = MessageCreateSpec.builder()
                .allowedMentions(MENTION_USERS)
                .content(content.toString())
                .addAllEmbeds(embeds);

        if (!buttons.isEmpty()) {
            builder.addComponent(ActionRow.of(buttons));
        }

        if (replyToMessageId != null) {
            builder.messageReference(MessageReferenceData.builder().messageId(replyToMessageId).build());
        }

        return builder.build();
    }

}
