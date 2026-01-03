package net.flectone.bot.module.discord.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.common.util.Snowflake;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.MessageComponent;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.WebhookCreateSpec;
import discord4j.discordjson.json.*;
import discord4j.rest.util.AllowedMentions;
import discord4j.rest.util.MultipartRequest;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.BuildConfig;
import net.flectone.bot.config.Integration;
import net.flectone.bot.module.discord.DiscordBot;
import org.apache.commons.lang3.StringUtils;

import java.awt.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class WebhookService {

    private final Map<Long, WebhookData> channelWebhooks = new HashMap<>();
    private final DiscordBot discordBot;

    public void sendMessage(Snowflake channel, String text) {
        MessageCreateSpec.Builder messageCreateSpecBuilder = MessageCreateSpec.builder()
                .allowedMentions(AllowedMentions.suppressAll())
                .content(text);

        discordBot.getDiscordClient().getChannelById(channel)
                .createMessage(messageCreateSpecBuilder.build().asRequest())
                .subscribe();
    }

    public void sendMessage(String sender, Snowflake channel, Integration.Discord.WithEmbed channelEmbed, UnaryOperator<String> discordString, List<Button> buttons) {
        if (channelEmbed == null) return;

        Integration.Discord.Embed messageEmbed = channelEmbed.embed();

        EmbedCreateSpec embed = null;
        if (messageEmbed != null) {
            embed = createEmbed(messageEmbed, discordString);
        }

        String webhookAvatar = channelEmbed.webhookAvatar();
        if (StringUtils.isNotEmpty(webhookAvatar)) {
            long channelID = channel.asLong();

            WebhookData webhookData = channelWebhooks.get(channelID);

            if (webhookData == null) {
                webhookData = createWebhook(channelID);
                if (webhookData == null) return;

                channelWebhooks.put(channelID, webhookData);
            }

            ImmutableWebhookExecuteRequest.Builder webhookBuilder = WebhookExecuteRequest.builder()
                    .allowedMentions(AllowedMentionsData.builder().build())
                    .username(sender)
                    .avatarUrl(discordString.apply(webhookAvatar))
                    .content(discordString.apply(channelEmbed.message()));

            if (embed != null) {
                webhookBuilder.addEmbed(embed.asRequest());
            }

            if (!buttons.isEmpty()) {
                List<ComponentData> buttonData = buttons.stream()
                        .map(btn -> ComponentData.builder()
                                .type(MessageComponent.Type.BUTTON.getValue())
                                .style(btn.getStyle().getValue())
                                .customId(btn.getCustomId())
                                .label(btn.getLabel().get())
                                .build()
                        )
                        .collect(Collectors.toList());

                webhookBuilder.addAllComponents(buttonData);
            }

            discordBot.getDiscordClient().getWebhookService().executeWebhook(
                    webhookData.id().asLong(),
                    webhookData.token().get(),
                    false,
                    MultipartRequest.ofRequest(webhookBuilder.build())
            ).subscribe();

            return;
        }

        MessageCreateSpec.Builder messageCreateSpecBuilder = MessageCreateSpec.builder().allowedMentions(AllowedMentions.suppressAll());

        if (embed != null) {
            messageCreateSpecBuilder.addEmbed(embed);
        }

        String content = discordString.apply(channelEmbed.message());
        if (StringUtils.isEmpty(content) && embed == null) return;

        messageCreateSpecBuilder.content(content).components(ActionRow.of(buttons));

        discordBot.getDiscordClient().getChannelById(channel)
                .createMessage(messageCreateSpecBuilder.build().asRequest())
                .subscribe();
    }

    private WebhookData createWebhook(long channelID) {
        WebhookCreateSpec.Builder builder = WebhookCreateSpec.builder()
                .name(BuildConfig.PROJECT_NAME + "Webhook");

        WebhookCreateSpec webhook = builder.build();

        return discordBot.getDiscordClient().getWebhookService().createWebhook(channelID, webhook.asRequest(), null).block();
    }

    public EmbedCreateSpec createEmbed(Integration.Discord.Embed embed, UnaryOperator<String> discordString) {
        EmbedCreateSpec.Builder embedBuilder = EmbedCreateSpec.builder();

        if (StringUtils.isNotEmpty(embed.color())) {
            Color color = Color.decode(embed.color());
            embedBuilder.color(discord4j.rest.util.Color.of(color.getRGB()));
        }

        if (StringUtils.isNotEmpty(embed.title())) {
            embedBuilder.title(discordString.apply(embed.title()));
        }

        if (StringUtils.isNotEmpty(embed.url())) {
            embedBuilder.url(discordString.apply(embed.url()));
        }

        Integration.Discord.Embed.Author author = embed.author();
        if (author != null && (StringUtils.isNotEmpty(author.name())
                || StringUtils.isNotEmpty(author.url())
                || StringUtils.isNotEmpty(author.iconUrl()))) {
            embedBuilder.author(
                    discordString.apply(author.name()),
                    discordString.apply(author.url()),
                    discordString.apply(author.iconUrl())
            );
        }

        if (StringUtils.isNotEmpty(embed.description())) {
            embedBuilder.description(discordString.apply(embed.description()));
        }

        if (StringUtils.isNotEmpty(embed.thumbnail())) {
            embedBuilder.thumbnail(discordString.apply(embed.thumbnail()));
        }

        if (StringUtils.isNotEmpty(embed.image())) {
            embedBuilder.image(discordString.apply(embed.image()));
        }

        if (Boolean.TRUE.equals(embed.timestamp())) {
            embedBuilder.timestamp(Instant.now());
        }

        Integration.Discord.Embed.Footer footer = embed.footer();
        if (footer != null && (StringUtils.isNotEmpty(footer.text()) || StringUtils.isNotEmpty(footer.iconUrl()))) {
            embedBuilder.footer(
                    discordString.apply(footer.text()),
                    discordString.apply(footer.iconUrl())
            );
        }

        if (embed.fields() != null && !embed.fields().isEmpty()) {
            for (Integration.Discord.Embed.Field field : embed.fields()) {
                if (StringUtils.isEmpty(field.name()) || StringUtils.isEmpty(field.value())) continue;

                embedBuilder.addField(discordString.apply(field.name()), discordString.apply(field.value()), field.inline());
            }
        }

        return embedBuilder.build();
    }

}
