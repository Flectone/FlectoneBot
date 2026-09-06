package net.flectone.bot.discord.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.common.util.Snowflake;
import discord4j.rest.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class CommandRegistry {

    private final Map<String, Command> commands = new ConcurrentHashMap<>();

    private final Logger logger;

    public void register(ApplicationService applicationService, long applicationId, Snowflake guildId, Command command) {
        String name = command.config().name();
        commands.put(name, command);

        applicationService.createGuildApplicationCommand(applicationId, guildId.asLong(), command.request())
                .subscribe(
                        registered -> logger.info("Registered command /{}", registered.name()),
                        error -> logger.error("Failed to register command /{}", name, error)
                );
    }

    public Optional<Command> get(String name) {
        return Optional.ofNullable(commands.get(name));
    }

    public void clear() {
        commands.clear();
    }

}
