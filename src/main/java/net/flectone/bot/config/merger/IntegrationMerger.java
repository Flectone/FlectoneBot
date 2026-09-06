package net.flectone.bot.config.merger;

import net.flectone.bot.config.Integration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapstructMergerConfig.class)
public interface IntegrationMerger {

    @Mapping(target = "discord", expression = "java(mergeDiscord(target.build().discord().toBuilder(), source.discord()))")
    @Mapping(target = "telegram", expression = "java(mergeTelegram(target.build().telegram().toBuilder(), source.telegram()))")
    Integration merge(@MappingTarget Integration.IntegrationBuilder target, Integration source);

    @Mapping(target = "messages", expression = "java(mergeMessages(target.build().messages().toBuilder(), source.messages()))")
    @Mapping(target = "presence", expression = "java(mergePresence(target.build().presence().toBuilder(), source.presence()))")
    @Mapping(target = "forum", expression = "java(mergeForum(target.build().forum().toBuilder(), source.forum()))")
    Integration.Discord mergeDiscord(@MappingTarget Integration.Discord.DiscordBuilder target, Integration.Discord source);

    Integration.Discord.Messages mergeMessages(@MappingTarget Integration.Discord.Messages.MessagesBuilder target, Integration.Discord.Messages source);

    @Mapping(target = "activity", expression = "java(mergeActivity(target.build().activity().toBuilder(), source.activity()))")
    Integration.Discord.Presence mergePresence(@MappingTarget Integration.Discord.Presence.PresenceBuilder target, Integration.Discord.Presence source);

    Integration.Discord.Presence.Activity mergeActivity(@MappingTarget Integration.Discord.Presence.Activity.ActivityBuilder target, Integration.Discord.Presence.Activity source);

    @Mapping(target = "paste", expression = "java(mergePaste(target.build().paste().toBuilder(), source.paste()))")
    @Mapping(target = "release", expression = "java(mergeRelease(target.build().release().toBuilder(), source.release()))")
    Integration.Discord.Forum mergeForum(@MappingTarget Integration.Discord.Forum.ForumBuilder target, Integration.Discord.Forum source);

    Integration.Discord.Forum.Paste mergePaste(@MappingTarget Integration.Discord.Forum.Paste.PasteBuilder target, Integration.Discord.Forum.Paste source);

    Integration.Discord.Forum.Release mergeRelease(@MappingTarget Integration.Discord.Forum.Release.ReleaseBuilder target, Integration.Discord.Forum.Release source);

    Integration.Telegram mergeTelegram(@MappingTarget Integration.Telegram.TelegramBuilder target, Integration.Telegram source);

}
