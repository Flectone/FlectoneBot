package net.flectone.bot.discord.command;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.ImmutableApplicationCommandOptionData;
import net.flectone.bot.config.Integration;
import net.flectone.bot.discord.message.MessageSender;
import net.flectone.bot.util.Placeholders;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

public record ConfigCommand(
        Integration.Discord.Command config,
        Integration.Discord.Messages messages,
        MessageSender messageSender
) implements Command {

    @Override
    public ApplicationCommandRequest request() {
        return ApplicationCommandRequest.builder()
                .name(config.name())
                .description(config.description())
                .options(options().stream().map(this::option).toList())
                .build();
    }

    @Override
    public Mono<Void> handle(ChatInputInteractionEvent event) {
        if (!hasPermission(event, config.permissionRole())) {
            return event.reply(messages.noPermission()).withEphemeral(true).then();
        }

        Optional<Integration.Discord.Command.Option> subCommand = subCommand(event);

        Integration.WithEmbed answer = subCommand.map(Integration.WithEmbed.class::cast).orElse(config);

        if (subCommand.isPresent() && !hasPermission(event, subCommand.get().permissionRole())) {
            return event.reply(messages.noPermission()).withEphemeral(true).then();
        }

        Placeholders placeholders = placeholders(event);
        boolean ephemeral = Boolean.TRUE.equals(config.privateReply());

        return messageSender.reply(event, answer, placeholders, ephemeral)
                .switchIfEmpty(event.reply(messages.unknownCommand()).withEphemeral(true).then());
    }

    private Optional<Integration.Discord.Command.Option> subCommand(ChatInputInteractionEvent event) {
        return event.getOptions().stream()
                .filter(option -> option.getType() == ApplicationCommandOption.Type.SUB_COMMAND)
                .findFirst()
                .flatMap(option -> options().stream()
                        .filter(configured -> configured.name().equals(option.getName()))
                        .findFirst()
                );
    }

    private Placeholders placeholders(ChatInputInteractionEvent event) {
        Placeholders placeholders = Placeholders.create()
                .put("user", event.getInteraction().getUser().getMention())
                .put("username", event.getInteraction().getUser().getUsername())
                .put("skin", event.getInteraction().getUser().getAvatarUrl())
                .put("source", config.name());

        event.getInteraction().getMember().ifPresent(member -> placeholders
                .put("global_name", member.getGlobalName().orElse(""))
                .put("nickname", member.getNickname().orElse(""))
                .put("display_name", member.getDisplayName())
        );

        event.getOptions().forEach(option -> option.getValue()
                .map(ApplicationCommandInteractionOptionValue::asString)
                .ifPresent(value -> placeholders.put(option.getName(), value))
        );

        event.getOptions().stream()
                .flatMap(option -> option.getOptions().stream())
                .forEach(option -> option.getValue()
                        .map(ApplicationCommandInteractionOptionValue::asString)
                        .ifPresent(value -> placeholders.put(option.getName(), value))
                );

        return placeholders;
    }

    private boolean hasPermission(ChatInputInteractionEvent event, Long role) {
        if (role == null || role == 0) return true;

        return event.getInteraction().getMember()
                .map(member -> member.getRoleIds().contains(Snowflake.of(role)))
                .orElse(false);
    }

    private List<Integration.Discord.Command.Option> options() {
        return config.options() == null ? List.of() : config.options();
    }

    private ApplicationCommandOptionData option(Integration.Discord.Command.Option option) {
        ImmutableApplicationCommandOptionData.Builder builder = ApplicationCommandOptionData.builder()
                .type(option.type().getValue())
                .name(option.name())
                .description(option.description());

        boolean container = option.type() == ApplicationCommandOption.Type.SUB_COMMAND
                || option.type() == ApplicationCommandOption.Type.SUB_COMMAND_GROUP;

        if (!container) {
            builder.required(Boolean.TRUE.equals(option.required()));
        }

        return builder.build();
    }

}
