package net.flectone.bot.discord.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.BanQuerySpec;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.file.FileFacade;
import org.apache.logging.log4j.Logger;
import reactor.core.publisher.Mono;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class HoneypotListener implements EventListener<MessageCreateEvent> {

    private static final String BAN_REASON = "Scam in honeypot channel";
    private static final int DELETE_MESSAGE_SECONDS = 60;

    private final FileFacade fileFacade;
    private final Logger logger;

    @Override
    public Class<MessageCreateEvent> eventType() {
        return MessageCreateEvent.class;
    }

    @Override
    public Mono<Void> handle(MessageCreateEvent event) {
        Message message = event.getMessage();

        Long channelId = fileFacade.discord().channelIdForHoneypot();
        if (channelId == null || channelId == 0 || message.getChannelId().asLong() != channelId) {
            return Mono.empty();
        }

        return Mono.justOrEmpty(event.getMember())
                .filter(member -> !member.isBot())
                .flatMap(this::ban);
    }

    private Mono<Void> ban(Member member) {
        logger.info("Banning {} for writing in the honeypot channel", member.getId().asLong());

        return member.ban(BanQuerySpec.builder()
                .reason(BAN_REASON)
                .deleteMessageSeconds(DELETE_MESSAGE_SECONDS)
                .build()
        );
    }

}
