package net.flectone.bot.module.discord.command;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.ImmutableApplicationCommandOptionData;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import net.flectone.bot.config.Integration;
import net.flectone.bot.module.discord.sender.MessageSender;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Getter
@SuperBuilder
public class BaseCommand implements Command {

    protected final Integration.Discord.Command config;
    protected final MessageSender messageSender;
    protected final Integration.Discord.Messages messages;

    @Override
    public ApplicationCommandRequest getRequest() {
        return ApplicationCommandRequest.builder()
                .name(config.name())
                .description(config.description())
                .options(config.options().stream().map(this::buildOption).toList())
                .build();
    }

    @Override
    public Mono<Void> handle(ChatInputInteractionEvent event) {
        if (!hasPermission(event)) {
            return event.reply(messages.noPermission()).withEphemeral(true).then();
        }

        Optional<Integration.Discord.Command.Option> subCommand = event.getOptions().stream()
                .filter(opt -> opt.getType() == ApplicationCommandOption.Type.SUB_COMMAND)
                .findFirst()
                .flatMap(opt -> config.options().stream()
                        .filter(o -> o.name().equals(opt.getName()))
                        .findFirst()
                );

        if (subCommand.isEmpty()) return event.reply(messages.unknownCommand()).withEphemeral(true).then();

        return messageSender.sendMessage(
                event,
                subCommand.get(),
                config.name(),
                config.privateReply()
        );
    }

    private ApplicationCommandOptionData buildOption(Integration.Discord.Command.Option option) {
        ImmutableApplicationCommandOptionData.Builder builder = ApplicationCommandOptionData.builder()
                .type(option.type().getValue())
                .name(option.name())
                .description(option.description());

        if (option.type() != ApplicationCommandOption.Type.SUB_COMMAND &&
                option.type() != ApplicationCommandOption.Type.SUB_COMMAND_GROUP) {
            builder.required(option.required());
        }

        return builder.build();
    }

    private boolean hasPermission(ChatInputInteractionEvent event) {
        return event.getInteraction().getMember()
                .map(member -> {
                    if (config.permissionRole() == null) return true;
                    return member.getRoleIds().contains(Snowflake.of(config.permissionRole()));
                })
                .orElse(false);
    }
}