package net.flectone.bot.module.discord;

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
import discord4j.rest.service.ApplicationService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.module.Bot;
import net.flectone.bot.module.discord.command.BaseCommand;
import net.flectone.bot.module.discord.listener.ButtonListener;
import net.flectone.bot.module.discord.listener.ChatInputInteractionListener;
import net.flectone.bot.module.discord.listener.MessageCreateListener;
import net.flectone.bot.module.discord.listener.ModalSubmitInteractionListener;
import net.flectone.bot.module.discord.register.CommandRegistry;
import net.flectone.bot.module.discord.register.ListenerRegistry;
import net.flectone.bot.module.discord.sender.MessageSender;
import net.flectone.bot.processing.SystemVariableResolver;
import net.flectone.bot.util.file.FileFacade;
import org.apache.logging.log4j.Logger;

import java.util.List;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class DiscordBot implements Bot {

    private final FileFacade fileFacade;
    private final SystemVariableResolver systemVariableResolver;
    private final ListenerRegistry listenerRegistry;
    private final CommandRegistry commandRegistry;
    private final Logger logger;
    private final Injector injector;

    @Getter private DiscordClient discordClient;
    @Getter private GatewayDiscordClient gateway;
    @Getter private ApplicationService applicationService;
    @Getter private long clientID;

    public Integration.Discord config() {
        return fileFacade.integration().discord();
    }

    @Override
    public void startup() {
        String token = systemVariableResolver.substituteEnvVars(config().token());
        if (token.isEmpty()) return;

        discordClient = DiscordClient.create(token);

        gateway = discordClient.gateway().login().block();
        if (gateway == null) return;

        Integration.Discord.Presence presence = config().presence();

        if (presence.enable()) {
            Integration.Discord.Presence.Activity activity = presence.activity();

            ClientActivity clientActivity = activity.enable()
                    ? ClientActivity.of(Activity.Type.valueOf(activity.type()), activity.name(), activity.url())
                    : null;

            gateway.updatePresence(ClientPresence.of(Status.valueOf(presence.status()), clientActivity)).block();
        }

        ApplicationInfo applicationInfo = gateway.getApplicationInfo().block();
        if (applicationInfo == null) return;

        applicationService = gateway.getRestClient().getApplicationService();
        clientID = applicationInfo.getId().asLong();

        listenerRegistry.register(gateway, injector.getInstance(ButtonListener.class));
        listenerRegistry.register(gateway, injector.getInstance(ChatInputInteractionListener.class));
        listenerRegistry.register(gateway, injector.getInstance(MessageCreateListener.class));
        listenerRegistry.register(gateway, injector.getInstance(ModalSubmitInteractionListener.class));

        Snowflake guildId = Snowflake.of(fileFacade.integration().discord().guildId());

        MessageSender messageSender = injector.getInstance(MessageSender.class);

        fileFacade.integration().discord().commands().forEach(command -> {

            BaseCommand discordCommand = BaseCommand.builder()
                    .config(command)
                    .messageSender(messageSender)
                    .messages(fileFacade.integration().discord().messages())
                    .build();

            commandRegistry.registerGuild(applicationService, clientID, discordCommand, guildId);
        });

        config().channels().keySet()
                .forEach(channelID -> {
                    try {
                        List<WebhookData> botWebhooks = discordClient.getWebhookService().getChannelWebhooks(channelID)
                                .filter(data -> data.applicationId().isPresent() && data.applicationId().get().asLong() == clientID)
                                .collectList()
                                .block();

                        if (botWebhooks != null && !botWebhooks.isEmpty()) {
                            WebhookData kept = botWebhooks.getFirst();
                            for (int i = 1; i < botWebhooks.size(); i++) {
                                discordClient.getWebhookService().deleteWebhook(botWebhooks.get(i).id().asLong(), null).block();
                            }

                            messageSender.putWebhook(channelID, kept);
                        }
                    } catch (Exception e) {
                        logger.warn(e);
                    }
                });
    }

    @Override
    public void shutdown() {
        if (gateway != null) {
            logger.info("Disconnecting from Discord...");

            gateway.logout()
                    .doOnSuccess(v -> logger.info("Disconnected from Discord"))
                    .doOnError(e -> logger.error("Error during disconnect", e))
                    .block();
        }

        logger.info("Discord bot shutdown completed");
    }
}