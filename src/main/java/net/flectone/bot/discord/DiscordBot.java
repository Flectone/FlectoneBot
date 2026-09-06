package net.flectone.bot.discord;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.ApplicationInfo;
import discord4j.core.object.presence.Activity;
import discord4j.core.object.presence.ClientActivity;
import discord4j.core.object.presence.ClientPresence;
import discord4j.core.object.presence.Status;
import discord4j.discordjson.json.WebhookData;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.core.Bot;
import net.flectone.bot.discord.command.CommandRegistry;
import net.flectone.bot.discord.command.ConfigCommand;
import net.flectone.bot.discord.listener.AiListener;
import net.flectone.bot.discord.listener.BridgeListener;
import net.flectone.bot.discord.listener.ButtonListener;
import net.flectone.bot.discord.listener.CommandListener;
import net.flectone.bot.discord.listener.DumpListener;
import net.flectone.bot.discord.listener.EventListener;
import net.flectone.bot.discord.listener.ForumListener;
import net.flectone.bot.discord.listener.HoneypotListener;
import net.flectone.bot.discord.listener.ListenerRegistry;
import net.flectone.bot.discord.message.MessageSender;
import net.flectone.bot.discord.message.WebhookSender;
import net.flectone.bot.file.FileFacade;
import net.flectone.bot.util.SystemVariableResolver;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class DiscordBot implements Bot {

    private static final List<Class<? extends EventListener<?>>> LISTENERS = List.of(
            ButtonListener.class,
            CommandListener.class,
            HoneypotListener.class,
            ForumListener.class,
            DumpListener.class,
            AiListener.class,
            BridgeListener.class
    );

    private final FileFacade fileFacade;
    private final SystemVariableResolver systemVariableResolver;
    private final ListenerRegistry listenerRegistry;
    private final CommandRegistry commandRegistry;
    private final MessageSender messageSender;
    private final Injector injector;
    private final Logger logger;

    @Getter private @Nullable DiscordClient discordClient;
    @Getter private @Nullable GatewayDiscordClient gateway;
    @Getter private long clientId;

    @Override
    public String name() {
        return "Discord";
    }

    @Override
    public void startup() {
        String token = systemVariableResolver.substitute(config().token());
        if (StringUtils.isEmpty(token)) {
            logger.info("Discord token is missing, the bot stays off");
            return;
        }

        discordClient = DiscordClient.create(token);
        gateway = discordClient.gateway().login().block();
        if (gateway == null) {
            logger.error("Discord login failed");
            return;
        }

        ApplicationInfo applicationInfo = gateway.getApplicationInfo().block();
        if (applicationInfo == null) {
            logger.error("Discord did not tell who this bot is");
            return;
        }

        clientId = applicationInfo.getId().asLong();

        updatePresence();
        registerListeners();
        registerCommands();
        prepareWebhooks();
    }

    @Override
    public void shutdown() {
        listenerRegistry.unregisterAll();
        commandRegistry.clear();

        if (gateway == null) return;

        gateway.logout().block();
        gateway = null;
    }

    @Override
    public boolean isEnabled() {
        return gateway != null;
    }

    public Integration.Discord config() {
        return fileFacade.discord();
    }

    private void updatePresence() {
        Integration.Discord.Presence presence = config().presence();
        if (presence == null || !Boolean.TRUE.equals(presence.enable())) return;

        Integration.Discord.Presence.Activity activity = presence.activity();

        ClientActivity clientActivity = activity != null && Boolean.TRUE.equals(activity.enable())
                ? ClientActivity.of(Activity.Type.valueOf(activity.type()), activity.name(), activity.url())
                : null;

        gateway.updatePresence(ClientPresence.of(Status.valueOf(presence.status()), clientActivity)).block();
    }

    private void registerListeners() {
        LISTENERS.forEach(this::registerListener);
    }

    private <E extends discord4j.core.event.domain.Event> void registerListener(Class<? extends EventListener<?>> type) {
        @SuppressWarnings("unchecked")
        EventListener<E> listener = (EventListener<E>) injector.getInstance(type);

        listenerRegistry.register(gateway, listener);
    }

    private void registerCommands() {
        if (config().commands() == null || config().guildId() == null) return;

        Snowflake guildId = Snowflake.of(config().guildId());

        config().commands().forEach(command -> commandRegistry.register(
                gateway.getRestClient().getApplicationService(),
                clientId,
                guildId,
                new ConfigCommand(command, config().messages(), messageSender)
        ));
    }

    private void prepareWebhooks() {
        if (config().channels() == null) return;

        WebhookSender webhookSender = injector.getInstance(WebhookSender.class);

        config().channels().keySet().forEach(channelId -> {
            try {
                List<WebhookData> webhooks = discordClient.getWebhookService()
                        .getChannelWebhooks(channelId)
                        .filter(data -> data.applicationId()
                                .map(id -> id.asLong() == clientId)
                                .orElse(false))
                        .collectList()
                        .block();

                if (webhooks == null || webhooks.isEmpty()) return;

                webhookSender.remember(channelId, webhooks.getFirst());

                webhooks.stream()
                        .skip(1)
                        .forEach(webhook -> discordClient.getWebhookService()
                                .deleteWebhook(webhook.id().asLong(), null)
                                .subscribe(null, error -> logger.warn("Failed to delete a stale webhook", error))
                        );
            } catch (Exception e) {
                logger.warn("Failed to prepare the webhook of channel {}", channelId, e);
            }
        });
    }

}
