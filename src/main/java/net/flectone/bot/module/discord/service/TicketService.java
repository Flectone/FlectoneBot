package net.flectone.bot.module.discord.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.common.util.Snowflake;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.ForumChannel;
import discord4j.core.object.entity.channel.ThreadChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.ForumThreadMessageCreateSpec;
import discord4j.core.spec.StartThreadInForumChannelSpec;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.module.discord.DiscordBot;
import net.flectone.bot.module.discord.formatter.DiscordFormatter;
import net.flectone.bot.module.discord.sender.MessageSender;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class TicketService {

    private final DiscordBot discordBot;
    private final DiscordFormatter discordFormatter;
    private final MessageSender messageSender;

    public Mono<ThreadChannel> createForumThread(String modalId,
                                                 Map<String, String> formData,
                                                 User user,
                                                 UnaryOperator<String> formatter) {

        Integration.Discord.Ticket.Modal modalConfig = discordBot.config().ticket().modals().get(modalId);
        if (modalConfig == null) return Mono.empty();

        Snowflake forumId = Snowflake.of(modalConfig.forumId());
        Snowflake tagId = Snowflake.of(modalConfig.tagId());

        Integration.Discord.Embed embedConfig = modalConfig.embed();
        EmbedCreateSpec embed = embedConfig != null ?
                messageSender.createEmbed(embedConfig, formatter) :
                createDefaultEmbed(modalConfig, formatter);

        String threadName = formatter.apply(formData.getOrDefault("name", modalConfig.name()));
        if (threadName.length() > 100) {
            threadName = threadName.substring(0, 97) + "...";
        }

        String finalThreadName = threadName;
        return discordBot.getGateway()
                .getChannelById(forumId)
                .ofType(ForumChannel.class)
                .flatMap(forum -> {

                    ForumThreadMessageCreateSpec.Builder messageThreadBuilder = ForumThreadMessageCreateSpec.builder()
                            .addEmbed(embed);

                    List<Button> buttons = discordFormatter.createButtons(modalConfig, "_" + user.getId().asString());
                    if (!buttons.isEmpty()) {
                        messageThreadBuilder.addComponent(ActionRow.of(buttons));
                    }

                    StartThreadInForumChannelSpec.Builder threadBuilder = StartThreadInForumChannelSpec.builder()
                            .name(finalThreadName)
                            .autoArchiveDuration(ThreadChannel.AutoArchiveDuration.DURATION4)
                            .message(messageThreadBuilder.build())
                            .appliedTags(tagId);

                    return forum.startThread(threadBuilder.build())
                            .flatMap(thread -> thread.addMember(user).thenReturn(thread));
                });
    }

    private EmbedCreateSpec createDefaultEmbed(Integration.Discord.Ticket.Modal modalConfig, UnaryOperator<String> formatter) {
        return EmbedCreateSpec.builder()
                .title(modalConfig.name())
                .description(formatter.apply(modalConfig.message()))
                .build();
    }

}