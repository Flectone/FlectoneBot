package net.flectone.bot.discord.message;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.ForumChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.WebhookCreateSpec;
import discord4j.discordjson.json.AllowedMentionsData;
import discord4j.discordjson.json.WebhookData;
import discord4j.discordjson.json.ImmutableWebhookExecuteRequest;
import discord4j.discordjson.json.WebhookExecuteRequest;
import discord4j.rest.util.AllowedMentions;
import discord4j.rest.util.MultipartRequest;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.BuildConfig;
import net.flectone.bot.config.Integration;
import net.flectone.bot.discord.DiscordBot;
import net.flectone.bot.util.Placeholders;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class WebhookSender {

    private static final String THREAD_SEPARATOR = "_";

    private final Map<Long, WebhookData> webhooks = new ConcurrentHashMap<>();

    private final DiscordBot discordBot;
    private final EmbedFactory embedFactory;
    private final Logger logger;

    public void remember(long channelId, WebhookData webhook) {
        webhooks.put(channelId, webhook);
    }

    public void send(String sender, String channel, Integration.WithEmbed config, Placeholders placeholders) {
        if (config == null) return;

        Snowflake channelId = channelId(channel);
        Snowflake threadId = threadId(channel);

        String content = placeholders.apply(config.message());
        Optional<EmbedCreateSpec> embed = embedFactory.create(config.embed(), placeholders);
        if (StringUtils.isEmpty(content) && embed.isEmpty()) return;

        String avatar = placeholders.apply(config.webhookAvatar());
        if (StringUtils.isEmpty(avatar)) {
            sendAsBot(channelId, threadId, content, embed);
            return;
        }

        WebhookData webhook = webhook(channelId.asLong());
        if (webhook == null) {
            sendAsBot(channelId, threadId, content, embed);
            return;
        }

        ImmutableWebhookExecuteRequest.Builder request = WebhookExecuteRequest.builder()
                .allowedMentions(AllowedMentionsData.builder().build())
                .username(sender)
                .avatarUrl(avatar)
                .content(content);

        embed.ifPresent(spec -> request.addEmbed(spec.asRequest()));

        long webhookId = webhook.id().asLong();
        String token = webhook.token().toOptional().orElse(StringUtils.EMPTY);
        MultipartRequest<WebhookExecuteRequest> body = MultipartRequest.ofRequest(request.build());

        if (threadId == null) {
            discordBot.getDiscordClient().getWebhookService()
                    .executeWebhook(webhookId, token, false, body)
                    .subscribe(null, error -> logger.warn("Webhook message failed", error));
            return;
        }

        discordBot.getDiscordClient().getWebhookService()
                .executeWebhook(webhookId, token, false, threadId.asLong(), body)
                .subscribe(null, error -> logger.warn("Webhook message failed", error));
    }

    private void sendAsBot(Snowflake channelId, @Nullable Snowflake threadId, String content, Optional<EmbedCreateSpec> embed) {
        MessageCreateSpec.Builder builder = MessageCreateSpec.builder()
                .allowedMentions(AllowedMentions.suppressAll())
                .content(content);

        embed.ifPresent(builder::addEmbed);

        MessageCreateSpec spec = builder.build();

        if (threadId == null) {
            discordBot.getDiscordClient().getChannelById(channelId)
                    .createMessage(spec.asRequest())
                    .subscribe(null, error -> logger.warn("Bridge message failed", error));
            return;
        }

        discordBot.getGateway().getChannelById(channelId)
                .ofType(ForumChannel.class)
                .flatMapMany(ForumChannel::getActiveThreads)
                .filter(thread -> thread.getId().equals(threadId))
                .next()
                .flatMap(thread -> thread.createMessage(spec))
                .subscribe(null, error -> logger.warn("Bridge message failed", error));
    }

    private @Nullable WebhookData webhook(long channelId) {
        return webhooks.computeIfAbsent(channelId, this::createWebhook);
    }

    private @Nullable WebhookData createWebhook(long channelId) {
        try {
            WebhookCreateSpec spec = WebhookCreateSpec.builder()
                    .name(BuildConfig.PROJECT_NAME + "Webhook")
                    .build();

            return discordBot.getDiscordClient().getWebhookService()
                    .createWebhook(channelId, spec.asRequest(), null)
                    .block();
        } catch (Exception e) {
            logger.warn("Failed to create a webhook in channel {}", channelId, e);
            return null;
        }
    }

    private Snowflake channelId(String channel) {
        return Snowflake.of(StringUtils.substringBefore(channel, THREAD_SEPARATOR));
    }

    private @Nullable Snowflake threadId(String channel) {
        String threadId = StringUtils.substringAfter(channel, THREAD_SEPARATOR);
        return threadId.isEmpty() ? null : Snowflake.of(threadId);
    }

}
