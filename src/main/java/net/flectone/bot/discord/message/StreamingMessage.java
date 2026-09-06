package net.flectone.bot.discord.message;

import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.discordjson.json.MessageReferenceData;
import discord4j.rest.util.AllowedMentions;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class StreamingMessage {

    private static final int MESSAGE_LIMIT = 1900;

    private static final AllowedMentions PING_AUTHOR = AllowedMentions.builder()
            .repliedUser(true)
            .build();
    private static final long EDIT_INTERVAL_MILLIS = 1000;

    private final List<Message> messages = new ArrayList<>();
    private final StringBuilder text = new StringBuilder();
    private final AtomicBoolean rendering = new AtomicBoolean();

    private final MessageChannel channel;
    private final long replyToMessageId;
    private final String footer;
    private final Logger logger;

    private volatile boolean finished;
    private volatile String rendered = StringUtils.EMPTY;
    private volatile long lastRenderedAt;

    public StreamingMessage(MessageChannel channel, long replyToMessageId, String footer, Logger logger) {
        this.channel = channel;
        this.replyToMessageId = replyToMessageId;
        this.footer = footer;
        this.logger = logger;
    }

    public void append(String chunk) {
        synchronized (text) {
            text.append(chunk);
        }

        render(false);
    }

    public void complete() {
        if (StringUtils.isNotEmpty(footer) && StringUtils.isNotEmpty(rendered)) {
            append("\n\n" + footer);
        }

        finish();
    }

    public void fail(String error) {
        append(StringUtils.isEmpty(rendered) ? "❌ " + error : "\n\n❌ " + error);
        finish();
    }

    private void finish() {
        finished = true;
        render(true);
    }

    private void render(boolean force) {
        String current = current();
        if (current.equals(rendered) && !force) return;
        if (!force && System.currentTimeMillis() - lastRenderedAt < EDIT_INTERVAL_MILLIS) return;
        if (!rendering.compareAndSet(false, true)) return;

        rendered = current;
        lastRenderedAt = System.currentTimeMillis();

        parts(current)
                .concatMap(this::renderPart)
                .then()
                .doFinally(signal -> {
                    rendering.set(false);

                    if (finished && !current().equals(rendered)) {
                        render(true);
                    }
                })
                .subscribe(null, error -> logger.warn("Failed to update the streamed answer", error));
    }

    private Flux<Part> parts(String content) {
        List<Part> parts = new ArrayList<>();

        String rest = content;
        while (rest.length() > MESSAGE_LIMIT) {
            int split = splitIndex(rest);
            parts.add(new Part(parts.size(), rest.substring(0, split)));
            rest = rest.substring(split).stripLeading();
        }

        if (!rest.isEmpty()) {
            parts.add(new Part(parts.size(), rest));
        }

        return Flux.fromIterable(parts);
    }

    private Mono<Void> renderPart(Part part) {
        Optional<Message> existing = part.index() < messages.size()
                ? Optional.of(messages.get(part.index()))
                : Optional.empty();

        if (existing.isEmpty()) {
            return channel.createMessage(MessageCreateSpec.builder()
                            .allowedMentions(PING_AUTHOR)
                            .content(part.content())
                            .messageReference(MessageReferenceData.builder().messageId(replyToMessageId).build())
                            .build()
                    )
                    .doOnNext(messages::add)
                    .then();
        }

        Message message = existing.get();
        if (message.getContent().equals(part.content())) return Mono.empty();

        return message.edit().withContent(part.content())
                .doOnNext(edited -> messages.set(part.index(), edited))
                .then();
    }

    private int splitIndex(String content) {
        int newLine = content.lastIndexOf('\n', MESSAGE_LIMIT);
        if (newLine > 0) return newLine;

        int space = content.lastIndexOf(' ', MESSAGE_LIMIT);
        return space > 0 ? space : MESSAGE_LIMIT;
    }

    private String current() {
        synchronized (text) {
            return text.toString();
        }
    }

    private record Part(int index, String content) {
    }

}
