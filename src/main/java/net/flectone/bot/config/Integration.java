package net.flectone.bot.config;

import discord4j.core.object.command.ApplicationCommandOption;
import lombok.Builder;
import lombok.With;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

@With
@Builder(toBuilder = true)
@Jacksonized
public record Integration(
        Discord discord,
        Telegram telegram
) {

    public interface WithEmbed {
        @Nullable String message();
        @Nullable String webhookAvatar();
        Integration.Discord.@Nullable Embed embed();
        @Nullable List<Discord.Button> buttons();
    }


    @With
    @Builder(toBuilder = true)
    @Jacksonized
    public record Discord(
            String token,
            Long guildId,
            Map<Long, String> channels,
            String formatReply,
            String message,
            String webhookAvatar,
            Discord.Embed embed,
            List<Discord.Button> buttons,
            Messages messages,
            Presence presence,
            Ticket ticket,
            List<Command> commands
    ) implements WithEmbed {

        @With
        @Builder(toBuilder = true)
        @Jacksonized
        public record Embed(
                String color,
                String title,
                String url,
                Author author,
                String description,
                String thumbnail,
                List<Field> fields,
                String image,
                Boolean timestamp,
                Footer footer
        ) {
            @With
            @Builder(toBuilder = true)
            @Jacksonized
            public record Author(
                    String name,
                    String url,
                    String iconUrl
            ) {}

            @With
            @Builder(toBuilder = true)
            @Jacksonized
            public record Footer(
                    String text,
                    String iconUrl
            ) {}

            @With
            @Builder(toBuilder = true)
            @Jacksonized
            public record Field(
                    String name,
                    String value,
                    Boolean inline
            ) {}
        }


        @With
        @Builder(toBuilder = true)
        @Jacksonized
        public record Messages(
                String noPermission,
                String unknownCommand,
                String commandError,
                String ticketCloseNoPermission,
                String ticketClosed,
                String notAThread
        ) {}

        @With
        @Builder(toBuilder = true)
        @Jacksonized
        public record Presence(
                Boolean enable,
                String status,
                Activity activity
        ) {
            @With
            @Builder(toBuilder = true)
            @Jacksonized
            public record Activity(
                    Boolean enable,
                    String type,
                    String name,
                    String url
            ) {}
        }

        @With
        @Builder(toBuilder = true)
        @Jacksonized
        public record Ticket(
                Long permissionRole,
                Long closeTagId,
                Map<String, Modal> modals
        ) implements WithPermission {
            @With
            @Builder(toBuilder = true)
            @Jacksonized
            public record Modal(
                    String name,
                    String message,
                    String webhookAvatar,
                    Embed embed,
                    CreateMessage createMessage,
                    Long forumId,
                    Long tagId,
                    List<TextInput> textInputs,
                    List<Button> buttons
            ) implements WithEmbed {
                @With
                @Builder(toBuilder = true)
                @Jacksonized
                public record CreateMessage(
                        String message,
                        String webhookAvatar,
                        Embed embed,
                        List<Button> buttons
                ) implements WithEmbed {}

                @With
                @Builder(toBuilder = true)
                @Jacksonized
                public record TextInput(
                        String id,
                        String name,
                        String placeholder,
                        discord4j.core.object.component.TextInput.Style style,
                        Integer maxLength,
                        Boolean required
                ) {}
            }
        }

        @With
        @Builder(toBuilder = true)
        @Jacksonized
        public record Command(
                String name,
                String description,
                String message,
                String webhookAvatar,
                Embed embed,
                Long permissionRole,
                Boolean privateReply,
                List<Option> options,
                List<Button> buttons
        ) implements WithPermission, WithEmbed {
            @With
            @Builder(toBuilder = true)
            @Jacksonized
            public record Option(
                    ApplicationCommandOption.Type type,
                    String name,
                    String description,
                    String message,
                    String webhookAvatar,
                    Embed embed,
                    Long permissionRole,
                    Boolean required,
                    List<Button> buttons
            ) implements WithPermission, WithEmbed {}
        }

        public interface WithPermission {
            @Nullable Long permissionRole();
        }

        @With
        @Builder(toBuilder = true)
        @Jacksonized
        public record Button(
                String id,
                String name,
                discord4j.core.object.component.Button.Style style,
                Emoji emoji
        ) {
            @With
            @Builder(toBuilder = true)
            @Jacksonized
            public record Emoji(
                    Long id,
                    String name,
                    Boolean animated
            ) {}

            public discord4j.core.object.emoji.Emoji convertEmoji() {
                if (emoji == null) return null;
                return discord4j.core.object.emoji.Emoji.of(emoji.id, emoji.name, emoji.animated);
            }
        }
    }

    @With
    @Builder(toBuilder = true)
    @Jacksonized
    public record Telegram(
            String token,
            Mode parseMode,
            String formatReply,
            String message,
            Map<String, String> channels
    ) {

        public enum Mode {
            MARKDOWN,
            MARKDOWN_V2,
            HTML,
            NONE
        }

    }
}