package net.flectone.bot.module.discord.register;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.common.util.Snowflake;
import discord4j.rest.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.module.discord.command.Command;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class CommandRegistry {

    private final Map<String, Command> commands = new ConcurrentHashMap<>();
    private final Logger logger;

    public void registerGlobal(ApplicationService applicationService, long applicationId, Command command) {
        commands.put(command.getConfig().name(), command);

        logger.info("Registering global command in Discord: {}", command.getConfig().name());

        applicationService.createGlobalApplicationCommand(applicationId, command.getRequest())
                .doOnSuccess(cmd -> logger.info("Successfully registered global command: {}", cmd.name()))
                .doOnError(error -> logger.error("Failed to register global command: {}", command.getConfig().name(), error))
                .subscribe();
    }

    public void registerGuild(ApplicationService applicationService, long applicationId, Command command, Snowflake guildId) {
        commands.put(command.getConfig().name(), command);

        logger.info("Registering guild command in Discord: {} for guild {}", command.getConfig().name(), guildId);

        applicationService.createGuildApplicationCommand(applicationId, guildId.asLong(), command.getRequest())
                .doOnSuccess(cmd -> logger.info("Successfully registered guild command: {} for guild {}",
                        cmd.name(), guildId.asString()))
                .doOnError(error -> logger.error("Failed to register guild command: {} for guild {}",
                        command.getConfig().name(), guildId.asString(), error))
                .subscribe();
    }

    public List<String> getRegisteredCommands() {
        return new ArrayList<>(commands.keySet());
    }

    public Optional<Command> getCommand(String name) {
        return Optional.ofNullable(commands.get(name));
    }

    public boolean hasCommand(String name) {
        return commands.containsKey(name);
    }

}
