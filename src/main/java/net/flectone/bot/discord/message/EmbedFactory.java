package net.flectone.bot.discord.message;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import discord4j.core.spec.EmbedCreateFields;
import discord4j.core.spec.EmbedCreateSpec;
import lombok.RequiredArgsConstructor;
import net.flectone.bot.config.Integration;
import net.flectone.bot.util.Placeholders;
import net.flectone.bot.util.Texts;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import java.awt.Color;
import java.time.Instant;
import java.util.Optional;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class EmbedFactory {

    private static final int TITLE_LIMIT = 256;
    private static final int DESCRIPTION_LIMIT = 4096;
    private static final int FIELD_NAME_LIMIT = 256;
    private static final int FIELD_VALUE_LIMIT = 1024;
    private static final int FOOTER_LIMIT = 2048;
    private static final int AUTHOR_LIMIT = 256;
    private static final int FIELDS_LIMIT = 25;
    private static final int TOTAL_LIMIT = 6000;

    private final Logger logger;

    public Optional<EmbedCreateSpec> create(Integration.Discord.Embed embed, Placeholders placeholders) {
        if (embed == null) return Optional.empty();

        EmbedCreateSpec.Builder builder = EmbedCreateSpec.builder();
        int budget = TOTAL_LIMIT;

        color(embed.color()).ifPresent(builder::color);

        String title = Texts.limit(placeholders.apply(embed.title()), TITLE_LIMIT);
        if (StringUtils.isNotEmpty(title)) {
            builder.title(title);
            budget -= title.length();
        }

        String url = placeholders.apply(embed.url());
        if (StringUtils.isNotEmpty(url)) {
            builder.url(url);
        }

        String description = Texts.limit(placeholders.apply(embed.description()), DESCRIPTION_LIMIT);
        if (StringUtils.isNotEmpty(description)) {
            builder.description(description);
            budget -= description.length();
        }

        Integration.Discord.Embed.Author author = embed.author();
        if (author != null) {
            String name = Texts.limit(placeholders.apply(author.name()), AUTHOR_LIMIT);
            String iconUrl = placeholders.apply(author.iconUrl());

            if (StringUtils.isNotEmpty(name) || StringUtils.isNotEmpty(iconUrl)) {
                builder.author(name, emptyToNull(placeholders.apply(author.url())), emptyToNull(iconUrl));
                budget -= name.length();
            }
        }

        String thumbnail = placeholders.apply(embed.thumbnail());
        if (StringUtils.isNotEmpty(thumbnail)) {
            builder.thumbnail(thumbnail);
        }

        String image = placeholders.apply(embed.image());
        if (StringUtils.isNotEmpty(image)) {
            builder.image(image);
        }

        Integration.Discord.Embed.Footer footer = embed.footer();
        if (footer != null) {
            String text = Texts.limit(placeholders.apply(footer.text()), FOOTER_LIMIT);
            String iconUrl = placeholders.apply(footer.iconUrl());

            if (StringUtils.isNotEmpty(text) || StringUtils.isNotEmpty(iconUrl)) {
                builder.footer(text, emptyToNull(iconUrl));
                budget -= text.length();
            }
        }

        if (Boolean.TRUE.equals(embed.timestamp())) {
            builder.timestamp(Instant.now());
        }

        addFields(builder, embed, placeholders, budget);

        return Optional.of(builder.build());
    }

    private void addFields(EmbedCreateSpec.Builder builder,
                           Integration.Discord.Embed embed,
                           Placeholders placeholders,
                           int budget) {

        if (embed.fields() == null) return;

        int added = 0;
        for (Integration.Discord.Embed.Field field : embed.fields()) {
            if (added == FIELDS_LIMIT) {
                logger.warn("Embed '{}' has more than {} fields, the rest was dropped", embed.title(), FIELDS_LIMIT);
                return;
            }

            String name = Texts.limit(placeholders.apply(field.name()), FIELD_NAME_LIMIT);
            String value = Texts.limit(placeholders.apply(field.value()), FIELD_VALUE_LIMIT);
            if (StringUtils.isEmpty(name) || StringUtils.isEmpty(value)) continue;

            budget -= name.length() + value.length();
            if (budget < 0) {
                logger.warn("Embed '{}' reached the {} character limit, the rest was dropped", embed.title(), TOTAL_LIMIT);
                return;
            }

            builder.addField(EmbedCreateFields.Field.of(name, value, Boolean.TRUE.equals(field.inline())));
            added++;
        }
    }

    private Optional<discord4j.rest.util.Color> color(String color) {
        if (StringUtils.isEmpty(color)) return Optional.empty();

        try {
            return Optional.of(discord4j.rest.util.Color.of(Color.decode(color).getRGB()));
        } catch (NumberFormatException e) {
            logger.warn("Embed color {} is not a hex color", color);
            return Optional.empty();
        }
    }

    private String emptyToNull(String value) {
        return StringUtils.isEmpty(value) ? null : value;
    }

}
