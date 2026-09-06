package net.flectone.bot.discord.forum;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.component.TopLevelMessageComponent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.ThreadChannel;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.discord.message.MessageSender;
import net.flectone.bot.file.FileFacade;
import net.flectone.bot.pulse.DumpPresenter;
import net.flectone.bot.pulse.DumpReader;
import net.flectone.bot.pulse.PasteLink;
import net.flectone.bot.pulse.PulseDump;
import net.flectone.bot.util.Placeholders;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class ForumService {

    public static final String CLOSE_BUTTON_ID = "close_button";

    private static final String ID_SEPARATOR = "_";
    private static final Object PRESENT = new Object();

    private final Cache<String, Object> answeredPastes = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(500)
            .build();

    private final FileFacade fileFacade;
    private final MessageSender messageSender;
    private final DumpReader dumpReader;
    private final DumpPresenter dumpPresenter;
    private final Logger logger;

    public boolean isWatched(ThreadChannel post) {
        Integration.Discord.Forum forum = config();
        if (forum == null || forum.forumIds() == null) return false;

        return post.getParentId()
                .map(parentId -> forum.forumIds().contains(parentId.asLong()))
                .orElse(false);
    }

    public Optional<ThreadChannel> watchedPost(MessageChannel channel) {
        if (!(channel instanceof ThreadChannel post)) return Optional.empty();

        return isWatched(post) ? Optional.of(post) : Optional.empty();
    }

    public Mono<Void> welcome(ThreadChannel post, @Nullable Message starter) {
        Integration.Discord.Forum forum = config();
        if (forum == null) return Mono.empty();

        List<Integration.WithEmbed> answers = new ArrayList<>();
        if (forum.created() != null) {
            answers.add(forum.created());
        }

        answers.addAll(tagAnswers(post, forum));
        if (answers.isEmpty()) return Mono.empty();

        return messageSender.send(post, answers, placeholders(post, starter), closeSuffix(post.getStarterId()));
    }

    public Mono<Void> answerStarter(ThreadChannel post, Message starter) {
        Integration.Discord.Forum forum = config();
        if (forum == null) return Mono.empty();

        if (dumpReader.findLink(starter.getContent()).isEmpty()) {
            return messageSender.reply(starter, forum.noLink(), placeholders(post, starter));
        }

        return answerDump(starter, post);
    }

    public Mono<Void> answerDump(Message message, @Nullable ThreadChannel post) {
        Integration.Discord.Forum forum = config();
        if (forum == null) return Mono.empty();

        Optional<PasteLink> link = dumpReader.findLink(message.getContent());
        if (link.isEmpty()) return Mono.empty();

        long channelId = message.getChannelId().asLong();
        if (!claim(channelId, link.get().key())) return Mono.empty();

        return Mono.fromCallable(() -> dumpReader.read(link.get().key()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(dump -> dump
                        .map(value -> reply(message, post, link.get(), value))
                        .orElseGet(() -> {
                            release(channelId, link.get().key());
                            return post == null
                                    ? Mono.empty()
                                    : messageSender.reply(message, forum.error(), placeholders(post, message));
                        })
                );
    }

    public Mono<Void> close(ButtonInteractionEvent event, String customId) {
        Integration.Discord.Messages messages = fileFacade.discord().messages();
        Snowflake author = Snowflake.of(customId.substring((CLOSE_BUTTON_ID + ID_SEPARATOR).length()));

        if (!canClose(event, author)) {
            return event.reply(messages.postCloseNoPermission()).withEphemeral(true).then();
        }

        return event.deferReply()
                .withEphemeral(true)
                .then(event.getInteraction().getChannel())
                .flatMap(channel -> channel instanceof ThreadChannel post
                        ? closePost(event, post).then(event.editReply(messages.postClosed()))
                        : event.editReply(messages.notPost())
                )
                .then();
    }

    public Placeholders placeholders(@Nullable ThreadChannel post, @Nullable Message message) {
        Placeholders placeholders = Placeholders.create();

        Optional<discord4j.core.object.entity.User> author = message == null ? Optional.empty() : message.getAuthor();

        author.ifPresent(user -> placeholders
                .put("user", user.getMention())
                .put("username", user.getUsername())
                .put("skin", user.getAvatarUrl())
        );

        if (post != null) {
            if (author.isEmpty()) {
                placeholders.put("user", "<@" + post.getStarterId().asString() + ">");
            }

            placeholders
                    .put("name", post.getName())
                    .put("thread", post.getMention());
        }

        return placeholders;
    }

    private Mono<Void> reply(Message message, @Nullable ThreadChannel post, PasteLink link, PulseDump dump) {
        logger.info("Answered a dump of FlectonePulse {} ({}) in channel {}",
                dump.projectVersion(), dumpPresenter.status(dump), message.getChannelId().asLong());

        Placeholders placeholders = placeholders(post, message).putAll(dumpPresenter.present(dump, link));

        return messageSender.reply(message, config().dump(), placeholders);
    }

    private boolean claim(long channelId, String pasteKey) {
        return answeredPastes.asMap().putIfAbsent(pasteKey(channelId, pasteKey), PRESENT) == null;
    }

    private void release(long channelId, String pasteKey) {
        answeredPastes.invalidate(pasteKey(channelId, pasteKey));
    }

    private String pasteKey(long channelId, String pasteKey) {
        return channelId + ID_SEPARATOR + pasteKey;
    }

    private String closeSuffix(Snowflake authorId) {
        return ID_SEPARATOR + authorId.asString();
    }

    private List<Integration.WithEmbed> tagAnswers(ThreadChannel post, Integration.Discord.Forum forum) {
        Map<Long, Integration.Discord.EmbedMessage> tags = forum.tags();
        if (tags == null || tags.isEmpty()) return List.of();

        Set<Long> applied = new HashSet<>();
        post.getAppliedTagsIds().forEach(tagId -> applied.add(tagId.asLong()));

        return tags.entrySet().stream()
                .filter(entry -> applied.contains(entry.getKey()))
                .map(entry -> (Integration.WithEmbed) entry.getValue())
                .toList();
    }

    private boolean canClose(ButtonInteractionEvent event, Snowflake author) {
        if (event.getInteraction().getUser().getId().equals(author)) return true;

        Long permissionRole = config() == null ? null : config().permissionRole();
        if (permissionRole == null || permissionRole == 0) return false;

        return event.getInteraction().getMember()
                .map(member -> member.getRoleIds().contains(Snowflake.of(permissionRole)))
                .orElse(false);
    }

    private Mono<Void> closePost(ButtonInteractionEvent event, ThreadChannel post) {
        return disableButtons(event.getMessageId(), post)
                .then(post.edit()
                        .withArchived(true)
                        .withLocked(true)
                        .withAppliedTags(closedTags(post))
                )
                .then();
    }

    private Mono<Void> disableButtons(Snowflake messageId, ThreadChannel post) {
        return post.getMessageById(messageId)
                .flatMap(message -> {
                    List<TopLevelMessageComponent> components = new ArrayList<>();

                    for (TopLevelMessageComponent component : message.getComponents()) {
                        if (!(component instanceof ActionRow row)) {
                            components.add(component);
                            continue;
                        }

                        List<Button> buttons = row.getChildren().stream()
                                .filter(Button.class::isInstance)
                                .map(Button.class::cast)
                                .map(button -> button.disabled(true))
                                .toList();

                        components.add(ActionRow.of(buttons));
                    }

                    return message.edit()
                            .withComponentsOrNull(components.isEmpty() ? null : components)
                            .then();
                })
                .onErrorResume(error -> Mono.empty());
    }

    private Set<Snowflake> closedTags(ThreadChannel post) {
        Set<Snowflake> tags = new HashSet<>(post.getAppliedTagsIds());

        Long closeTagId = config() == null ? null : config().closeTagId();
        if (closeTagId != null && closeTagId != 0) {
            tags.add(Snowflake.of(closeTagId));
        }

        return tags;
    }

    private Integration.Discord.@Nullable Forum config() {
        return fileFacade.forum();
    }

}
